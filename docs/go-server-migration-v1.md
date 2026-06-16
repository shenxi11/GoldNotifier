# GoldNotifier Go 高并发服务端 v1

## 目标

Go 版服务端用于替代当前 Python 单进程 API 的高并发承接能力，同时保持 Android 客户端接口完全兼容。迁移阶段不直接覆盖现有 Python 服务，Go 版本放在 `server-go/`，支持和现有 `server/` 并行验证。

## 架构

- `gold-api`：只读 Redis 缓存，对外提供 Android 当前使用的 HTTP API，不访问 Finnhub、Alpha Vantage 或 NowAPI。
- `gold-worker`：独立刷新上游行情，写入 Redis 最新价、历史点、每日汇总、K 线预聚合缓存和数据源状态。
- `redis`：沿用当前运行态数据存储，保持现有 key 和 JSON 字段兼容。

Go API 保留现有路径：

- `GET /api/v1/gold/latest`
- `GET /api/v1/gold/history`
- `GET /api/v1/gold/candles`
- `GET /api/v1/app/config`
- `GET /api/v1/health`

响应 envelope 继续使用：

```json
{"code":0,"message":"success","data":{}}
```

## Redis 数据

继续使用现有 key：

- `gold:latest:{symbol}`
- `gold:last_success:{symbol}`
- `gold:history:{symbol}:{yyyy-MM-dd}`
- `gold:daily_summary:{symbol}:{yyyy-MM-dd}`
- `gold:source:status`

新增 K 线预聚合缓存：

- `gold:candles:{symbol}:{range}`

`/candles` 优先读预聚合缓存，缓存缺失时才从历史点兜底重建，避免高并发下每个客户端重复聚合。

## 本地验证

```powershell
cd E:\GoldNotifier\server-go
go test ./...
go build ./cmd/gold-api
go build ./cmd/gold-worker
```

并行容器验证：

```powershell
cd E:\GoldNotifier\server-go
docker compose -f docker-compose.go.yml up -d --build
```

Go API 默认映射到 `9988`，用于和当前 Python `9987` 并行对比：

```powershell
Invoke-RestMethod http://64.90.3.109:9988/api/v1/gold/latest?symbol=XAU
Invoke-RestMethod http://64.90.3.109:9988/api/v1/gold/candles?symbol=XAU&range=1h
```

## 迁移步骤

1. 保持 Python 服务继续运行在 `9987`。
2. 启动 Go API 和 Go Worker 到临时端口 `9988`。
3. 对比 Python 与 Go 的 `latest/history/candles/app config/health` 响应结构。
4. Android 测试包切到 `9988` 做功能验证。
5. 验证通过后，停止 Python 服务，把 Go API 切到正式端口 `9987`。
6. 保留 Python 服务端一个版本周期，出现问题时直接回滚容器。

## 回滚

Go 版本保持 Redis key 和 JSON 字段兼容，所以回滚不需要客户端发版：

1. 停止 `gold-api-go` 和 `gold-worker-go`。
2. 重新启动 Python `server/docker-compose.yml`。
3. 确认 `/api/v1/gold/latest` 和 `/api/v1/gold/candles` 正常返回。

## 当前边界

- 默认只支持 `XAU`，其他 symbol 继续返回业务错误。
- 当前 Go Compose 使用独立 Redis 容器和 `9988` 端口，避免误覆盖生产 Python 服务。
- `/metrics` 当前为占位文本端点，后续如需要接 Prometheus 再补正式指标。
- K 线时间戳仍是真实 epoch millis，客户端继续负责 TradingView 北京时间显示修正。
