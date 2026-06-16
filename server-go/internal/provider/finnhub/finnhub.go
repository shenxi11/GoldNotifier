// 模块名: provider/finnhub
// 功能概述: 接入 Finnhub WebSocket 和 Alpha Vantage 汇率，换算为人民币每克黄金价格。
// 对外接口: Provider、BuildGoldPriceFromSnapshot
// 依赖关系: net/http、nhooyr.io/websocket、config、model、timeutil
// 输入输出: 输入 XAU 符号和上游报价，输出 Android 兼容 GoldPrice。
// 异常与错误: 凭据缺失、WebSocket 超时、汇率异常和非法价格均返回 error。
// 维护说明: 不记录 token；USD/CNY 按配置 TTL 缓存，失败时使用缓存或 fallback rate。
package finnhub

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
	"sync"
	"time"

	"nhooyr.io/websocket"

	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/timeutil"
)

const troyOunceGrams = 31.1034768

type Quote struct {
	Current           float64
	Open              float64
	PrevClose         float64
	High              float64
	Low               float64
	LatestTimestampMS int64
	IsStale           bool
}

type StreamPrice struct {
	Symbol            string
	Price             float64
	LatestTimestampMS int64
}

type QuoteSnapshot struct {
	XAUJPY Quote
	USDJPY Quote
	USDCNY Quote
}

type Provider struct {
	settings         config.Settings
	client           *http.Client
	mu               sync.RWMutex
	latest           map[string]StreamPrice
	notify           chan struct{}
	streamMu         sync.Mutex
	streamStarted    bool
	lastStreamError  string
	usdCNYCache      *Quote
	usdCNYCachedTime time.Time
}

func New(settings config.Settings) *Provider {
	return &Provider{
		settings: settings,
		client: &http.Client{
			Timeout: durationSeconds(settings.UpstreamTimeoutSeconds),
		},
		latest: make(map[string]StreamPrice),
		notify: make(chan struct{}, 1),
	}
}

func (p *Provider) FetchLatest(ctx context.Context, symbol string) (model.GoldPrice, error) {
	if strings.ToUpper(symbol) != p.settings.DefaultSymbol {
		return model.GoldPrice{}, fmt.Errorf("unsupported symbol: %s", symbol)
	}
	if !p.settings.FinnhubConfigured() {
		return model.GoldPrice{}, errors.New("FINNHUB_API_KEY is required")
	}
	snapshot, err := p.collectSnapshot(ctx)
	if err != nil {
		return model.GoldPrice{}, err
	}
	return BuildGoldPriceFromSnapshot(snapshot, strings.ToUpper(symbol), p.settings.StaleAfterSeconds, p.settings.Timezone)
}

func (p *Provider) collectSnapshot(ctx context.Context) (QuoteSnapshot, error) {
	streamPrices, err := p.collectStreamPrices(ctx)
	if err != nil {
		return QuoteSnapshot{}, err
	}
	usdCNY, err := p.usdCNYQuote(ctx)
	if err != nil {
		return QuoteSnapshot{}, err
	}
	return QuoteSnapshot{
		XAUJPY: quoteFromStreamPrice(streamPrices[p.settings.FinnhubXAUJPYSymbol]),
		USDJPY: quoteFromStreamPrice(streamPrices[p.settings.FinnhubUSDJPYSymbol]),
		USDCNY: usdCNY,
	}, nil
}

func (p *Provider) collectStreamPrices(ctx context.Context) (map[string]StreamPrice, error) {
	symbols := p.streamSymbols()
	p.ensureStreamTask(symbols)
	deadline := time.Now().Add(durationSeconds(p.settings.FinnhubStreamTimeoutSeconds))
	for {
		if snapshot := p.freshSnapshot(symbols); snapshot != nil {
			return snapshot, nil
		}
		remaining := time.Until(deadline)
		if remaining <= 0 {
			detail := "Finnhub stream timed out"
			if last := p.lastError(); last != "" {
				detail += ": " + last
			}
			return nil, errors.New(detail)
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-p.notify:
		case <-time.After(remaining):
		}
	}
}

func (p *Provider) ensureStreamTask(symbols []string) {
	p.streamMu.Lock()
	defer p.streamMu.Unlock()
	if p.streamStarted {
		return
	}
	p.streamStarted = true
	go p.runStream(symbols)
}

func (p *Provider) runStream(symbols []string) {
	for {
		if err := p.runStreamOnce(symbols); err != nil {
			p.setLastError(err.Error())
			p.signal()
		}
		time.Sleep(2 * time.Second)
	}
}

func (p *Provider) runStreamOnce(symbols []string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, websocketURL(p.settings.FinnhubWSEndpoint, p.settings.FinnhubAPIKey), nil)
	if err != nil {
		return fmt.Errorf("Finnhub stream connection failed: %w", err)
	}
	defer conn.Close(websocket.StatusNormalClosure, "")
	p.setLastError("")
	for _, symbol := range symbols {
		payload, _ := json.Marshal(map[string]string{"type": "subscribe", "symbol": symbol})
		if err := conn.Write(context.Background(), websocket.MessageText, payload); err != nil {
			return err
		}
	}
	for {
		messageType, raw, err := conn.Read(context.Background())
		if err != nil {
			return err
		}
		if messageType != websocket.MessageText {
			continue
		}
		var payload map[string]any
		if err := json.Unmarshal(raw, &payload); err != nil {
			p.setLastError("Finnhub stream payload is invalid")
			p.signal()
			continue
		}
		switch payload["type"] {
		case "trade":
			p.captureTradePrices(payload["data"], symbols)
		case "error":
			p.setLastError(fmt.Sprint(payload["msg"]))
			p.signal()
		}
	}
}

func (p *Provider) captureTradePrices(trades any, expectedSymbols []string) {
	values, ok := trades.([]any)
	if !ok {
		return
	}
	expected := make(map[string]struct{}, len(expectedSymbols))
	for _, symbol := range expectedSymbols {
		expected[symbol] = struct{}{}
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, item := range values {
		trade, ok := item.(map[string]any)
		if !ok {
			continue
		}
		symbol, _ := trade["s"].(string)
		if _, ok := expected[symbol]; !ok {
			continue
		}
		price, ok := positiveFloat(trade["p"])
		if !ok {
			continue
		}
		p.latest[symbol] = StreamPrice{
			Symbol:            symbol,
			Price:             price,
			LatestTimestampMS: timestampMS(trade["t"]),
		}
	}
	p.signal()
}

func (p *Provider) freshSnapshot(symbols []string) map[string]StreamPrice {
	p.mu.RLock()
	defer p.mu.RUnlock()
	result := make(map[string]StreamPrice, len(symbols))
	staleAfter := int64(max(p.settings.StaleAfterSeconds, 1)) * 1000
	nowMillis := time.Now().UnixMilli()
	for _, symbol := range symbols {
		streamPrice, ok := p.latest[symbol]
		if !ok || streamPrice.LatestTimestampMS <= 0 {
			return nil
		}
		if nowMillis-streamPrice.LatestTimestampMS > staleAfter {
			return nil
		}
		result[symbol] = streamPrice
	}
	return result
}

func (p *Provider) usdCNYQuote(ctx context.Context) (Quote, error) {
	if cached, ok := p.cachedUSD(false); ok {
		return cached, nil
	}
	quote, err := p.fetchUSDCNYQuote(ctx)
	if err == nil {
		p.mu.Lock()
		p.usdCNYCache = &quote
		p.usdCNYCachedTime = time.Now()
		p.mu.Unlock()
		return quote, nil
	}
	if cached, ok := p.cachedUSD(true); ok {
		cached.LatestTimestampMS = time.Now().UnixMilli()
		cached.IsStale = true
		return cached, nil
	}
	if p.settings.USDCNYFallbackRate > 0 {
		rate := p.settings.USDCNYFallbackRate
		return Quote{Current: rate, Open: rate, PrevClose: rate, High: rate, Low: rate, LatestTimestampMS: time.Now().UnixMilli(), IsStale: true}, nil
	}
	return Quote{}, err
}

func (p *Provider) fetchUSDCNYQuote(ctx context.Context) (Quote, error) {
	if !p.settings.AlphaVantageConfigured() {
		return Quote{}, errors.New("ALPHA_VANTAGE_API_KEY is required")
	}
	requestURL, err := url.Parse(p.settings.AlphaVantageEndpoint)
	if err != nil {
		return Quote{}, err
	}
	query := requestURL.Query()
	query.Set("function", "CURRENCY_EXCHANGE_RATE")
	query.Set("from_currency", p.settings.AlphaVantageFromCurrency)
	query.Set("to_currency", p.settings.AlphaVantageToCurrency)
	query.Set("apikey", p.settings.AlphaVantageAPIKey)
	requestURL.RawQuery = query.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return Quote{}, err
	}
	resp, err := p.client.Do(req)
	if err != nil {
		return Quote{}, fmt.Errorf("Alpha Vantage USD/CNY request failed: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return Quote{}, fmt.Errorf("Alpha Vantage USD/CNY returned HTTP %d", resp.StatusCode)
	}
	var payload map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return Quote{}, errors.New("Alpha Vantage USD/CNY payload is invalid")
	}
	rate, ok := extractAlphaVantageRate(payload)
	if !ok {
		return Quote{}, errors.New("Alpha Vantage USD/CNY rate is missing")
	}
	now := time.Now().UnixMilli()
	return Quote{Current: rate, Open: rate, PrevClose: rate, High: rate, Low: rate, LatestTimestampMS: now}, nil
}

func (p *Provider) cachedUSD(allowStale bool) (Quote, bool) {
	p.mu.RLock()
	defer p.mu.RUnlock()
	if p.usdCNYCache == nil {
		return Quote{}, false
	}
	if allowStale {
		return *p.usdCNYCache, true
	}
	ttl := time.Duration(max(p.settings.AlphaVantageCacheTTLSeconds, 1)) * time.Second
	if time.Since(p.usdCNYCachedTime) > ttl {
		return Quote{}, false
	}
	return *p.usdCNYCache, true
}

func (p *Provider) streamSymbols() []string {
	seen := make(map[string]struct{})
	result := make([]string, 0, 2)
	for _, symbol := range []string{p.settings.FinnhubXAUJPYSymbol, p.settings.FinnhubUSDJPYSymbol} {
		if _, ok := seen[symbol]; ok {
			continue
		}
		seen[symbol] = struct{}{}
		result = append(result, symbol)
	}
	return result
}

func (p *Provider) signal() {
	select {
	case p.notify <- struct{}{}:
	default:
	}
}

func (p *Provider) setLastError(value string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.lastStreamError = value
}

func (p *Provider) lastError() string {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.lastStreamError
}

func BuildGoldPriceFromSnapshot(snapshot QuoteSnapshot, symbol string, staleAfterSeconds int, timezone string) (model.GoldPrice, error) {
	price, err := convert(snapshot.XAUJPY.Current, snapshot.USDJPY.Current, snapshot.USDCNY.Current)
	if err != nil {
		return model.GoldPrice{}, err
	}
	open, err := convert(snapshot.XAUJPY.Open, snapshot.USDJPY.Open, snapshot.USDCNY.Open)
	if err != nil {
		return model.GoldPrice{}, err
	}
	prevClose, err := convert(snapshot.XAUJPY.PrevClose, snapshot.USDJPY.PrevClose, snapshot.USDCNY.PrevClose)
	if err != nil {
		return model.GoldPrice{}, err
	}
	rawHigh, err := convert(snapshot.XAUJPY.High, snapshot.USDJPY.High, snapshot.USDCNY.High)
	if err != nil {
		return model.GoldPrice{}, err
	}
	rawLow, err := convert(snapshot.XAUJPY.Low, snapshot.USDJPY.Low, snapshot.USDCNY.Low)
	if err != nil {
		return model.GoldPrice{}, err
	}
	high := round2(maxFloat(price, open, prevClose, rawHigh, rawLow))
	low := round2(minFloat(price, open, prevClose, rawHigh, rawLow))
	change := round2(price - prevClose)
	changePercent := 0.0
	if prevClose > 0 {
		changePercent = round2(change / prevClose * 100)
	}
	updateTime := formatTimestampMS(maxInt64(snapshot.XAUJPY.LatestTimestampMS, snapshot.USDJPY.LatestTimestampMS, snapshot.USDCNY.LatestTimestampMS), timezone)
	isStale := timeutil.IsTimeStale(updateTime, staleAfterSeconds, timezone) || snapshot.USDCNY.IsStale
	return model.GoldPrice{
		Name:          "现货黄金",
		Symbol:        strings.ToUpper(symbol),
		Price:         price,
		Change:        change,
		ChangePercent: changePercent,
		Unit:          "元/克",
		Open:          open,
		PrevClose:     prevClose,
		High:          high,
		Low:           low,
		UpdateTime:    updateTime,
		ServerTime:    timeutil.NowString(timezone),
		Source:        "finnhub",
		MarketStatus:  map[bool]string{true: "closed", false: "trading"}[isStale],
		IsStale:       isStale,
	}, nil
}

func quoteFromStreamPrice(streamPrice StreamPrice) Quote {
	return Quote{
		Current:           streamPrice.Price,
		Open:              streamPrice.Price,
		PrevClose:         streamPrice.Price,
		High:              streamPrice.Price,
		Low:               streamPrice.Price,
		LatestTimestampMS: streamPrice.LatestTimestampMS,
	}
}

func extractAlphaVantageRate(payload map[string]any) (float64, bool) {
	if payload["Error Message"] != nil || payload["Note"] != nil || payload["Information"] != nil {
		return 0, false
	}
	block, ok := payload["Realtime Currency Exchange Rate"].(map[string]any)
	if !ok {
		return 0, false
	}
	return positiveFloat(block["5. Exchange Rate"])
}

func convert(xauValue float64, usdJPYValue float64, usdCNYValue float64) (float64, error) {
	if xauValue <= 0 || usdJPYValue <= 0 || usdCNYValue <= 0 {
		return 0, errors.New("Finnhub quote fields must be positive")
	}
	return round2(xauValue / usdJPYValue * usdCNYValue / troyOunceGrams), nil
}

func websocketURL(endpoint string, token string) string {
	separator := "?"
	if strings.Contains(endpoint, "?") {
		separator = "&"
	}
	return endpoint + separator + url.Values{"token": []string{token}}.Encode()
}

func positiveFloat(value any) (float64, bool) {
	switch typed := value.(type) {
	case float64:
		return typed, typed > 0
	case string:
		parsed, err := strconv.ParseFloat(strings.TrimSpace(typed), 64)
		return parsed, err == nil && parsed > 0
	default:
		return 0, false
	}
}

func timestampMS(value any) int64 {
	switch typed := value.(type) {
	case float64:
		timestamp := int64(typed)
		if timestamp > 0 && timestamp < 1_000_000_000_000 {
			return timestamp * 1000
		}
		return timestamp
	case string:
		parsed, err := strconv.ParseInt(strings.TrimSpace(typed), 10, 64)
		if err != nil {
			return 0
		}
		if parsed > 0 && parsed < 1_000_000_000_000 {
			return parsed * 1000
		}
		return parsed
	default:
		return 0
	}
}

func formatTimestampMS(timestampMS int64, timezone string) string {
	if timestampMS <= 0 {
		return timeutil.NowString(timezone)
	}
	return time.UnixMilli(timestampMS).In(timeutil.Location(timezone)).Format(timeutil.Layout)
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

func maxFloat(values ...float64) float64 {
	result := values[0]
	for _, value := range values[1:] {
		if value > result {
			result = value
		}
	}
	return result
}

func minFloat(values ...float64) float64 {
	result := values[0]
	for _, value := range values[1:] {
		if value < result {
			result = value
		}
	}
	return result
}

func maxInt64(values ...int64) int64 {
	result := values[0]
	for _, value := range values[1:] {
		if value > result {
			result = value
		}
	}
	return result
}

func max(left int, right int) int {
	if left > right {
		return left
	}
	return right
}
