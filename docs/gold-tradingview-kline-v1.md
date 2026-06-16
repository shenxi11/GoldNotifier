# GoldNotifier TradingView K 线 v1 落地说明

## 业务目标

首页趋势卡片在保留默认折线图的基础上，增加 TradingView 风格 K 线查看模式。K 线用于查看短线 OHLC 走势，不提供指标、成交量、画线、交易或全屏横屏能力。

## 已落地行为

- 趋势卡片新增 `折线 / K线` 切换，默认仍为折线。
- 折线图继续使用原 Compose Canvas 实现，作为默认展示和 WebView 不可用时的回退入口。
- K 线模式请求服务端 `/api/v1/gold/candles`，由服务端统一聚合 OHLC，客户端不再从 `/history` 自行分页聚合。
- 用户切换时间窗口时，折线重新请求 `/history`，K 线模式同步请求 `/candles`。
- 前台 3 秒刷新到新鲜最新价时，客户端只更新当前最后一根 candle，不每 3 秒全量重拉 K 线。

## 数据口径

- `5分钟` 对应服务端 `range=5m`，K 线粒度为 `15s`。
- `1小时` 对应服务端 `range=1h`，K 线粒度为 `1m`。
- `6小时` 对应服务端 `range=6h`，K 线粒度为 `5m`。
- `1天` 对应服务端 `range=1d`，K 线粒度为 `15m`。
- K 线只代表服务端已经采集到的新鲜行情点，不等同于交易所级完整 OHLC。

## Android 依赖

- 使用 `com.tradingview:lightweightcharts:4.0.0`。
- 该依赖是 TradingView Lightweight Charts 的 Android WebView wrapper，要求设备存在可用 Android System WebView 或 Chrome WebView Provider。
- 依赖的 Apache 2.0 许可要求在移动应用中保留 TradingView attribution，当前 K 线 footer 显示 `TradingView`。

## 验证

- 服务端 `.\.venv\Scripts\python.exe -m pytest`：验证 `/candles` API 契约和 OHLC 聚合规则。
- 客户端 `.\gradlew.bat testDebugUnitTest`：验证客户端 candles 数据链路、DTO 过滤和 UI 编译。
