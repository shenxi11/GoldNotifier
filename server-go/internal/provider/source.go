// 模块名: provider
// 功能概述: 定义 Worker 调用的上游行情源接口。
// 对外接口: Source
// 依赖关系: context、model
// 输入输出: 输入行情 symbol，输出统一 GoldPrice。
// 异常与错误: 上游失败由实现返回 error，Worker 负责写入 source_status。
// 维护说明: API 进程不得依赖本包，避免客户端请求触发上游访问。
package provider

import (
	"context"

	"goldnotifier/server-go/internal/model"
)

type Source interface {
	FetchLatest(ctx context.Context, symbol string) (model.GoldPrice, error)
}
