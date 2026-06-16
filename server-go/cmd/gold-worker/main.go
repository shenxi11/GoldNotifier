// 模块名: cmd/gold-worker
// 功能概述: 启动 Go 版行情刷新 Worker。
// 对外接口: main
// 依赖关系: cache、config、provider、service、worker
// 输入输出: 输入上游行情和环境变量，输出 Redis 缓存数据。
// 异常与错误: 启动配置错误会退出，单次刷新失败由 Runner 记录并继续下一轮。
// 维护说明: 本进程不暴露公网端口，部署时应保证同一 Redis 里只有一个有效刷新者持锁。
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/provider"
	"goldnotifier/server-go/internal/provider/finnhub"
	"goldnotifier/server-go/internal/provider/nowapi"
	"goldnotifier/server-go/internal/service"
	"goldnotifier/server-go/internal/worker"
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
	runner := worker.New(settings, createSource(settings), store, svc, logger)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	logger.Info("gold worker starting", "source", settings.DataSource)
	if err := runner.Run(ctx); err != nil && ctx.Err() == nil {
		logger.Error("gold worker stopped with error", "error", err)
		os.Exit(1)
	}
	logger.Info("gold worker stopped")
}

func createSource(settings config.Settings) provider.Source {
	if settings.DataSource == "nowapi" {
		return nowapi.New(settings)
	}
	return finnhub.New(settings)
}
