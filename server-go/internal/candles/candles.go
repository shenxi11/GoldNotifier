// 模块名: candles
// 功能概述: 按当前服务端历史点聚合 TradingView K 线窗口。
// 对外接口: Resolution、Resolutions、Aggregate
// 依赖关系: model
// 输入输出: 输入历史行情点和窗口配置，输出 OHLC K 线列表。
// 异常与错误: 非法价格或窗口外点位会被忽略，不中断请求。
// 维护说明: 聚合口径必须与 Python 服务端保持一致，open 为桶内第一条，close 为桶内最后一条。
package candles

import "goldnotifier/server-go/internal/model"

type Resolution struct {
	WindowMillis int64
	BucketMillis int64
	Label        string
}

var Resolutions = map[string]Resolution{
	"5m": {WindowMillis: 5 * 60_000, BucketMillis: 15_000, Label: "15s"},
	"1h": {WindowMillis: 60 * 60_000, BucketMillis: 60_000, Label: "1m"},
	"6h": {WindowMillis: 6 * 60 * 60_000, BucketMillis: 5 * 60_000, Label: "5m"},
	"1d": {WindowMillis: 24 * 60 * 60_000, BucketMillis: 15 * 60_000, Label: "15m"},
}

func Aggregate(points []model.GoldHistoryPoint, startMillis int64, endMillis int64, bucketMillis int64) []model.GoldCandleBar {
	barsByBucket := make(map[int64]model.GoldCandleBar)
	keys := make([]int64, 0)
	for _, point := range points {
		if point.TimestampMillis < startMillis || point.TimestampMillis > endMillis || point.Price <= 0 {
			continue
		}
		bucketStart := point.TimestampMillis - point.TimestampMillis%bucketMillis
		current, exists := barsByBucket[bucketStart]
		if !exists {
			barsByBucket[bucketStart] = model.GoldCandleBar{
				TimestampMillis: bucketStart,
				Open:            point.Price,
				High:            point.Price,
				Low:             point.Price,
				Close:           point.Price,
			}
			keys = append(keys, bucketStart)
			continue
		}
		if point.Price > current.High {
			current.High = point.Price
		}
		if point.Price < current.Low {
			current.Low = point.Price
		}
		current.Close = point.Price
		barsByBucket[bucketStart] = current
	}
	sortInt64s(keys)
	bars := make([]model.GoldCandleBar, 0, len(keys))
	for _, key := range keys {
		bars = append(bars, barsByBucket[key])
	}
	return bars
}

func sortInt64s(values []int64) {
	for i := 1; i < len(values); i++ {
		current := values[i]
		j := i - 1
		for j >= 0 && values[j] > current {
			values[j+1] = values[j]
			j--
		}
		values[j+1] = current
	}
}
