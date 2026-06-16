# GoldNotifier 趋势图时间尺度 v1.2 落地说明

## 业务目标

首页趋势图支持切换近 5 分钟、近 1 小时、近 6 小时、近 1 天四个时间尺度。短窗口继续跟随 3 秒刷新追加实时点，长窗口通过服务端历史行情接口补齐 App 未运行期间的数据。

## 已落地行为

- 趋势卡片新增时间尺度选择控件，支持 `5 分钟`、`1 小时`、`6 小时`、`1 天`。
- `HomeViewModel` 维护当前选中尺度，切换后请求对应时间窗口内的服务端历史行情。
- `GoldRepository.fetchTrendHistory()` 调用 `/api/v1/gold/history`，按 `Asia/Shanghai` 日期拆分查询窗口。
- 单页最多请求 `10000` 个历史点；如果窗口内数据超过单页上限，客户端会继续用更早的 `endMillis` 分页补齐。
- 刷新到新鲜最新行情后，客户端会把最新点合并进当前趋势点列表，避免长窗口等待下一次历史重载。
- 图表绘制前使用 `TrendPointSampler` 抽样，最多绘制 `720` 个点，并保留首尾点，避免近一天高密度数据造成 Canvas 绘制压力。
- 趋势图左侧显示独立价格刻度，每条横向网格线对应一个实际价格，最高价作为顶部刻度，最低价作为底部刻度，中间刻度按当前点位区间等距动态计算。

## 数据流

1. 用户在趋势卡片选择时间尺度。
2. `HomeScreen` 将选择事件传给 `HomeViewModel.selectTrendTimeRange()`。
3. `HomeViewModel` 设置加载态，并调用 `GoldRepository.fetchTrendHistory()`。
4. `GoldRepository` 按时间窗口请求服务端历史点，过滤非法点并按时间排序。
5. `HomeViewModel` 合并服务端历史点与本地实时缓冲点，再按当前尺度裁剪。
6. `TrendPointSampler` 对长窗口点列抽样，`GoldRealtimeTrendChart` 只负责绘制传入点。

## 当前边界

- 趋势图仍是价格折线，不是 K 线图，不展示成交量。
- 近一天窗口依赖服务端历史保留策略；如果服务端没有对应时段采集数据，客户端只展示可查询到的点。
- 趋势快照仍只用于短时间恢复本地实时缓冲，不替代服务端历史存储。
- 价格刻度只显示数值，单位继续由趋势卡片右上角展示，避免小屏每条刻度重复单位造成拥挤。

## 验证

- `.\gradlew.bat testDebugUnitTest`：通过。
- `GoldRepositoryTest` 覆盖跨日期历史查询、非法历史点过滤和结果排序。
- `TrendPointBufferTest` 覆盖同一缓冲区按较短窗口生成快照。
- `TrendPointSamplerTest` 覆盖首尾点保留、抽样数量上限和非法上限处理。
- `GoldRealtimeTrendChartTest` 覆盖动态价格刻度的最高/最低边界、等距跨度、平盘点位和非法输入。
