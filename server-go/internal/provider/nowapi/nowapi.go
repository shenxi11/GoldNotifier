// 模块名: provider/nowapi
// 功能概述: 接入 NowAPI 黄金行情接口，并转换为统一 GoldPrice。
// 对外接口: Provider
// 依赖关系: net/http、config、model、timeutil
// 输入输出: 输入 XAU symbol 和 NowAPI JSON，输出 Android 兼容 GoldPrice。
// 异常与错误: 凭据缺失、HTTP 异常、上游错误码和字段缺失均返回 error。
// 维护说明: 该实现用于兼容旧配置，默认生产数据源仍建议使用 Finnhub。
package nowapi

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/timeutil"
)

type Provider struct {
	settings config.Settings
	client   *http.Client
}

func New(settings config.Settings) *Provider {
	return &Provider{
		settings: settings,
		client: &http.Client{
			Timeout: durationSeconds(settings.UpstreamTimeoutSeconds),
		},
	}
}

func (p *Provider) FetchLatest(ctx context.Context, symbol string) (model.GoldPrice, error) {
	if !p.settings.NowAPIConfigured() {
		return model.GoldPrice{}, errors.New("NOWAPI_APPKEY and NOWAPI_SIGN are required")
	}
	goldID, err := p.settings.GoldIDForSymbol(symbol)
	if err != nil {
		return model.GoldPrice{}, err
	}
	requestURL, err := url.Parse(p.settings.NowAPIEndpoint)
	if err != nil {
		return model.GoldPrice{}, err
	}
	query := requestURL.Query()
	query.Set("app", p.settings.NowAPIApp)
	query.Set("goldid", goldID)
	query.Set("appkey", p.settings.NowAPIAppKey)
	query.Set("sign", p.settings.NowAPISign)
	query.Set("format", "json")
	requestURL.RawQuery = query.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return model.GoldPrice{}, err
	}
	resp, err := p.client.Do(req)
	if err != nil {
		return model.GoldPrice{}, fmt.Errorf("NowAPI request failed: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return model.GoldPrice{}, fmt.Errorf("NowAPI returned HTTP %d", resp.StatusCode)
	}
	var payload map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return model.GoldPrice{}, errors.New("NowAPI returned invalid JSON")
	}
	success := strings.ToLower(fmt.Sprint(payload["success"]))
	if success != "1" && success != "true" {
		message := firstPresent(payload, "msg", "message")
		if message == nil {
			message = "NowAPI returned an error"
		}
		return model.GoldPrice{}, errors.New(fmt.Sprint(message))
	}
	row, err := extractQuoteRow(payload, goldID)
	if err != nil {
		return model.GoldPrice{}, err
	}
	return normalize(row, strings.ToUpper(symbol), p.settings.StaleAfterSeconds, p.settings.Timezone)
}

func normalize(row map[string]any, symbol string, staleAfterSeconds int, timezone string) (model.GoldPrice, error) {
	price, err := parseFloat(firstPresent(row, "last_price", "price", "nowpri", "latest_price"))
	if err != nil {
		return model.GoldPrice{}, err
	}
	open, err := parseFloat(firstPresent(row, "open_price", "open", "openpri"))
	if err != nil {
		return model.GoldPrice{}, err
	}
	prevClose, err := parseFloat(firstPresent(row, "yesy_price", "prevClose", "yestclose", "yes_price"))
	if err != nil {
		return model.GoldPrice{}, err
	}
	high, err := parseFloat(firstPresent(row, "high_price", "high", "maxpri"))
	if err != nil {
		return model.GoldPrice{}, err
	}
	low, err := parseFloat(firstPresent(row, "low_price", "low", "minpri"))
	if err != nil {
		return model.GoldPrice{}, err
	}
	change, err := parseFloat(firstPresent(row, "change_price", "change", "increase"))
	if err != nil {
		change = price - prevClose
	}
	changePercent, err := parsePercent(firstPresent(row, "change_margin", "changePercent", "changeratio"))
	if err != nil && prevClose > 0 {
		changePercent = change / prevClose * 100
	}
	updateTime := strings.TrimSpace(fmt.Sprint(firstPresent(row, "uptime", "updateTime", "time", "date")))
	if updateTime == "" || updateTime == "<nil>" {
		return model.GoldPrice{}, errors.New("updateTime is required")
	}
	name := strings.TrimSpace(fmt.Sprint(firstPresent(row, "varietynm", "name")))
	if name == "" || name == "<nil>" {
		name = "现货黄金"
	}
	return model.GoldPrice{
		Name:          name,
		Symbol:        symbol,
		Price:         round2(price),
		Change:        round2(change),
		ChangePercent: round2(changePercent),
		Unit:          "元/克",
		Open:          round2(open),
		PrevClose:     round2(prevClose),
		High:          round2(high),
		Low:           round2(low),
		UpdateTime:    updateTime,
		ServerTime:    timeutil.NowString(timezone),
		Source:        "nowapi",
		MarketStatus:  timeutil.MarketStatus(timezone),
		IsStale:       timeutil.IsTimeStale(updateTime, staleAfterSeconds, timezone),
	}, nil
}

func extractQuoteRow(payload map[string]any, expectedGoldID string) (map[string]any, error) {
	result, ok := payload["result"].(map[string]any)
	if !ok {
		return nil, errors.New("result is required")
	}
	dtList := result["dtList"]
	if dtList == nil {
		dtList = result
	}
	if rows, ok := dtList.(map[string]any); ok {
		if row, ok := rows[expectedGoldID].(map[string]any); ok {
			return row, nil
		}
		for _, value := range rows {
			if row, ok := value.(map[string]any); ok {
				return row, nil
			}
		}
	}
	if rows, ok := dtList.([]any); ok {
		for _, value := range rows {
			row, ok := value.(map[string]any)
			if !ok {
				continue
			}
			goldID := fmt.Sprint(row["goldid"])
			if goldID == "" || goldID == expectedGoldID {
				return row, nil
			}
		}
	}
	return nil, errors.New("quote row is required")
}

func firstPresent(row map[string]any, keys ...string) any {
	for _, key := range keys {
		value := row[key]
		if value != nil && value != "" {
			return value
		}
	}
	return nil
}

func parseFloat(value any) (float64, error) {
	switch typed := value.(type) {
	case float64:
		if typed <= 0 {
			return 0, errors.New("price fields must be positive")
		}
		return typed, nil
	case string:
		parsed, err := strconv.ParseFloat(strings.TrimSpace(strings.TrimSuffix(typed, "%")), 64)
		if err != nil || parsed <= 0 {
			return 0, errors.New("price fields must be positive")
		}
		return parsed, nil
	default:
		return 0, errors.New("price fields must be positive")
	}
}

func parsePercent(value any) (float64, error) {
	switch typed := value.(type) {
	case float64:
		return typed, nil
	case string:
		return strconv.ParseFloat(strings.TrimSpace(strings.TrimSuffix(typed, "%")), 64)
	default:
		return 0, errors.New("percent is required")
	}
}

func durationSeconds(seconds float64) time.Duration {
	if seconds <= 0 {
		seconds = 1
	}
	return time.Duration(seconds * float64(time.Second))
}

func round2(value float64) float64 {
	return math.Round(value*100) / 100
}
