package httpapi

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/service"
)

func TestLatestContractReturnsEnvelope(t *testing.T) {
	router, store := testRouter(t)
	_, err := store.StoreSuccess(testContext(t), model.GoldPrice{
		Name:          "现货黄金",
		Symbol:        "XAU",
		Price:         942.52,
		Change:        0,
		ChangePercent: 0,
		Unit:          "元/克",
		Open:          942.52,
		PrevClose:     940.00,
		High:          942.52,
		Low:           942.52,
		UpdateTime:    "2026-06-16 16:00:00",
		ServerTime:    "2026-06-16 16:00:00",
		Source:        "finnhub",
		MarketStatus:  "trading",
	})
	if err != nil {
		t.Fatal(err)
	}

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/api/v1/gold/latest?symbol=XAU", nil))

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d", response.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["code"].(float64) != 0 || body["message"].(string) != "success" {
		t.Fatalf("unexpected body: %s", response.Body.String())
	}
	data := body["data"].(map[string]any)
	if data["prevClose"] == nil || data["open"] == nil {
		t.Fatalf("missing Android fields: %s", response.Body.String())
	}
}

func TestLatestWithoutCacheReturnsBusinessErrorEnvelope(t *testing.T) {
	router, _ := testRouter(t)

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/api/v1/gold/latest?symbol=XAU", nil))

	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["code"].(float64) != 503 || body["data"] != nil {
		t.Fatalf("unexpected body: %s", response.Body.String())
	}
}

func TestLatestFallsBackToLastSuccessWhenLatestCacheExpires(t *testing.T) {
	router, store, redisServer := testRouterWithSettings(t, map[string]string{
		"HISTORY_RETENTION_DAYS":   "2",
		"LAST_SUCCESS_TTL_SECONDS": "1",
		"LATEST_CACHE_TTL_SECONDS": "1",
	})
	_, err := store.StoreSuccess(testContext(t), model.GoldPrice{
		Name:          "现货黄金",
		Symbol:        "XAU",
		Price:         942.52,
		Change:        0,
		ChangePercent: 0,
		Unit:          "元/克",
		Open:          942.52,
		PrevClose:     940.00,
		High:          942.52,
		Low:           942.52,
		UpdateTime:    "2026-06-16 16:00:00",
		ServerTime:    "2026-06-16 16:00:00",
		Source:        "finnhub",
		MarketStatus:  "trading",
	})
	if err != nil {
		t.Fatal(err)
	}
	redisServer.FastForward(2 * time.Second)

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/api/v1/gold/latest?symbol=XAU", nil))

	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["code"].(float64) != 0 {
		t.Fatalf("unexpected body: %s", response.Body.String())
	}
	data := body["data"].(map[string]any)
	if data["source"].(string) != "cache" || data["isStale"].(bool) != true {
		t.Fatalf("expected stale cache fallback: %s", response.Body.String())
	}
}

func TestCandlesContractReturnsBars(t *testing.T) {
	router, store := testRouter(t)
	err := store.SetCandles(testContext(t), model.GoldCandlesResponse{
		Symbol:     "XAU",
		Range:      "1h",
		Resolution: "1m",
		Timezone:   "Asia/Shanghai",
		Count:      1,
		Bars: []model.GoldCandleBar{
			{TimestampMillis: 1_781_596_800_000, Open: 941.00, High: 942.00, Low: 940.50, Close: 941.80},
		},
	})
	if err != nil {
		t.Fatal(err)
	}

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/api/v1/gold/candles?symbol=XAU&range=1h", nil))

	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	data := body["data"].(map[string]any)
	if data["range"].(string) != "1h" || data["resolution"].(string) != "1m" {
		t.Fatalf("unexpected body: %s", response.Body.String())
	}
}

func testRouter(t *testing.T) (http.Handler, *cache.Store) {
	t.Helper()
	router, store, _ := testRouterWithSettings(t, map[string]string{})
	return router, store
}

func testRouterWithSettings(t *testing.T, overrides map[string]string) (http.Handler, *cache.Store, *miniredis.Miniredis) {
	t.Helper()
	redisServer := miniredis.RunT(t)
	values := map[string]string{
		"REDIS_URL":             "redis://" + redisServer.Addr() + "/0",
		"RATE_LIMIT_PER_MINUTE": "0",
	}
	for key, value := range overrides {
		values[key] = value
	}
	settings := config.FromMap(values)
	store, err := cache.New(settings)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	svc := service.New(settings, store)
	return NewRouter(settings, svc, store), store, redisServer
}

func testContext(t *testing.T) context.Context {
	t.Helper()
	return context.Background()
}
