// 模块名: model
// 功能概述: 定义 Go 服务端与 Android 客户端共享的 JSON 契约模型。
// 对外接口: ApiResponse、GoldPrice、GoldHistoryResponse、GoldCandlesResponse、AppConfig
// 依赖关系: 无
// 输入输出: 输入业务层数据，输出与当前 Python 服务端兼容的 JSON 字段。
// 异常与错误: 模型层不做复杂校验，非法数据由 service/provider/cache 层处理。
// 维护说明: 字段名必须保持 Android DTO 兼容，尤其是 open、prevClose、timestampMillis。
package model

type ApiResponse[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    *T     `json:"data"`
}

func Success[T any](data T) ApiResponse[T] {
	return ApiResponse[T]{Code: 0, Message: "success", Data: &data}
}

func Error(code int, message string) ApiResponse[any] {
	return ApiResponse[any]{Code: code, Message: message, Data: nil}
}

type GoldPrice struct {
	Name          string  `json:"name"`
	Symbol        string  `json:"symbol"`
	Price         float64 `json:"price"`
	Change        float64 `json:"change"`
	ChangePercent float64 `json:"changePercent"`
	Unit          string  `json:"unit"`
	Open          float64 `json:"open"`
	PrevClose     float64 `json:"prevClose"`
	High          float64 `json:"high"`
	Low           float64 `json:"low"`
	UpdateTime    string  `json:"updateTime"`
	ServerTime    string  `json:"serverTime"`
	Source        string  `json:"source"`
	MarketStatus  string  `json:"marketStatus"`
	IsStale       bool    `json:"isStale"`
}

type GoldHistoryPoint struct {
	TimestampMillis int64   `json:"timestampMillis"`
	Price           float64 `json:"price"`
	UpdateTime      string  `json:"updateTime"`
	ServerTime      string  `json:"serverTime"`
	Source          string  `json:"source"`
}

type GoldHistoryResponse struct {
	Symbol   string             `json:"symbol"`
	Date     string             `json:"date"`
	Timezone string             `json:"timezone"`
	Count    int                `json:"count"`
	Points   []GoldHistoryPoint `json:"points"`
}

type GoldDailySummary struct {
	Symbol               string  `json:"symbol"`
	Date                 string  `json:"date"`
	Open                 float64 `json:"open"`
	High                 float64 `json:"high"`
	Low                  float64 `json:"low"`
	Close                float64 `json:"close"`
	OpenTimestampMillis  int64   `json:"openTimestampMillis"`
	CloseTimestampMillis int64   `json:"closeTimestampMillis"`
}

type GoldCandleBar struct {
	TimestampMillis int64   `json:"timestampMillis"`
	Open            float64 `json:"open"`
	High            float64 `json:"high"`
	Low             float64 `json:"low"`
	Close           float64 `json:"close"`
}

type GoldCandlesResponse struct {
	Symbol     string          `json:"symbol"`
	Range      string          `json:"range"`
	Resolution string          `json:"resolution"`
	Timezone   string          `json:"timezone"`
	Count      int             `json:"count"`
	Bars       []GoldCandleBar `json:"bars"`
}

type AppConfig struct {
	MinRefreshInterval        int    `json:"minRefreshInterval"`
	DefaultRefreshInterval    int    `json:"defaultRefreshInterval"`
	NonTradingRefreshInterval int    `json:"nonTradingRefreshInterval"`
	NotificationEnabled       bool   `json:"notificationEnabled"`
	LatestVersionCode         int    `json:"latestVersionCode"`
	ForceUpdate               bool   `json:"forceUpdate"`
	Notice                    string `json:"notice"`
}
