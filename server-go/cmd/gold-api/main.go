// 模块名: cmd/gold-api
// 功能概述: 启动 Go 版只读缓存 API 服务。
// 对外接口: main
// 依赖关系: cache、config、httpapi、service
// 输入输出: 输入环境变量和 Redis 缓存，输出兼容 Android 客户端的 HTTP API。
// 异常与错误: Redis 初始化或 HTTP 监听失败会退出进程，运行中业务错误返回 JSON envelope。
// 维护说明: 本进程不得访问上游行情源，避免客户端并发放大第三方 API 请求。
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/httpapi"
	"goldnotifier/server-go/internal/service"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	settings := config.Load()
	store, err := cache.New(settings)
	if err != nil {
		logger.Error("redis init failed", "error", err)
		os.Exit(1)
	}
	defer store.Close()
	svc := service.New(settings, store)
	server := &http.Server{
		Addr:              settings.ServerAddr,
		Handler:           httpapi.NewRouter(settings, svc, store),
		ReadHeaderTimeout: 3 * time.Second,
		ReadTimeout:       5 * time.Second,
		WriteTimeout:      5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		logger.Info("gold api starting", "addr", settings.ServerAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("gold api failed", "error", err)
			stop()
		}
	}()
	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("gold api shutdown failed", "error", err)
	}
	logger.Info("gold api stopped")
}
