// 模块名: worker
// 功能概述: 独立执行上游行情刷新、Redis 缓存写入和 K 线预聚合。
// 对外接口: Runner
// 依赖关系: cache、config、provider、service、slog
// 输入输出: 输入上游行情，输出 latest、history、daily_summary、candles 和 source_status 缓存。
// 异常与错误: 单次刷新失败只写 source_status 并等待下一轮，不影响进程继续运行。
// 维护说明: Worker 是唯一允许请求上游的组件，API 进程不得复制本逻辑。
package worker

import (
	"context"
	"log/slog"
	"time"

	"goldnotifier/server-go/internal/cache"
	"goldnotifier/server-go/internal/config"
	"goldnotifier/server-go/internal/provider"
	"goldnotifier/server-go/internal/service"
	"goldnotifier/server-go/internal/timeutil"
)

type Runner struct {
	settings config.Settings
	source   provider.Source
	store    *cache.Store
	service  *service.Service
	logger   *slog.Logger
}

func New(
	settings config.Settings,
	source provider.Source,
	store *cache.Store,
	service *service.Service,
	logger *slog.Logger,
) *Runner {
	return &Runner{
		settings: settings,
		source:   source,
		store:    store,
		service:  service,
		logger:   logger,
	}
}

func (r *Runner) Run(ctx context.Context) error {
	if err := r.refreshOnce(ctx); err != nil {
		r.logger.Warn("initial refresh failed", "error", err)
	}
	ticker := time.NewTicker(time.Duration(max(r.settings.RefreshIntervalSeconds, 1)) * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			if err := r.refreshOnce(ctx); err != nil {
				r.logger.Warn("scheduled refresh failed", "error", err)
			}
		}
	}
}

func (r *Runner) refreshOnce(ctx context.Context) error {
	locked, err := r.store.AcquireLock(
		ctx,
		"gold:worker:refresh:lock",
		time.Duration(max(r.settings.WorkerLockTTLSeconds, 4))*time.Second,
	)
	if err != nil {
		return err
	}
	if !locked {
		r.logger.Debug("refresh skipped by distributed lock")
		return nil
	}
	price, err := r.source.FetchLatest(ctx, r.settings.DefaultSymbol)
	now := timeutil.NowString(r.settings.Timezone)
	if err != nil {
		_ = r.store.SetSourceStatus(ctx, map[string]any{
			"ok":                  false,
			"symbol":              r.settings.DefaultSymbol,
			"error":               err.Error(),
			"serverTime":          now,
			"workerLastRunMillis": timeutil.NowMillis(r.settings.Timezone),
		})
		return err
	}
	enriched, err := r.store.StoreSuccess(ctx, price)
	if err != nil {
		return err
	}
	endMillis := timeutil.NowMillis(r.settings.Timezone)
	if err := r.service.RebuildAllCandles(ctx, enriched.Symbol, endMillis); err != nil {
		return err
	}
	status := map[string]any{
		"ok":                      true,
		"symbol":                  enriched.Symbol,
		"source":                  enriched.Source,
		"serverTime":              enriched.ServerTime,
		"workerLastRunMillis":     endMillis,
		"workerLastSuccessMillis": endMillis,
	}
	if err := r.store.SetSourceStatus(ctx, status); err != nil {
		return err
	}
	r.logger.Info("gold price refreshed", "symbol", enriched.Symbol, "price", enriched.Price, "source", enriched.Source)
	return nil
}

func max(left int, right int) int {
	if left > right {
		return left
	}
	return right
}
