// 模块名: service
// 功能概述: 提供 API 层可调用的只读行情服务，封装缓存读取、历史查询和 K 线预聚合兜底。
// 对外接口: Service、Error
// 依赖关系: cache、candles、config、model、timeutil
// 输入输出: 输入 HTTP 查询参数，输出 Android 兼容响应数据。
// 异常与错误: 业务错误通过 Error 携带 code，HTTP 层转为统一 envelope。
// 维护说明: API 服务不得主动请求上游，所有上游刷新只能由 Worker 执行。
package service

import (
	"context"
	"fmt"
	"strings"
	"time"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/candles"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/timeutil"
)

type Error struct {
	Code    int
	Message string
}

func (e Error) Error() string {
	return e.Message
}

type Service struct {
	settings config.Settings
	store    *cache.Store
}

func New(settings config.Settings, store *cache.Store) *Service {
	return &Service{settings: settings, store: store}
}

func (s *Service) Latest(ctx context.Context, symbol string) (model.GoldPrice, error) {
	normalized := strings.ToUpper(symbol)
	if normalized != s.settings.DefaultSymbol {
		return model.GoldPrice{}, Error{Code: 400, Message: fmt.Sprintf("unsupported symbol: %s", symbol)}
	}
	cached, err := s.store.Latest(ctx, normalized)
	if err != nil {
		return model.GoldPrice{}, err
	}
	if cached != nil {
		cached.ServerTime = timeutil.NowString(s.settings.Timezone)
		return *cached, nil
	}
	fallback, err := s.store.LastSuccess(ctx, normalized)
	if err != nil {
		return model.GoldPrice{}, err
	}
	if fallback != nil {
		fallback.Source = "cache"
		fallback.IsStale = true
		fallback.ServerTime = timeutil.NowString(s.settings.Timezone)
		return *fallback, nil
	}
	return model.GoldPrice{}, Error{Code: 503, Message: "latest gold cache is not ready"}
}

func (s *Service) History(
	ctx context.Context,
	symbol string,
	date string,
	startMillis *int64,
	endMillis *int64,
	limit int,
) (model.GoldHistoryResponse, error) {
	normalized := strings.ToUpper(symbol)
	if normalized != s.settings.DefaultSymbol {
		return model.GoldHistoryResponse{}, Error{Code: 400, Message: fmt.Sprintf("unsupported symbol: %s", symbol)}
	}
	queryDate := date
	if queryDate == "" {
		queryDate = timeutil.TodayDateString(s.settings.Timezone)
	}
	if !timeutil.IsDateString(queryDate) {
		return model.GoldHistoryResponse{}, Error{Code: 400, Message: fmt.Sprintf("invalid date: %s", queryDate)}
	}
	if startMillis != nil && endMillis != nil && *startMillis > *endMillis {
		return model.GoldHistoryResponse{}, Error{Code: 400, Message: "startMillis must be less than or equal to endMillis"}
	}
	if limit < 1 {
		limit = 1
	}
	if limit > 10_000 {
		limit = 10_000
	}
	points, err := s.store.History(ctx, normalized, queryDate, startMillis, endMillis, limit)
	if err != nil {
		return model.GoldHistoryResponse{}, err
	}
	return model.GoldHistoryResponse{
		Symbol:   normalized,
		Date:     queryDate,
		Timezone: s.settings.Timezone,
		Count:    len(points),
		Points:   points,
	}, nil
}

func (s *Service) Candles(ctx context.Context, symbol string, rangeName string) (model.GoldCandlesResponse, error) {
	normalized := strings.ToUpper(symbol)
	if normalized != s.settings.DefaultSymbol {
		return model.GoldCandlesResponse{}, Error{Code: 400, Message: fmt.Sprintf("unsupported symbol: %s", symbol)}
	}
	if _, ok := candles.Resolutions[rangeName]; !ok {
		return model.GoldCandlesResponse{}, unsupportedRange(rangeName)
	}
	cached, err := s.store.Candles(ctx, normalized, rangeName)
	if err != nil {
		return model.GoldCandlesResponse{}, err
	}
	if cached != nil {
		return *cached, nil
	}
	return s.RebuildCandles(ctx, normalized, rangeName, timeutil.NowMillis(s.settings.Timezone))
}

func (s *Service) RebuildCandles(ctx context.Context, symbol string, rangeName string, endMillis int64) (model.GoldCandlesResponse, error) {
	resolution, ok := candles.Resolutions[rangeName]
	if !ok {
		return model.GoldCandlesResponse{}, unsupportedRange(rangeName)
	}
	startMillis := endMillis - resolution.WindowMillis
	points, err := s.historyPointsBetween(ctx, symbol, startMillis, endMillis)
	if err != nil {
		return model.GoldCandlesResponse{}, err
	}
	bars := candles.Aggregate(points, startMillis, endMillis, resolution.BucketMillis)
	response := model.GoldCandlesResponse{
		Symbol:     strings.ToUpper(symbol),
		Range:      rangeName,
		Resolution: resolution.Label,
		Timezone:   s.settings.Timezone,
		Count:      len(bars),
		Bars:       bars,
	}
	if err := s.store.SetCandles(ctx, response); err != nil {
		return model.GoldCandlesResponse{}, err
	}
	return response, nil
}

func (s *Service) RebuildAllCandles(ctx context.Context, symbol string, endMillis int64) error {
	for _, rangeName := range []string{"5m", "1h", "6h", "1d"} {
		if _, err := s.RebuildCandles(ctx, symbol, rangeName, endMillis); err != nil {
			return err
		}
	}
	return nil
}

func (s *Service) AppConfig() model.AppConfig {
	return model.AppConfig{
		MinRefreshInterval:        s.settings.MinRefreshInterval,
		DefaultRefreshInterval:    s.settings.DefaultRefreshInterval,
		NonTradingRefreshInterval: s.settings.NonTradingRefreshInterval,
		NotificationEnabled:       true,
		LatestVersionCode:         s.settings.LatestVersionCode,
		ForceUpdate:               s.settings.ForceUpdate,
		Notice:                    s.settings.Notice,
	}
}

func (s *Service) Health(ctx context.Context) map[string]any {
	redisStatus := map[string]any{"ok": true, "url": s.store.SafeRedisURL()}
	if err := s.store.Ping(ctx); err != nil {
		redisStatus["ok"] = false
		redisStatus["error"] = err.Error()
	}
	sourceStatus, _ := s.store.SourceStatus(ctx)
	serviceStatus := map[string]any{
		"defaultSymbol":          s.settings.DefaultSymbol,
		"dataSource":             s.settings.DataSource,
		"upstreamConfigured":     s.settings.UpstreamConfigured(),
		"finnhubConfigured":      s.settings.FinnhubConfigured(),
		"alphaVantageConfigured": s.settings.AlphaVantageConfigured(),
		"nowapiConfigured":       s.settings.NowAPIConfigured(),
		"sourceStatus":           sourceStatus,
	}
	return map[string]any{
		"ok":      redisStatus["ok"],
		"redis":   redisStatus,
		"service": serviceStatus,
	}
}

func (s *Service) historyPointsBetween(ctx context.Context, symbol string, startMillis int64, endMillis int64) ([]model.GoldHistoryPoint, error) {
	points := make([]model.GoldHistoryPoint, 0)
	for _, date := range datesBetween(startMillis, endMillis, s.settings.Timezone) {
		page, err := s.store.History(ctx, symbol, date, &startMillis, &endMillis, 100_000)
		if err != nil {
			return nil, err
		}
		points = append(points, page...)
	}
	return dedupeSortPoints(points, startMillis, endMillis), nil
}

func unsupportedRange(rangeName string) Error {
	return Error{Code: 400, Message: fmt.Sprintf("unsupported range: %s. supported: %s", rangeName, strings.Join([]string{"5m", "1h", "6h", "1d"}, ", "))}
}

func datesBetween(startMillis int64, endMillis int64, timezone string) []string {
	location := timeutil.Location(timezone)
	start := time.UnixMilli(startMillis).In(location)
	end := time.UnixMilli(endMillis).In(location)
	dates := make([]string, 0)
	current := time.Date(start.Year(), start.Month(), start.Day(), 0, 0, 0, 0, location)
	endDate := time.Date(end.Year(), end.Month(), end.Day(), 0, 0, 0, 0, location)
	for !current.After(endDate) {
		dates = append(dates, current.Format("2006-01-02"))
		current = current.AddDate(0, 0, 1)
	}
	return dates
}

func dedupeSortPoints(points []model.GoldHistoryPoint, startMillis int64, endMillis int64) []model.GoldHistoryPoint {
	byTimestamp := make(map[int64]model.GoldHistoryPoint)
	keys := make([]int64, 0)
	for _, point := range points {
		if point.TimestampMillis < startMillis || point.TimestampMillis > endMillis || point.Price <= 0 {
			continue
		}
		if _, exists := byTimestamp[point.TimestampMillis]; !exists {
			keys = append(keys, point.TimestampMillis)
		}
		byTimestamp[point.TimestampMillis] = point
	}
	for i := 1; i < len(keys); i++ {
		current := keys[i]
		j := i - 1
		for j >= 0 && keys[j] > current {
			keys[j+1] = keys[j]
			j--
		}
		keys[j+1] = current
	}
	result := make([]model.GoldHistoryPoint, 0, len(keys))
	for _, key := range keys {
		result = append(result, byTimestamp[key])
	}
	return result
}
