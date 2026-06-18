package config

import "testing"

func TestFromMapKeepsPythonCompatibleDefaults(t *testing.T) {
	settings := FromMap(map[string]string{})

	if settings.DataSource != "finnhub" {
		t.Fatalf("DataSource = %s", settings.DataSource)
	}
	if settings.RefreshIntervalSeconds != 2 {
		t.Fatalf("RefreshIntervalSeconds = %d", settings.RefreshIntervalSeconds)
	}
	if settings.LatestCacheTTLSeconds != 10 {
		t.Fatalf("LatestCacheTTLSeconds = %d", settings.LatestCacheTTLSeconds)
	}
	if settings.LastSuccessTTLSeconds != 604800 {
		t.Fatalf("LastSuccessTTLSeconds = %d", settings.LastSuccessTTLSeconds)
	}
	if settings.DailySummaryRetentionDays != 3650 {
		t.Fatalf("DailySummaryRetentionDays = %d", settings.DailySummaryRetentionDays)
	}
	if settings.DefaultSymbol != "XAU" {
		t.Fatalf("DefaultSymbol = %s", settings.DefaultSymbol)
	}
}

func TestFromMapBuildsRedisURLFromHostAndPort(t *testing.T) {
	settings := FromMap(map[string]string{
		"REDIS_HOST": "cache",
		"REDIS_PORT": "6380",
		"REDIS_DB":   "2",
	})

	if settings.RedisURL != "redis://cache:6380/2" {
		t.Fatalf("RedisURL = %s", settings.RedisURL)
	}
}

func TestInvalidNumbersFallbackToDefaults(t *testing.T) {
	settings := FromMap(map[string]string{
		"RATE_LIMIT_PER_MINUTE":        "bad",
		"USD_CNY_FALLBACK_RATE":        "bad",
		"DAILY_SUMMARY_RETENTION_DAYS": "bad",
	})

	if settings.RateLimitPerMinute != 120 {
		t.Fatalf("RateLimitPerMinute = %d", settings.RateLimitPerMinute)
	}
	if settings.USDCNYFallbackRate != 6.7582 {
		t.Fatalf("USDCNYFallbackRate = %f", settings.USDCNYFallbackRate)
	}
	if settings.DailySummaryRetentionDays != 3650 {
		t.Fatalf("DailySummaryRetentionDays = %d", settings.DailySummaryRetentionDays)
	}
}

func TestFromMapReadsDailySummaryRetentionDays(t *testing.T) {
	settings := FromMap(map[string]string{
		"DAILY_SUMMARY_RETENTION_DAYS": "730",
	})

	if settings.DailySummaryRetentionDays != 730 {
		t.Fatalf("DailySummaryRetentionDays = %d", settings.DailySummaryRetentionDays)
	}
}
