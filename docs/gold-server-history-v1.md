# GoldNotifier 服务端历史行情存储说明

## 业务目标

服务端开始保存当天获取到的新鲜行情点，供 App 或外部分析方查询当天趋势。当前版本沿用已有 Redis，不新增数据库。

## 已落地行为

- 服务端后台调度器负责统一刷新上游行情，默认每 2 秒刷新一次默认品种；多客户端请求不会放大第三方 API 调用次数。
- `/api/v1/gold/latest` 只读取服务端缓存，不再由客户端请求触发上游刷新。
- 成功刷新到新鲜行情后，服务端会把精简点位追加到 Redis ZSet。
- 历史 key 以 `gold:history:{SYMBOL}:{YYYY-MM-DD}` 命名，默认保留 2 天。
- 服务端会同步维护每日汇总 key `gold:daily_summary:{SYMBOL}:{YYYY-MM-DD}`，保存当天 `open/high/low/close`。
- 如果每日汇总 key 缺失，服务端会从已有历史行情回填汇总，兼容部署前已经写入的历史数据。
- `/api/v1/gold/latest` 的 `open` 取当天第一条新鲜行情，`high/low` 取当天历史最高/最低，`prevClose` 取前一天每日汇总的 `close`。
- 缓存回退、延迟行情和异常价格不会写入历史。
- 新增 `/api/v1/gold/history` 查询接口，支持按日期、时间窗口和 limit 查询。

## 当前边界

- 当前历史能力只覆盖服务端运行期间成功采集到的新鲜行情。
- 如果前一天没有成功采集记录，`prevClose` 会保留上游或兜底值，不阻断 `/latest` 返回。
- 如果上游 Finnhub 配置异常，服务端只能返回 last_success 缓存，不会产生新的历史点。
- 服务启动后第一轮后台刷新成功前，`/latest` 会返回 `code=503`；如已有 last_success 缓存，则返回 `isStale=true` 的缓存兜底。
