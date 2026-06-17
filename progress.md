## 2026-06-15 - Task: 落地 App 端黄金价格实时趋势图 v1.1

### What was done
- 在客户端内实现 3 秒刷新节奏下的实时趋势点采集、5 分钟内存窗口和缓存/延迟行情过滤。
- 首页新增实时趋势卡片和 Canvas 折线图，不新增服务端接口、不引入图表库。
- 增加低频 DataStore 趋势快照，用于短时间回到 App 时恢复趋势图。
- 首页改为可滚动布局，避免新增趋势卡片影响小屏设备上的控制区访问。
- 增加趋势缓冲区单元测试，并补充 v1.1 落地说明文档。

### Testing
- `..\gradlew.bat testDebugUnitTest`：通过。

### Notes
- `app/src/main/java/com/example/goldnotifier/domain/model/GoldTrendPoint.kt`：新增趋势点和趋势快照领域模型。
- `app/src/main/java/com/example/goldnotifier/domain/trend/TrendPointBuffer.kt`：新增客户端趋势点缓冲区和追加结果枚举。
- `app/src/test/java/com/example/goldnotifier/domain/trend/TrendPointBufferTest.kt`：新增趋势点过滤、裁剪和恢复单元测试。
- `app/src/main/java/com/example/goldnotifier/data/local/UserSettingsDataStore.kt`：新增趋势快照 DataStore 读写能力。
- `app/src/main/java/com/example/goldnotifier/data/repository/GoldRepository.kt`：向 ViewModel 暴露趋势快照读写入口。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeViewModel.kt`：接入趋势点采集、UI 状态和快照保存/恢复逻辑。
- `app/src/main/java/com/example/goldnotifier/MainActivity.kt`：App 停止时触发趋势快照保存。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendCard.kt`：新增实时趋势摘要卡片。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendChart.kt`：新增 Compose Canvas 趋势折线图。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeScreen.kt`：首页接入趋势卡片并改为可滚动布局。
- `app/src/test/java/com/example/goldnotifier/data/repository/GoldRepositoryTest.kt`：同步 Fake 本地存储接口。
- `docs/gold-realtime-trend-v1.1.md`：新增本轮功能落地说明。
- 回滚方式：使用 Git 回退本轮涉及的上述文件；若只需关闭入口，可先从 `HomeScreen` 移除 `GoldRealtimeTrendCard` 调用，再移除 ViewModel 的趋势采集和 DataStore 快照接口。

## 2026-06-15 - Task: 新增服务端当天历史行情存储功能

### What was done
- 在服务端新增当天历史行情存储能力，成功刷新到新鲜行情后追加写入 Redis ZSet。
- 新增 `/api/v1/gold/history` 查询接口，支持按 symbol、日期、时间窗口和 limit 查询。
- 保持 `/api/v1/gold/latest` 原有响应契约不变，缓存回退行情不会写入历史。
- 新增服务端缓存层、服务层和 API 契约测试，并补充历史行情接口文档。

### Testing
- `.\.venv\Scripts\python.exe -m pytest`：通过，26 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。

### Notes
- `server/model/gold_history.py`：新增历史行情点和历史查询响应模型。
- `server/utils/time_utils.py`：新增时间戳和日期字符串转换工具。
- `server/service/cache_service.py`：新增历史行情 ZSet 写入和查询能力。
- `server/service/gold_service.py`：新增历史行情查询业务方法。
- `server/api/gold_history_api.py`：新增历史行情 HTTP 查询接口。
- `server/app.py`：挂载历史行情路由。
- `server/config.py`、`server/.env.example`、`server/docker-compose.yml`：新增 `HISTORY_RETENTION_DAYS` 配置。
- `server/tests/test_cache_service.py`：新增 Redis 历史缓存策略测试。
- `server/tests/test_gold_service.py`、`server/tests/test_api_contract.py`：补充历史查询服务层和 API 契约测试。
- `server/docs/gold_history_api.md`、`server/docs/gold_latest_api.md`、`docs/gold-server-history-v1.md`：补充服务端历史行情说明。
- 回滚方式：使用 Git 回退本轮涉及的上述 server 文件和根目录文档；若只需临时关闭查询入口，可先从 `server/app.py` 移除 `gold_history_router` 挂载。

## 2026-06-15 - Task: 服务端用本地历史行情生成 open/high/low/昨收

### What was done
- 服务端新增每日行情汇总缓存，随每次新鲜历史行情写入同步维护当天 `open/high/low/close`。
- `/api/v1/gold/latest` 返回前用本地每日汇总覆盖上游 OHLC 兜底值：当天第一条作为 `open`，当天历史最高/最低作为 `high/low`。
- 今天的 `prevClose` 改为读取前一天每日汇总的 `close`；前一天无记录时保留上游或兜底值，避免接口失败。
- 同步更新服务端最新行情与历史行情接口说明，以及根目录服务端历史行情说明。

### Testing
- `.\.venv\Scripts\python.exe -m pytest tests/test_cache_service.py tests/test_gold_service.py tests/test_api_contract.py`：通过，16 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- `.\.venv\Scripts\python.exe -m pytest`：通过，28 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- UTF-8 解码检查：本轮修改的服务端源码、测试和文档均可按 UTF-8 正常读取。

### Notes
- `server/model/gold_history.py`：新增 `GoldDailySummary` 每日汇总模型。
- `server/utils/time_utils.py`：新增前一天日期计算工具。
- `server/service/cache_service.py`：新增每日汇总写入/读取，并在成功行情缓存前生成本地历史聚合后的价格字段。
- `server/service/gold_service.py`：返回缓存层增强后的最新行情。
- `server/tests/test_cache_service.py`：新增当天 open/high/low 和前一天 close 作为昨收的缓存层测试。
- `server/tests/test_gold_service.py`：补充服务层返回增强行情的测试。
- `server/docs/gold_latest_api.md`、`server/docs/gold_history_api.md`：更新 latest 字段来源和每日汇总存储说明。
- `docs/gold-server-history-v1.md`：同步服务端历史行情能力说明。
- 回滚方式：使用 Git 回退本轮涉及的上述文件；如只需临时停用本地 OHLC，可让 `GoldService.refresh` 忽略 `store_success` 返回值并恢复直接返回上游 `price`。

## 2026-06-15 - Task: 提交并部署服务端每日汇总行情能力

### What was done
- 将服务端本地历史行情生成 `open/high/low/prevClose` 的改动提交并推送到 GitHub。
- 补充每日汇总缺失时从已有历史行情回填的兼容逻辑，避免部署当天只从新点位开始计算 `open/high/low`。
- 在服务器 `/opt/gold-notifier/server` 拉取最新代码，重建并重启 `gold-api` 容器。
- 线上验证 `health`、`latest`、`history` 和 Redis 每日汇总 key，确认昨天收盘价已作为今天 `prevClose` 使用。

### Testing
- `.\.venv\Scripts\python.exe -m pytest`：通过，29 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- 线上 `/api/v1/health`：`ok=true`、`redis.ok=true`、`alphaVantageConfigured=true`。
- 线上 `/api/v1/gold/latest?symbol=XAU`：返回 `code=0`、`source=finnhub`、`isStale=false`，`prevClose=945.79`。
- Redis `gold:daily_summary:XAU:2026-06-15`：存在，`close=945.79`；`gold:daily_summary:XAU:2026-06-16`：存在，`open=946.76`。

### Notes
- 服务端提交 `f0f0cd8 add local daily ohlc summary`：新增每日汇总缓存并让 `/latest` 返回本地聚合后的行情字段。
- 服务端提交 `137c7ba backfill daily summary from history`：每日汇总 key 缺失时从已有历史行情回填。
- 远端容器已运行 `137c7ba`，`gold-api` 与 `gold-redis` 均为 Up。
- `docs/gold-server-history-v1.md`：补充每日汇总缺失时可从历史行情回填的说明。
- 回滚方式：在服务器 `/opt/gold-notifier/server` 执行 `git checkout 6754895` 后 `docker compose up -d --build gold-api`，或在 GitHub 回退 `f0f0cd8`、`137c7ba` 两个提交后重新部署。

## 2026-06-16 - Task: 客户端趋势图支持多时间尺度切换

### What was done
- 首页趋势卡片新增时间尺度选择，支持近 5 分钟、近 1 小时、近 6 小时、近 1 天。
- 客户端接入服务端 `/api/v1/gold/history`，切换尺度时按时间窗口查询历史行情。
- Repository 支持按 `Asia/Shanghai` 日期拆分历史查询，并在单页超过 `10000` 点时继续分页获取更早数据。
- ViewModel 将服务端历史点与本地最新刷新点合并，刷新成功后当前尺度图表可继续更新。
- 长窗口绘图前增加趋势点抽样，最多绘制 `720` 个点并保留首尾点，降低近一天数据的 Canvas 压力。
- 新增 v1.2 功能说明文档。

### Testing
- `.\gradlew.bat testDebugUnitTest`：通过。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- UTF-8 解码检查：本轮新增和修改的客户端源码、测试和文档均可按 UTF-8 正常读取。

### Notes
- `app/src/main/java/com/example/goldnotifier/data/api/GoldApi.kt`：新增历史行情 Retrofit 接口。
- `app/src/main/java/com/example/goldnotifier/data/model/GoldHistoryDto.kt`：新增历史行情 DTO 和领域点映射。
- `app/src/main/java/com/example/goldnotifier/data/repository/GoldRepository.kt`：新增趋势历史查询、跨日期拆分和分页逻辑。
- `app/src/main/java/com/example/goldnotifier/domain/trend/TrendTimeRange.kt`：新增趋势图时间尺度枚举。
- `app/src/main/java/com/example/goldnotifier/domain/trend/TrendPointSampler.kt`：新增长窗口趋势点抽样工具。
- `app/src/main/java/com/example/goldnotifier/domain/trend/TrendPointBuffer.kt`：支持按指定窗口生成快照。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeViewModel.kt`：新增时间尺度状态、历史加载和历史/实时点合并逻辑。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeScreen.kt`、`app/src/main/java/com/example/goldnotifier/MainActivity.kt`：接入时间尺度选择回调。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendCard.kt`：新增时间尺度分段选择控件和加载态展示。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendChart.kt`：同步说明为通用时间尺度趋势图。
- `app/src/test/java/com/example/goldnotifier/data/repository/GoldRepositoryTest.kt`：新增跨日期历史查询测试，并同步 Fake API。
- `app/src/test/java/com/example/goldnotifier/domain/trend/TrendPointBufferTest.kt`：新增指定窗口快照测试。
- `app/src/test/java/com/example/goldnotifier/domain/trend/TrendPointSamplerTest.kt`：新增抽样策略测试。
- `docs/gold-trend-time-range-v1.2.md`：新增多时间尺度趋势图落地说明。
- 回滚方式：使用 Git 回退上述文件；若只需临时关闭入口，可先从 `GoldRealtimeTrendCard` 移除 `TrendRangeSelector` 并让 `HomeViewModel` 固定使用 `TrendTimeRange.FiveMinutes`。

## 2026-06-16 - Task: 服务端统一刷新金价缓存

### What was done
- `/api/v1/gold/latest` 改为只读取服务端缓存，不再由客户端请求触发 Finnhub 或 Alpha Vantage 上游刷新。
- 服务端后台调度器默认每 2 秒刷新一次默认品种，并在服务启动后立即执行第一轮刷新。
- 最新行情短缓存默认 TTL 调整为 10 秒；短缓存过期时回退最近成功缓存并标记 `source="cache"`、`isStale=true`。
- 容器配置和环境变量示例同步启用服务端调度刷新，接口文档同步新的缓存读取语义。

### Testing
- `.\.venv\Scripts\python.exe -m pytest`：通过，31 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- `git -C server diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- `git diff --check -- docs/gold-server-history-v1.md progress.md`：通过。
- UTF-8 解码检查：本轮修改的服务端源码、测试、配置和文档均可按 UTF-8 正常读取。

### Notes
- `server/api/gold_api.py`：`/latest` 路由改为调用缓存读取，不再传入强制刷新参数。
- `server/service/gold_service.py`：`latest()` 改为按 latest 缓存、last_success 缓存、503 错误的顺序返回，不访问上游。
- `server/scheduler.py`：定时任务增加启动即刷。
- `server/config.py`：默认刷新间隔改为 2 秒，latest 短缓存 TTL 改为 10 秒。
- `server/docker-compose.yml`、`server/.env.example`：同步服务端调度刷新相关运行配置。
- `server/tests/test_gold_service.py`、`server/tests/test_api_contract.py`：新增缓存读取、不触发 refresh 和默认配置断言。
- `server/docs/gold_latest_api.md`、`server/docs/gold_history_api.md`、`docs/gold-server-history-v1.md`：同步服务端统一刷新和客户端只读缓存的说明。
- 回滚方式：使用 Git 回退上述文件；如需临时恢复旧行为，可先将 `server/api/gold_api.py` 改回调用 `service.latest(symbol, force_refresh=True)`，但这会重新产生多客户端放大上游请求的问题。

## 2026-06-16 - Task: 部署服务端统一刷新版本

### What was done
- 将服务端统一刷新缓存改动提交并推送到 GitHub。
- 在服务器 `/opt/gold-notifier/server` 拉取最新提交，重建并重启 `gold-api` 容器。
- 线上确认调度器按 2 秒间隔刷新默认品种，客户端 `/latest` 请求读取服务端缓存。

### Testing
- 本地 `.\.venv\Scripts\python.exe -m pytest`：通过，31 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- 本地 `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- 服务器 `docker compose ps`：`gold-api` 与 `gold-redis` 均为 Up。
- 服务器内网 `/api/v1/health`：`ok=true`、`redis.ok=true`、`sourceStatus.ok=true`。
- 服务器内网 `/api/v1/gold/latest?symbol=XAU`：返回 `code=0`、`source=finnhub`、`isStale=false`。
- 服务器内网 `/api/v1/gold/history?symbol=XAU&limit=3`：返回连续历史点，时间间隔约 2 秒。
- Redis `ttl gold:latest:XAU`：返回 `9`，符合 10 秒短缓存策略。
- 公网 `http://64.90.3.109:9987/api/v1/gold/latest?symbol=XAU`：返回 `code=0`、`source=finnhub`、`isStale=false`。

### Notes
- 服务端提交 `94bc4a1 centralize gold refresh scheduler`：上线 `/latest` 只读缓存、后台 2 秒统一刷新和启动即刷。
- 远端容器已运行提交 `94bc4a1`。
- 服务器部署目录存在未跟踪备份文件 `.env.backup.20260615143830`，本轮未修改。
- `progress.md`：追加本次提交、部署和线上验证记录。
- 回滚方式：在服务器 `/opt/gold-notifier/server` 执行 `git checkout 137c7ba` 后 `docker compose up -d --build gold-api`；或在 GitHub 回退 `94bc4a1` 后重新部署。

## 2026-06-16 - Task: 客户端趋势图增加左侧价格刻度

### What was done
- 趋势图左侧新增独立价格刻度区，每条横向网格线对应一个实际价格。
- 价格刻度按当前展示点位动态计算，顶部为最高价，底部为最低价，中间刻度按等距跨度生成。
- 折线绘图区向右收缩，避免刻度文字遮挡折线、网格线和最后一个点。
- 同步更新趋势图时间尺度说明文档，并补充价格刻度计算单元测试。

### Testing
- `.\gradlew.bat testDebugUnitTest`：通过。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- UTF-8 和尾随空白检查：本轮修改/新增的趋势图源码、测试和文档均通过。

### Notes
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendChart.kt`：新增左侧价格刻度绘制、右侧绘图区坐标重算和刻度计算函数。
- `app/src/test/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendChartTest.kt`：新增最高/最低边界、动态等距跨度、平盘点位和非法输入测试。
- `docs/gold-trend-time-range-v1.2.md`：补充左侧动态价格刻度说明。
- `progress.md`：追加本次客户端趋势图刻度改动记录。
- 回滚方式：使用 Git 回退上述文件；如只需临时隐藏刻度，可先将 `GoldRealtimeTrendChart` 恢复为原来的全宽绘图区和无文本刻度绘制。

## 2026-06-16 - Task: 根据 TSX 设计稿重设计移动端 UI

### What was done
- 将 Android 首页调整为 `goldpriceapp.tsx` 对应的深色行情盘风格。
- 首页保留现有真实业务能力，不新增无功能的搜索、铃铛或返回入口。
- 主行情区突出展示价格、涨跌、更新时间和 OHLC；趋势区改为深色分段控件和高图表区；设置区改为深色控制面板。
- 新增移动端 UI 重设计说明文档，并准备将客户端源码、测试、文档和进度记录提交推送到 GitHub。

### Testing
- `.\gradlew.bat testDebugUnitTest`：通过。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- UTF-8 解码检查：本轮修改的 Kotlin、Markdown 和进度日志文件均需通过。

### Notes
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeScreen.kt`：首页改为黑底深灰分段布局，并重绘设置区和底部提示。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldPriceCard.kt`：主行情卡改为深色大价格、涨跌状态和一行四列 OHLC 指标。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendCard.kt`：趋势卡改为深色时间段选择器、右侧趋势摘要和更高图表区域。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendChart.kt`：图表网格、基准线和刻度文字改为深色行情盘配色。
- `docs/gold-mobile-ui-redesign-v1.3.md`：新增 TSX 设计稿到 Android Compose 的落地说明。
- `progress.md`：追加本次移动端 UI 重设计记录。
- 回滚方式：使用 Git 回退本轮客户端 UI 提交；如只需临时恢复旧首页，可优先回退 `HomeScreen`、`GoldPriceCard`、`GoldRealtimeTrendCard` 和 `GoldRealtimeTrendChart`。

## 2026-06-16 - Task: 推送客户端深色行情 UI 版本

### What was done
- 将客户端趋势图、多时间尺度和深色行情 UI 改动提交到客户端仓库。
- 推送 `main` 到 GitHub，供外部分析和后续构建使用。

### Testing
- 代码提交前 `.\gradlew.bat testDebugUnitTest`：通过。
- 代码提交前 `git diff --cached --check`：通过。
- 代码提交前 UTF-8 解码检查：本轮修改的 Kotlin、Markdown 和进度日志文件均通过。
- `git push origin main`：通过。

### Notes
- 客户端 UI 代码提交 `22a03a1 redesign mobile gold dashboard`：包含深色行情 UI、趋势图多时间尺度和相关测试文档。
- 本轮未提交 `server` 子仓库指针、`AGENTS.md` 或 `设计文档` 下的设计源文件。
- `progress.md`：追加本次客户端仓库推送记录。
- 回滚方式：执行 `git revert 22a03a1` 后重新推送；如同时回滚本记录，再回退本条进度日志提交。

## 2026-06-16 - Task: 落地 TradingView K 线查看方案

### What was done
- 服务端新增 `/api/v1/gold/candles`，按 `5m/1h/6h/1d` 窗口统一聚合 OHLC K 线，不主动刷新上游。
- 客户端新增 K 线数据链路和 `折线 / K线` 切换，默认保留原折线图，K 线使用 TradingView Lightweight Charts Android wrapper 渲染。
- 前台刷新到新鲜最新价时，客户端只更新当前最后一根 K 线，避免每 3 秒全量重拉 K 线。
- 同步补充服务端接口文档和客户端 TradingView K 线落地说明。

### Testing
- 服务端 `.\.venv\Scripts\python.exe -m pytest`：通过，34 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- 客户端 `.\gradlew.bat testDebugUnitTest`：通过。
- 客户端 `.\gradlew.bat assembleDebug`：通过；`stripDebugDebugSymbols` 提示部分三方 native 库无法 strip，已按原样打包。
- 根仓库 `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- 服务端子仓库 `git -C server diff --check`：通过，仅提示 Windows 工作区换行转换警告。

### Notes
- `server/model/gold_history.py`：新增 `GoldCandleBar` 与 `GoldCandlesResponse` 响应模型。
- `server/service/gold_service.py`：新增 candles 查询、窗口到聚合粒度映射和 OHLC 聚合逻辑。
- `server/api/gold_history_api.py`：新增 `/api/v1/gold/candles` 路由。
- `server/tests/test_api_contract.py`、`server/tests/test_gold_service.py`：补充 K 线 API 契约和聚合规则测试。
- `server/docs/gold_history_api.md`：补充 candles 接口、响应结构和聚合口径。
- `gradle/libs.versions.toml`、`app/build.gradle.kts`：新增 `com.tradingview:lightweightcharts:4.0.0` 依赖。
- `app/src/main/java/com/example/goldnotifier/data/api/GoldApi.kt`、`app/src/main/java/com/example/goldnotifier/data/model/GoldHistoryDto.kt`、`app/src/main/java/com/example/goldnotifier/data/repository/GoldRepository.kt`：新增 candles API、DTO 映射和仓库方法。
- `app/src/main/java/com/example/goldnotifier/domain/model/GoldCandle.kt`、`app/src/main/java/com/example/goldnotifier/domain/trend/TrendChartMode.kt`、`app/src/main/java/com/example/goldnotifier/domain/trend/TrendTimeRange.kt`：新增 K 线领域模型、图表模式和周期映射。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeViewModel.kt`、`app/src/main/java/com/example/goldnotifier/ui/screen/HomeScreen.kt`、`app/src/main/java/com/example/goldnotifier/MainActivity.kt`：接入 K 线状态、模式切换和刷新更新逻辑。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendCard.kt`、`app/src/main/java/com/example/goldnotifier/ui/component/TradingViewKLineChart.kt`：新增折线/K 线切换和 TradingView K 线渲染。
- `app/src/test/java/com/example/goldnotifier/data/repository/GoldRepositoryTest.kt`：补充客户端 candles 数据链路测试。
- `docs/gold-tradingview-kline-v1.md`：新增客户端 K 线落地说明。
- `progress.md`：追加本次 TradingView K 线落地记录。
- 回滚方式：客户端回退上述 app、gradle、docs、progress 文件；服务端在 `server` 子仓库回退上述 API、service、model、tests、docs 文件。若已提交，可分别在根仓库和 `server` 子仓库执行对应提交的 `git revert`。

## 2026-06-16 - Task: 提交并部署 TradingView K 线服务端

### What was done
- 将服务端 `/api/v1/gold/candles` 改动提交并推送到 GitHub。
- 服务器 `/opt/gold-notifier/server` 拉取服务端提交 `d518a9f`，重建并重启 `gold-api` 容器。
- 公网验证 health、latest 和 `5m/1h/6h/1d` 四个 K 线窗口均可访问。

### Testing
- 服务端提交前 `.\.venv\Scripts\python.exe -m pytest`：通过，34 passed，1 个 FastAPI/Starlette TestClient 依赖弃用警告。
- 服务端提交前 `git diff --cached --check`：通过，仅提示 Windows 工作区换行转换警告。
- 服务器 `docker compose ps`：`gold-api` 与 `gold-redis` 均为 Up。
- 公网 `/api/v1/health`：`ok=true`、`redis.ok=true`、`sourceStatus.ok=true`。
- 公网 `/api/v1/gold/latest?symbol=XAU`：返回 `code=0`、`source=finnhub`、`isStale=false`。
- 公网 `/api/v1/gold/candles?symbol=XAU&range=5m`：返回 `code=0`、`resolution=15s`、`count=21`。
- 公网 `/api/v1/gold/candles?symbol=XAU&range=1h`：返回 `code=0`、`resolution=1m`、`count=61`。
- 公网 `/api/v1/gold/candles?symbol=XAU&range=6h`：返回 `code=0`、`resolution=5m`、`count=67`。
- 公网 `/api/v1/gold/candles?symbol=XAU&range=1d`：返回 `code=0`、`resolution=15m`、`count=45`。

### Notes
- 服务端提交 `d518a9f add gold candle history api`：新增 K 线接口、聚合逻辑、测试和接口文档。
- 远端容器已运行提交 `d518a9f`。
- 服务器部署目录仍存在未跟踪备份文件 `.env.backup.20260615143830`，本轮未修改。
- `progress.md`：追加本次服务端提交和部署记录。
- 回滚方式：在服务器 `/opt/gold-notifier/server` 执行 `git checkout 94bc4a1` 后 `docker compose up -d --build gold-api`；或在 GitHub 回退 `d518a9f` 后重新部署。

## 2026-06-16 - Task: 优化 TradingView K 线拖拽卡顿

### What was done
- 将 K 线图更新策略从每次 Compose 更新都全量 `setData + fitContent`，调整为首次加载和切换周期才全量刷新。
- 同一周期内最新 K 线变化时改用 TradingView `series.update` 增量更新，避免拖拽时被全量数据刷新和视图重置打断。
- K 线 WebView 触摸期间请求父级不拦截触摸事件，减少与首页纵向滚动的手势竞争。
- 刷新到的价格没有改变当前 K 线时，不更新 `HomeUiState`，减少无效重组。
- 补充 K 线更新策略单元测试，并更新客户端 K 线说明文档。

### Testing
- `.\gradlew.bat testDebugUnitTest`：通过。
- `.\gradlew.bat assembleDebug`：通过。
- `git diff --check`：通过，仅提示 Windows 工作区换行转换警告。
- 真机 `QGP7NVR4J7RW7HY5` 安装 Debug APK 并切到 K 线后，使用同一套 adb 横向拖拽脚本采集 `dumpsys gfxinfo`。
- 修复前诊断样本：`Janky frames=11.86%`、`High input latency=599`。
- 本轮稳定策略最终样本：`Janky frames=17.60%`、`High input latency=448`。输入延迟低于诊断样本，但自动化 jank 比例未达到 5% 目标。

### Notes
- `app/src/main/java/com/example/goldnotifier/ui/component/TradingViewKLineChart.kt`：新增增量更新控制器、触摸拦截委托和更新决策函数。
- `app/src/main/java/com/example/goldnotifier/ui/component/GoldRealtimeTrendCard.kt`：向 K 线组件传入当前时间周期，供周期切换时判断全量刷新。
- `app/src/main/java/com/example/goldnotifier/ui/screen/HomeViewModel.kt`：避免未改变 K 线时重复更新 UI 状态。
- `app/src/test/java/com/example/goldnotifier/ui/component/TradingViewKLineChartTest.kt`：新增首次加载、周期切换、最后一根更新、新增一根和历史修正的更新策略测试。
- `docs/gold-tradingview-kline-v1.md`：补充 K 线性能策略。
- `progress.md`：追加本次 K 线拖拽卡顿优化记录。
- 残余风险：TradingView Android wrapper 基于 WebView，自动化横向拖拽仍有明显 jank；若后续需要继续压到 5% 以下，建议使用 Perfetto 定位 WebView/JS 渲染瓶颈，或评估改为 Compose Canvas 自绘 K 线。
- 回滚方式：回退上述 Kotlin、测试、文档和 progress 文件；如已提交，可执行本轮客户端提交的 `git revert`。

## 2026-06-16 - Task: 修复 TradingView K 线时间轴显示 UTC 时间

### What was done
- 确认服务端 `/api/v1/gold/candles` 返回的 `timestampMillis` 是真实 epoch millis，`timezone` 为 `Asia/Shanghai`；示例 K 线为 UTC `08:01`，对应北京时间 `16:01`。
- 客户端保留领域模型中的真实 `timestampMillis`，仅在传给 TradingView 时间轴时增加北京时间显示偏移，避免 TradingView UTC 轴把北京时间 `16:00` 显示为 `08:00`。
- 补充 K 线时间轴回归测试，锁定北京时间 `2026-06-16 16:00` 在 TradingView UTC 轴上仍显示为 `16:00`。
- 更新 TradingView K 线说明文档，明确服务端时间戳与客户端显示偏移的边界。

### Testing
- `Invoke-RestMethod http://64.90.3.109:9987/api/v1/gold/candles?symbol=XAU&range=1h`：返回 `timezone=Asia/Shanghai`，样例 `1781596860000` 对应 UTC `2026-06-16 08:01:00`、北京时间 `2026-06-16 16:01:00 +08:00`。
- `.\gradlew.bat testDebugUnitTest assembleDebug`：通过。
- 真机 `QGP7NVR4J7RW7HY5` 安装 Debug APK 并切到 K 线，截图确认设备 `16:11` 时 K 线底部显示 `16:07`、`16:10`，不再显示 UTC 的 `08:07`、`08:10`。

### Notes
- `app/src/main/java/com/example/goldnotifier/ui/component/TradingViewKLineChart.kt`：新增 TradingView 时间轴北京时间显示换算，K 线排序和增量更新仍使用原始时间戳。
- `app/src/test/java/com/example/goldnotifier/ui/component/TradingViewKLineChartTest.kt`：新增北京时间到 TradingView UTC 轴显示时间的回归测试。
- `docs/gold-tradingview-kline-v1.md`：补充 K 线时间轴显示口径。
- `progress.md`：追加本次 K 线时间轴修复记录。
- 回滚方式：恢复上述 4 个文件到提交前版本，或在提交后使用 `git revert <本次提交>` 回退。

## 2026-06-16 - Task: 新增 Go 高并发服务端实现

### What was done
- 新增独立 `server-go/` 模块，保留 Python 服务端不变，便于并行验证和快速回滚。
- 实现 Go 版 `gold-api` 只读缓存服务，兼容现有 Android API 路径、字段和 `code/message/data` envelope。
- 实现 Go 版 `gold-worker` 刷新服务，独立请求 Finnhub、Alpha Vantage 或 NowAPI，并写入 Redis latest、history、daily summary、source status 和 K 线预聚合缓存。
- 新增 Redis 级 K 线缓存 `gold:candles:{symbol}:{range}`，避免高并发客户端重复触发 K 线聚合。
- 新增 Go 版 Dockerfile、并行验证用 Compose 文件和迁移说明文档。

### Testing
- `cd server-go; go test ./...`：通过。
- `cd server-go; go build ./cmd/gold-api ./cmd/gold-worker`：通过。
- `cd server; .\.venv\Scripts\python.exe -m pytest`：34 passed，1 个 Starlette/httpx2 兼容性 warning。
- `.\gradlew.bat testDebugUnitTest`：通过。

### Notes
- `server-go/go.mod`、`server-go/go.sum`：新增 Go 模块和依赖锁定。
- `server-go/cmd/gold-api/main.go`：新增只读缓存 API 进程入口。
- `server-go/cmd/gold-worker/main.go`：新增行情刷新 Worker 进程入口。
- `server-go/internal/config/config.go`、`server-go/internal/config/config_test.go`：新增环境变量兼容配置和默认值测试。
- `server-go/internal/model/model.go`：新增与 Android/Python 契约兼容的响应和业务模型。
- `server-go/internal/timeutil/timeutil.go`：新增时间格式、时区、日期和 stale 判断工具。
- `server-go/internal/candles/candles.go`、`server-go/internal/candles/candles_test.go`：新增 K 线聚合逻辑和 OHLC 测试。
- `server-go/internal/cache/store.go`：新增 Redis latest、last_success、history、daily_summary、candles、限流和锁封装。
- `server-go/internal/service/service.go`：新增 API 只读业务服务和 K 线缓存兜底重建逻辑。
- `server-go/internal/httpapi/router.go`、`server-go/internal/httpapi/router_test.go`：新增兼容 HTTP 路由、Redis 限流和契约测试。
- `server-go/internal/provider/source.go`：新增 Worker 上游数据源接口。
- `server-go/internal/provider/finnhub/finnhub.go`、`server-go/internal/provider/finnhub/finnhub_test.go`：新增 Finnhub WebSocket、Alpha Vantage 汇率和换算测试。
- `server-go/internal/provider/nowapi/nowapi.go`：新增 NowAPI 兼容数据源。
- `server-go/internal/worker/runner.go`：新增定时刷新、Redis 分布式锁、缓存写入和 K 线预聚合。
- `server-go/Dockerfile`、`server-go/docker-compose.go.yml`、`server-go/.dockerignore`：新增 Go 服务容器构建和 9988 并行验证部署文件。
- `docs/go-server-migration-v1.md`：新增 Go 服务端架构、验证、迁移和回滚说明。
- `progress.md`：追加本次 Go 服务端实现记录。
- 回滚方式：删除 `server-go/` 和 `docs/go-server-migration-v1.md`，并回退本段 `progress.md` 记录；如已提交，使用 `git revert <本次提交>`。

## 2026-06-16 - Task: 准备 Go 服务端生产接管配置

### What was done
- 新增 Go 服务端生产 Compose 文件，正式接管时 `gold-api-go` 使用 `9987:8080`，`gold-worker-go` 不暴露端口。
- 生产 Compose 不创建新 Redis，改为通过外部 Docker 网络连接现有 `gold-redis`，复用生产 Redis 数据和历史行情。
- 更新 Go 服务端迁移文档，补充正式切换、验证和回滚命令，并明确不要停止生产 Redis。

### Testing
- `cd server-go; go test ./...`：通过。
- `cd server-go; go build ./cmd/gold-api ./cmd/gold-worker`：通过。
- 本机无 Docker CLI，`docker compose -f docker-compose.prod.yml config` 将在服务器执行。

### Notes
- `server-go/docker-compose.prod.yml`：新增 Go API/Worker 生产接管配置，使用现有 `gold-redis` 和正式端口 `9987`。
- `docs/go-server-migration-v1.md`：补充生产切换、验证和回滚步骤。
- `progress.md`：追加本次生产接管配置记录。
- 回滚方式：删除 `server-go/docker-compose.prod.yml`，并恢复 `docs/go-server-migration-v1.md` 和 `progress.md` 到本轮修改前；如已提交，使用 `git revert <本次提交>`。

## 2026-06-16 - Task: 切换线上服务端到 Go API

### What was done
- 服务器 `/opt/gold-notifier/app` 拉取客户端主仓库 `main`，线上版本更新到 `b965feb`。
- 在服务器执行 Go 生产 Compose 校验与镜像构建，复用现有 `server_default` Docker 网络和 `gold-redis` 数据。
- 停止旧 Python API 容器 `gold-api`，启动 `gold-api-go` 接管公网 `9987` 端口，并启动 `gold-worker-go` 统一刷新行情缓存。
- 确认客户端继续通过原有 `/api/v1/gold/latest` 和 `/api/v1/gold/candles` 路径访问数据，不需要改客户端接口地址。

### Testing
- 服务器执行 `docker compose -f docker-compose.prod.yml config`：通过。
- 服务器执行 `docker compose -f docker-compose.prod.yml build`：通过，生成 `gold-api-go:latest` 镜像。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/health`：返回 `ok=true`，Redis 状态 `ok=true`，数据源为 `finnhub`。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/gold/latest?symbol=XAU`：返回现货黄金最新价，字段包含 `open`、`prevClose`、`high`、`low`、`marketStatus`。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/gold/candles?symbol=XAU&range=1h`：返回 K 线数据。
- 本机公网 `curl http://64.90.3.109:9987/api/v1/health`：返回 `ok=true`。
- 本机公网 `curl http://64.90.3.109:9987/api/v1/gold/latest?symbol=XAU`：返回 `code=0`。
- 本机公网 `curl http://64.90.3.109:9987/api/v1/gold/candles?symbol=XAU&range=1h`：返回 `code=0`，`count=61`。
- 服务器 `docker ps --filter name=gold`：运行 `gold-api-go`、`gold-worker-go`、`gold-redis`，旧 `gold-api` 不在运行列表。

### Notes
- `progress.md`：追加本次线上 Go 服务接管记录。
- 远端状态：`gold-api-go` 已接管 `0.0.0.0:9987->8080`，`gold-worker-go` 已开始刷新 Finnhub 行情并写入 Redis。
- 回滚方式：登录服务器后执行 `cd /opt/gold-notifier/app/server-go && GOLD_DOCKER_NETWORK=server_default GOLD_ENV_FILE=/opt/gold-notifier/server/.env docker compose -f docker-compose.prod.yml down && docker start gold-api`，再用 `curl http://127.0.0.1:9987/api/v1/health` 验证旧 Python API 恢复。

## 2026-06-17 - Task: 增强 Go 服务端行情刷新自恢复

### What was done
- 修复 Finnhub WebSocket 异常后容易卡在旧连接的问题：收到上游错误或等待完整报价快照超时时，主动关闭当前连接并重建。
- 为 Finnhub 流重连增加递增退避，连续失败时最高等待 30 秒，降低上游 429 风险。
- 将 `last_success` 默认保留期从 1 天提高到 7 天，并保证不会短于历史行情保留期，避免实时缓存过期后客户端直接拿到空数据。
- 补充 API 兜底和 Finnhub 重连退避的回归测试，并更新 Go 服务端迁移文档。

### Testing
- `cd server-go; go test ./...`：通过。
- `cd server-go; go build ./cmd/gold-api ./cmd/gold-worker`：通过。

### Notes
- `server-go/internal/provider/finnhub/finnhub.go`：新增连接跟踪、超时强制重连、上游错误重连和递增退避。
- `server-go/internal/cache/store.go`：新增 `last_success` 保留期下限保护。
- `server-go/internal/config/config.go`：将 `LAST_SUCCESS_TTL_SECONDS` 默认值调整为 604800 秒。
- `server-go/internal/config/config_test.go`：更新默认配置测试。
- `server-go/internal/httpapi/router_test.go`：新增实时缓存过期后返回 `last_success` 陈旧兜底数据的测试。
- `server-go/internal/provider/finnhub/finnhub_test.go`：新增 Finnhub 重连退避上限测试。
- `docs/go-server-migration-v1.md`：补充 API 陈旧数据兜底和 Worker 自恢复说明。
- `progress.md`：追加本次 Go 服务端自恢复优化记录。
- 回滚方式：恢复上述文件到本轮修改前；如已提交，执行 `git revert <本次提交>` 后重新部署 Go 服务。

## 2026-06-17 - Task: 部署 Go 服务端行情刷新自恢复优化

### What was done
- 通过本地 `git bundle` 将提交 `f7a9cc2` 同步到服务器 `/opt/gold-notifier/app`，服务器仓库从 `b965feb` 快进到 `f7a9cc2`。
- 在服务器重新构建 `gold-api-go` 和 `gold-worker-go` 镜像，并滚动重建两个 Go 容器。
- 线上继续复用现有 `gold-redis` 和 `9987` 公网端口，旧 Python API 未重新启用。

### Testing
- 服务器执行 `git bundle verify /tmp/goldnotifier-main-f7a9cc2.bundle`：通过。
- 服务器执行 `docker compose -f docker-compose.prod.yml config`：通过。
- 服务器执行 `docker compose -f docker-compose.prod.yml build`：通过。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/health`：返回 `ok=true`，`sourceStatus.ok=true`。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/gold/latest?symbol=XAU`：返回 `code=0`。
- 服务器本机 `curl http://127.0.0.1:9987/api/v1/gold/candles?symbol=XAU&range=1h`：返回 `code=0`，`count=18`。
- 本机公网 `curl http://64.90.3.109:9987/api/v1/health`：返回 `ok=true`。
- 本机公网 `curl http://64.90.3.109:9987/api/v1/gold/latest?symbol=XAU`：返回 `code=0`。

### Notes
- `progress.md`：追加本次线上部署记录。
- GitHub 推送状态：本地提交已完成，但当前本机访问 `github.com:443` 超时，`git push origin main` 暂未成功；服务器已通过 bundle 部署到 `f7a9cc2`。
- 回滚方式：服务器执行 `cd /opt/gold-notifier/app/server-go && GOLD_DOCKER_NETWORK=server_default GOLD_ENV_FILE=/opt/gold-notifier/server/.env docker compose -f docker-compose.prod.yml down && docker start gold-api` 可恢复旧 Python API；若只回滚本次 Go 代码，可在服务器仓库回退到上一个 Go 服务端提交后重新构建并 `docker compose -f docker-compose.prod.yml up -d`。
