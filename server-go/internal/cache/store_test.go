package cache

import (
	"context"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"

	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
)

func TestStoreSuccessKeepsDailySummaryLongerThanHistory(t *testing.T) {
	redisServer := miniredis.RunT(t)
	store := testStore(t, redisServer, map[string]string{
		"HISTORY_RETENTION_DAYS":       "2",
		"DAILY_SUMMARY_RETENTION_DAYS": "3650",
	})

	_, err := store.StoreSuccess(context.Background(), goldPriceAt("2026-06-16 16:00:00", 942.52))
	if err != nil {
		t.Fatal(err)
	}

	historyTTL := redisServer.TTL(historyKey("XAU", "2026-06-16"))
	dailySummaryTTL := redisServer.TTL(dailySummaryKey("XAU", "2026-06-16"))
	if historyTTL != 48*time.Hour {
		t.Fatalf("history TTL = %s", historyTTL)
	}
	if dailySummaryTTL != 3650*24*time.Hour {
		t.Fatalf("daily summary TTL = %s", dailySummaryTTL)
	}
}

func TestDailySummaryReadExtendsExistingShortTTL(t *testing.T) {
	redisServer := miniredis.RunT(t)
	store := testStore(t, redisServer, map[string]string{
		"HISTORY_RETENTION_DAYS":       "2",
		"DAILY_SUMMARY_RETENTION_DAYS": "3650",
	})
	ctx := context.Background()

	_, err := store.StoreSuccess(ctx, goldPriceAt("2026-06-16 16:00:00", 942.52))
	if err != nil {
		t.Fatal(err)
	}
	key := dailySummaryKey("XAU", "2026-06-16")
	redisServer.SetTTL(key, 48*time.Hour)

	if _, err := store.DailySummary(ctx, "XAU", "2026-06-16"); err != nil {
		t.Fatal(err)
	}
	if ttl := redisServer.TTL(key); ttl != 3650*24*time.Hour {
		t.Fatalf("daily summary TTL = %s", ttl)
	}
}

func testStore(t *testing.T, redisServer *miniredis.Miniredis, overrides map[string]string) *Store {
	t.Helper()
	values := map[string]string{
		"REDIS_URL":             "redis://" + redisServer.Addr() + "/0",
		"RATE_LIMIT_PER_MINUTE": "0",
	}
	for key, value := range overrides {
		values[key] = value
	}
	store, err := New(config.FromMap(values))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	return store
}

func goldPriceAt(serverTime string, price float64) model.GoldPrice {
	return model.GoldPrice{
		Name:          "现货黄金",
		Symbol:        "XAU",
		Price:         price,
		Change:        0,
		ChangePercent: 0,
		Unit:          "元/克",
		Open:          price,
		PrevClose:     price,
		High:          price,
		Low:           price,
		UpdateTime:    serverTime,
		ServerTime:    serverTime,
		Source:        "finnhub",
		MarketStatus:  "trading",
	}
}
