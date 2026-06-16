// 模块名: httpapi
// 功能概述: 暴露与 Python 服务端兼容的 HTTP API，并在入口处执行 Redis 限流。
// 对外接口: NewRouter
// 依赖关系: chi、cache、config、model、service
// 输入输出: 输入 HTTP 请求，输出 code/message/data envelope 或 health JSON。
// 异常与错误: 业务错误保持 HTTP 200 和非 0 code，系统错误返回可解析 JSON。
// 维护说明: 本层不得访问 Finnhub、Alpha Vantage 或 NowAPI。
package httpapi

import (
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/model"
	"goldnotifier/server-go/internal/service"
)

func NewRouter(settings config.Settings, svc *service.Service, store *cache.Store) http.Handler {
	router := chi.NewRouter()
	router.Use(rateLimitMiddleware(settings, store))
	router.Get("/api/v1/gold/latest", latestHandler(svc))
	router.Get("/api/v1/gold/history", historyHandler(svc))
	router.Get("/api/v1/gold/candles", candlesHandler(svc))
	router.Get("/api/v1/app/config", appConfigHandler(svc))
	router.Get("/api/v1/health", healthHandler(svc))
	router.Get("/metrics", metricsHandler())
	return router
}

func latestHandler(svc *service.Service) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		symbol := valueOrDefault(r.URL.Query().Get("symbol"), "XAU")
		price, err := svc.Latest(r.Context(), symbol)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, model.Success(price))
	}
}

func historyHandler(svc *service.Service) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		query := r.URL.Query()
		symbol := valueOrDefault(query.Get("symbol"), "XAU")
		startMillis, err := optionalInt64(query.Get("startMillis"))
		if err != nil {
			writeJSON(w, http.StatusOK, model.Error(400, "startMillis must be a valid integer"))
			return
		}
		endMillis, err := optionalInt64(query.Get("endMillis"))
		if err != nil {
			writeJSON(w, http.StatusOK, model.Error(400, "endMillis must be a valid integer"))
			return
		}
		limit := intOrDefault(query.Get("limit"), 2000)
		response, err := svc.History(r.Context(), symbol, query.Get("date"), startMillis, endMillis, limit)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, model.Success(response))
	}
}

func candlesHandler(svc *service.Service) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		query := r.URL.Query()
		symbol := valueOrDefault(query.Get("symbol"), "XAU")
		rangeName := valueOrDefault(query.Get("range"), "5m")
		response, err := svc.Candles(r.Context(), symbol, rangeName)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, model.Success(response))
	}
}

func appConfigHandler(svc *service.Service) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, model.Success(svc.AppConfig()))
	}
}

func healthHandler(svc *service.Service) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, svc.Health(r.Context()))
	}
}

func metricsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4")
		_, _ = w.Write([]byte("# GoldNotifier Go metrics placeholder\n"))
	}
}

func rateLimitMiddleware(settings config.Settings, store *cache.Store) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if settings.RateLimitPerMinute <= 0 {
				next.ServeHTTP(w, r)
				return
			}
			client := clientIP(r)
			key := "gold:ratelimit:" + client
			allowed, err := store.AllowRate(r.Context(), key, settings.RateLimitPerMinute, time.Minute)
			if err != nil {
				next.ServeHTTP(w, r)
				return
			}
			if !allowed {
				writeJSON(w, http.StatusTooManyRequests, model.Error(429, "too many requests"))
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func writeError(w http.ResponseWriter, err error) {
	var serviceError service.Error
	if errors.As(err, &serviceError) {
		writeJSON(w, http.StatusOK, model.Error(serviceError.Code, serviceError.Message))
		return
	}
	writeJSON(w, http.StatusOK, model.Error(503, err.Error()))
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func optionalInt64(value string) (*int64, error) {
	if value == "" {
		return nil, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 {
		if err != nil {
			return nil, err
		}
		return nil, errors.New("value must be non-negative")
	}
	return &parsed, nil
}

func intOrDefault(value string, fallback int) int {
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func valueOrDefault(value string, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func clientIP(r *http.Request) string {
	for _, header := range []string{"X-Forwarded-For", "X-Real-IP"} {
		value := r.Header.Get(header)
		if value == "" {
			continue
		}
		first := strings.TrimSpace(strings.Split(value, ",")[0])
		if first != "" {
			return first
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	return r.RemoteAddr
}
