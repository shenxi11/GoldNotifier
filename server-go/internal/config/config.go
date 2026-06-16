// 模块名: config
// 功能概述: 读取并规范化 Go 服务端运行配置，保持与 Python 服务端环境变量兼容。
// 对外接口: Settings、Load、FromMap
// 依赖关系: os、strconv、strings
// 输入输出: 输入进程环境变量，输出 API 与 Worker 可复用的不可变配置。
// 异常与错误: 非法数值回退默认值，避免非关键配置拼写错误导致服务无法启动。
// 维护说明: 第三方密钥只允许来自环境变量，不写入代码或日志。
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Settings struct {
	DataSource                  string
	NowAPIAppKey                string
	NowAPISign                  string
	NowAPIEndpoint              string
	NowAPIApp                   string
	NowAPIGoldID                string
	FinnhubAPIKey               string
	FinnhubWSEndpoint           string
	FinnhubXAUJPYSymbol         string
	FinnhubUSDJPYSymbol         string
	FinnhubStreamTimeoutSeconds float64
	AlphaVantageAPIKey          string
	AlphaVantageEndpoint        string
	AlphaVantageFromCurrency    string
	AlphaVantageToCurrency      string
	AlphaVantageCacheTTLSeconds int
	USDCNYFallbackRate          float64
	DefaultSymbol               string
	RedisURL                    string
	RedisPoolSize               int
	LatestCacheTTLSeconds       int
	LastSuccessTTLSeconds       int
	HistoryRetentionDays        int
	CandlesCacheTTLSeconds      int
	StaleAfterSeconds           int
	UpstreamTimeoutSeconds      float64
	RefreshIntervalSeconds      int
	WorkerLockTTLSeconds        int
	NonTradingRefreshInterval   int
	RateLimitPerMinute          int
	LogLevel                    string
	Timezone                    string
	ServerAddr                  string
	MinRefreshInterval          int
	DefaultRefreshInterval      int
	LatestVersionCode           int
	ForceUpdate                 bool
	Notice                      string
}

func Load() Settings {
	return FromMap(envMap())
}

func FromMap(env map[string]string) Settings {
	redisHost := value(env, "REDIS_HOST", "redis")
	redisPort := value(env, "REDIS_PORT", "6379")
	redisDB := value(env, "REDIS_DB", "0")
	redisURL := value(env, "REDIS_URL", fmt.Sprintf("redis://%s:%s/%s", redisHost, redisPort, redisDB))
	refreshSeconds := intValue(env, "REFRESH_INTERVAL_SECONDS", 2)
	return Settings{
		DataSource:                  strings.ToLower(strings.TrimSpace(value(env, "DATA_SOURCE", "finnhub"))),
		NowAPIAppKey:                firstValue(env, []string{"NOWAPI_APPKEY", "NOWAPI_KEY"}, ""),
		NowAPISign:                  value(env, "NOWAPI_SIGN", ""),
		NowAPIEndpoint:              value(env, "NOWAPI_ENDPOINT", "https://sapi.k780.com"),
		NowAPIApp:                   value(env, "NOWAPI_APP", "finance.gold_price"),
		NowAPIGoldID:                value(env, "NOWAPI_GOLDID", "1053"),
		FinnhubAPIKey:               firstValue(env, []string{"FINNHUB_API_KEY", "FINNHUB_TOKEN"}, ""),
		FinnhubWSEndpoint:           value(env, "FINNHUB_WS_ENDPOINT", "wss://ws.finnhub.io"),
		FinnhubXAUJPYSymbol:         value(env, "FINNHUB_XAU_JPY_SYMBOL", "OANDA:XAU_JPY"),
		FinnhubUSDJPYSymbol:         value(env, "FINNHUB_USD_JPY_SYMBOL", "OANDA:USD_JPY"),
		FinnhubStreamTimeoutSeconds: floatValue(env, "FINNHUB_STREAM_TIMEOUT_SECONDS", 8.0),
		AlphaVantageAPIKey:          value(env, "ALPHA_VANTAGE_API_KEY", ""),
		AlphaVantageEndpoint:        value(env, "ALPHA_VANTAGE_ENDPOINT", "https://www.alphavantage.co/query"),
		AlphaVantageFromCurrency:    strings.ToUpper(value(env, "ALPHA_VANTAGE_FROM_CURRENCY", "USD")),
		AlphaVantageToCurrency:      strings.ToUpper(value(env, "ALPHA_VANTAGE_TO_CURRENCY", "CNY")),
		AlphaVantageCacheTTLSeconds: intValue(env, "ALPHA_VANTAGE_CACHE_TTL_SECONDS", 3600),
		USDCNYFallbackRate:          floatValue(env, "USD_CNY_FALLBACK_RATE", 6.7582),
		DefaultSymbol:               strings.ToUpper(value(env, "DEFAULT_SYMBOL", "XAU")),
		RedisURL:                    redisURL,
		RedisPoolSize:               intValue(env, "REDIS_POOL_SIZE", 50),
		LatestCacheTTLSeconds:       intValue(env, "LATEST_CACHE_TTL_SECONDS", 10),
		LastSuccessTTLSeconds:       intValue(env, "LAST_SUCCESS_TTL_SECONDS", 86400),
		HistoryRetentionDays:        intValue(env, "HISTORY_RETENTION_DAYS", 2),
		CandlesCacheTTLSeconds:      intValue(env, "CANDLES_CACHE_TTL_SECONDS", 10),
		StaleAfterSeconds:           intValue(env, "STALE_AFTER_SECONDS", 180),
		UpstreamTimeoutSeconds:      floatValue(env, "UPSTREAM_TIMEOUT_SECONDS", 8.0),
		RefreshIntervalSeconds:      refreshSeconds,
		WorkerLockTTLSeconds:        intValue(env, "WORKER_LOCK_TTL_SECONDS", max(refreshSeconds*2, 4)),
		NonTradingRefreshInterval:   intValue(env, "NON_TRADING_REFRESH_INTERVAL_SECONDS", 300),
		RateLimitPerMinute:          intValue(env, "RATE_LIMIT_PER_MINUTE", 120),
		LogLevel:                    strings.ToUpper(value(env, "LOG_LEVEL", "INFO")),
		Timezone:                    value(env, "TZ", "Asia/Shanghai"),
		ServerAddr:                  value(env, "SERVER_ADDR", ":8080"),
		MinRefreshInterval:          intValue(env, "MIN_REFRESH_INTERVAL", 3),
		DefaultRefreshInterval:      intValue(env, "DEFAULT_REFRESH_INTERVAL", 3),
		LatestVersionCode:           intValue(env, "LATEST_VERSION_CODE", 1),
		ForceUpdate:                 boolValue(env, "FORCE_UPDATE", false),
		Notice:                      value(env, "NOTICE", ""),
	}
}

func (s Settings) NowAPIConfigured() bool {
	return s.NowAPIAppKey != "" && s.NowAPISign != ""
}

func (s Settings) FinnhubConfigured() bool {
	return s.FinnhubAPIKey != ""
}

func (s Settings) AlphaVantageConfigured() bool {
	return s.AlphaVantageAPIKey != ""
}

func (s Settings) UpstreamConfigured() bool {
	switch s.DataSource {
	case "nowapi":
		return s.NowAPIConfigured()
	case "finnhub":
		return s.FinnhubConfigured()
	default:
		return false
	}
}

func (s Settings) GoldIDForSymbol(symbol string) (string, error) {
	if strings.ToUpper(symbol) != s.DefaultSymbol {
		return "", fmt.Errorf("unsupported symbol: %s", symbol)
	}
	return s.NowAPIGoldID, nil
}

func envMap() map[string]string {
	env := make(map[string]string)
	for _, item := range os.Environ() {
		key, val, ok := strings.Cut(item, "=")
		if ok {
			env[key] = val
		}
	}
	return env
}

func firstValue(env map[string]string, keys []string, fallback string) string {
	for _, key := range keys {
		if val := env[key]; val != "" {
			return val
		}
	}
	return fallback
}

func value(env map[string]string, key string, fallback string) string {
	if val := env[key]; val != "" {
		return val
	}
	return fallback
}

func intValue(env map[string]string, key string, fallback int) int {
	val := env[key]
	if val == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(val)
	if err != nil {
		return fallback
	}
	return parsed
}

func floatValue(env map[string]string, key string, fallback float64) float64 {
	val := env[key]
	if val == "" {
		return fallback
	}
	parsed, err := strconv.ParseFloat(val, 64)
	if err != nil {
		return fallback
	}
	return parsed
}

func boolValue(env map[string]string, key string, fallback bool) bool {
	val := env[key]
	if val == "" {
		return fallback
	}
	switch strings.ToLower(val) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func max(left int, right int) int {
	if left > right {
		return left
	}
	return right
}
