// 模块名: cache
// 功能概述: 封装 Go 服务端对 Redis 最新行情、历史行情、每日汇总、K 线缓存和限流状态的读写。
// 对外接口: Store
// 依赖关系: go-redis、config、model、timeutil
// 输入输出: 输入业务模型，输出 Redis 中与 Python 服务端兼容的 JSON 和 ZSet 数据。
// 异常与错误: Redis 异常返回给调用方，由 API 或 Worker 决定降级策略。
// 维护说明: Redis key 与 JSON 字段是跨版本迁移契约，不得随意改名。
package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"

	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/timeutil"
)

type Store struct {
	settings config.Settings
	redis    *redis.Client
}

func New(settings config.Settings) (*Store, error) {
	options, err := redis.ParseURL(settings.RedisURL)
	if err != nil {
		return nil, err
	}
	options.PoolSize = settings.RedisPoolSize
	return &Store{
		settings: settings,
		redis:    redis.NewClient(options),
	}, nil
}

func (s *Store) Close() error {
	return s.redis.Close()
}

func (s *Store) Ping(ctx context.Context) error {
	return s.redis.Ping(ctx).Err()
}

func (s *Store) Latest(ctx context.Context, symbol string) (*model.GoldPrice, error) {
	return getJSON[model.GoldPrice](ctx, s.redis, latestKey(symbol))
}

func (s *Store) LastSuccess(ctx context.Context, symbol string) (*model.GoldPrice, error) {
	return getJSON[model.GoldPrice](ctx, s.redis, lastSuccessKey(symbol))
}

func (s *Store) StoreSuccess(ctx context.Context, price model.GoldPrice) (model.GoldPrice, error) {
	point, err := s.AppendHistory(ctx, price)
	if err != nil {
		return price, err
	}
	enriched, err := s.withLocalDailyPrices(ctx, price, point)
	if err != nil {
		return price, err
	}
	if err := setJSON(ctx, s.redis, latestKey(enriched.Symbol), enriched, seconds(s.settings.LatestCacheTTLSeconds)); err != nil {
		return price, err
	}
	if err := setJSON(ctx, s.redis, lastSuccessKey(enriched.Symbol), enriched, seconds(s.settings.LastSuccessTTLSeconds)); err != nil {
		return price, err
	}
	return enriched, nil
}

func (s *Store) AppendHistory(ctx context.Context, price model.GoldPrice) (*model.GoldHistoryPoint, error) {
	if price.IsStale || strings.EqualFold(price.Source, "cache") || price.Price <= 0 {
		return nil, nil
	}
	point := historyPointFromPrice(price, s.settings.Timezone)
	date := timeutil.DateStringFromMillis(point.TimestampMillis, s.settings.Timezone)
	key := historyKey(price.Symbol, date)
	payload, err := json.Marshal(point)
	if err != nil {
		return nil, err
	}
	if err := s.redis.ZAdd(ctx, key, redis.Z{
		Score:  float64(point.TimestampMillis),
		Member: string(payload),
	}).Err(); err != nil {
		return nil, err
	}
	if err := s.redis.Expire(ctx, key, s.historyRetention()).Err(); err != nil {
		return nil, err
	}
	if err := s.upsertDailySummary(ctx, price.Symbol, date, *point); err != nil {
		return nil, err
	}
	return point, nil
}

func (s *Store) History(
	ctx context.Context,
	symbol string,
	date string,
	startMillis *int64,
	endMillis *int64,
	limit int,
) ([]model.GoldHistoryPoint, error) {
	minScore := "-inf"
	maxScore := "+inf"
	if startMillis != nil {
		minScore = strconv.FormatInt(*startMillis, 10)
	}
	if endMillis != nil {
		maxScore = strconv.FormatInt(*endMillis, 10)
	}
	rawValues, err := s.redis.ZRevRangeByScore(ctx, historyKey(symbol, date), &redis.ZRangeBy{
		Min:    minScore,
		Max:    maxScore,
		Offset: 0,
		Count:  int64(limit),
	}).Result()
	if err != nil {
		return nil, err
	}
	points := make([]model.GoldHistoryPoint, 0, len(rawValues))
	for i := len(rawValues) - 1; i >= 0; i-- {
		var point model.GoldHistoryPoint
		if err := json.Unmarshal([]byte(rawValues[i]), &point); err == nil {
			points = append(points, point)
		}
	}
	return points, nil
}

func (s *Store) DailySummary(ctx context.Context, symbol string, date string) (*model.GoldDailySummary, error) {
	summary, err := getJSON[model.GoldDailySummary](ctx, s.redis, dailySummaryKey(symbol, date))
	if err != nil {
		return nil, err
	}
	if summary != nil {
		return summary, nil
	}
	return s.buildDailySummaryFromHistory(ctx, symbol, date)
}

func (s *Store) SetSourceStatus(ctx context.Context, status map[string]any) error {
	return setJSON(ctx, s.redis, "gold:source:status", status, seconds(s.settings.LastSuccessTTLSeconds))
}

func (s *Store) SourceStatus(ctx context.Context) (map[string]any, error) {
	status, err := getJSON[map[string]any](ctx, s.redis, "gold:source:status")
	if err != nil || status == nil {
		return nil, err
	}
	return *status, nil
}

func (s *Store) Candles(ctx context.Context, symbol string, rangeName string) (*model.GoldCandlesResponse, error) {
	return getJSON[model.GoldCandlesResponse](ctx, s.redis, candlesKey(symbol, rangeName))
}

func (s *Store) SetCandles(ctx context.Context, candles model.GoldCandlesResponse) error {
	return setJSON(ctx, s.redis, candlesKey(candles.Symbol, candles.Range), candles, seconds(s.settings.CandlesCacheTTLSeconds))
}

func (s *Store) AcquireLock(ctx context.Context, key string, ttl time.Duration) (bool, error) {
	return s.redis.SetNX(ctx, key, "1", ttl).Result()
}

func (s *Store) AllowRate(ctx context.Context, key string, limit int, window time.Duration) (bool, error) {
	if limit <= 0 {
		return true, nil
	}
	count, err := s.redis.Incr(ctx, key).Result()
	if err != nil {
		return false, err
	}
	if count == 1 {
		_ = s.redis.Expire(ctx, key, window).Err()
	}
	return count <= int64(limit), nil
}

func (s *Store) SafeRedisURL() string {
	parts := strings.Split(s.settings.RedisURL, "@")
	return parts[len(parts)-1]
}

func (s *Store) withLocalDailyPrices(
	ctx context.Context,
	price model.GoldPrice,
	point *model.GoldHistoryPoint,
) (model.GoldPrice, error) {
	date := s.dateForPrice(price, point)
	today, err := s.DailySummary(ctx, price.Symbol, date)
	if err != nil {
		return price, err
	}
	previousDate, _ := timeutil.PreviousDateString(date)
	previous, err := s.DailySummary(ctx, price.Symbol, previousDate)
	if err != nil {
		return price, err
	}
	open := price.Open
	high := price.High
	low := price.Low
	prevClose := price.PrevClose
	if today != nil {
		open = today.Open
		high = today.High
		low = today.Low
	}
	if previous != nil {
		prevClose = previous.Close
	}
	change := round2(price.Price - prevClose)
	changePercent := 0.0
	if prevClose > 0 {
		changePercent = round2(change / prevClose * 100)
	}
	price.Open = round2(open)
	price.High = round2(high)
	price.Low = round2(low)
	price.PrevClose = round2(prevClose)
	price.Change = change
	price.ChangePercent = changePercent
	return price, nil
}

func (s *Store) upsertDailySummary(ctx context.Context, symbol string, date string, point model.GoldHistoryPoint) error {
	current, err := s.DailySummary(ctx, symbol, date)
	if err != nil {
		return err
	}
	var summary model.GoldDailySummary
	if current == nil {
		summary = model.GoldDailySummary{
			Symbol:               strings.ToUpper(symbol),
			Date:                 date,
			Open:                 point.Price,
			High:                 point.Price,
			Low:                  point.Price,
			Close:                point.Price,
			OpenTimestampMillis:  point.TimestampMillis,
			CloseTimestampMillis: point.TimestampMillis,
		}
	} else {
		summary = *current
		if point.TimestampMillis < summary.OpenTimestampMillis {
			summary.Open = point.Price
			summary.OpenTimestampMillis = point.TimestampMillis
		}
		if point.TimestampMillis >= summary.CloseTimestampMillis {
			summary.Close = point.Price
			summary.CloseTimestampMillis = point.TimestampMillis
		}
		if point.Price > summary.High {
			summary.High = point.Price
		}
		if point.Price < summary.Low {
			summary.Low = point.Price
		}
	}
	return setJSON(ctx, s.redis, dailySummaryKey(symbol, date), summary, s.historyRetention())
}

func (s *Store) buildDailySummaryFromHistory(ctx context.Context, symbol string, date string) (*model.GoldDailySummary, error) {
	points, err := s.History(ctx, symbol, date, nil, nil, 100_000)
	if err != nil || len(points) == 0 {
		return nil, err
	}
	high := points[0].Price
	low := points[0].Price
	for _, point := range points {
		if point.Price > high {
			high = point.Price
		}
		if point.Price < low {
			low = point.Price
		}
	}
	summary := model.GoldDailySummary{
		Symbol:               strings.ToUpper(symbol),
		Date:                 date,
		Open:                 points[0].Price,
		High:                 high,
		Low:                  low,
		Close:                points[len(points)-1].Price,
		OpenTimestampMillis:  points[0].TimestampMillis,
		CloseTimestampMillis: points[len(points)-1].TimestampMillis,
	}
	if err := setJSON(ctx, s.redis, dailySummaryKey(symbol, date), summary, s.historyRetention()); err != nil {
		return nil, err
	}
	return &summary, nil
}

func (s *Store) dateForPrice(price model.GoldPrice, point *model.GoldHistoryPoint) string {
	if point == nil {
		point = historyPointFromPrice(price, s.settings.Timezone)
	}
	return timeutil.DateStringFromMillis(point.TimestampMillis, s.settings.Timezone)
}

func getJSON[T any](ctx context.Context, client *redis.Client, key string) (*T, error) {
	raw, err := client.Get(ctx, key).Result()
	if err == redis.Nil {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var value T
	if err := json.Unmarshal([]byte(raw), &value); err != nil {
		return nil, err
	}
	return &value, nil
}

func setJSON(ctx context.Context, client *redis.Client, key string, value any, ttl time.Duration) error {
	payload, err := json.Marshal(value)
	if err != nil {
		return err
	}
	return client.SetEx(ctx, key, string(payload), ttl).Err()
}

func historyPointFromPrice(price model.GoldPrice, timezone string) *model.GoldHistoryPoint {
	timestamp, ok := timeutil.TimestampMillis(price.ServerTime, timezone)
	if !ok {
		timestamp, ok = timeutil.TimestampMillis(price.UpdateTime, timezone)
	}
	if !ok {
		timestamp = timeutil.NowMillis(timezone)
	}
	return &model.GoldHistoryPoint{
		TimestampMillis: timestamp,
		Price:           price.Price,
		UpdateTime:      price.UpdateTime,
		ServerTime:      price.ServerTime,
		Source:          price.Source,
	}
}

func latestKey(symbol string) string {
	return fmt.Sprintf("gold:latest:%s", strings.ToUpper(symbol))
}

func lastSuccessKey(symbol string) string {
	return fmt.Sprintf("gold:last_success:%s", strings.ToUpper(symbol))
}

func historyKey(symbol string, date string) string {
	return fmt.Sprintf("gold:history:%s:%s", strings.ToUpper(symbol), date)
}

func dailySummaryKey(symbol string, date string) string {
	return fmt.Sprintf("gold:daily_summary:%s:%s", strings.ToUpper(symbol), date)
}

func candlesKey(symbol string, rangeName string) string {
	return fmt.Sprintf("gold:candles:%s:%s", strings.ToUpper(symbol), rangeName)
}

func (s *Store) historyRetention() time.Duration {
	days := s.settings.HistoryRetentionDays
	if days < 1 {
		days = 1
	}
	return time.Duration(days) * 24 * time.Hour
}

func seconds(value int) time.Duration {
	if value < 1 {
		value = 1
	}
	return time.Duration(value) * time.Second
}

func round2(value float64) float64 {
	if value >= 0 {
		return float64(int64(value*100+0.5)) / 100
	}
	return float64(int64(value*100-0.5)) / 100
}
