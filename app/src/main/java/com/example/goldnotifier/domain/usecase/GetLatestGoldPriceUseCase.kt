package com.example.goldnotifier.domain.usecase

import com.example.goldnotifier.data.repository.GoldRefreshResult
import com.example.goldnotifier.data.repository.GoldRepository

/*
模块名: GetLatestGoldPriceUseCase
功能概述: 封装主动刷新最新金价的业务入口，便于后续复用和测试。
对外接口: invoke
依赖关系: GoldRepository
输入输出: 输入 symbol，输出 GoldRefreshResult。
异常与错误: 不向上抛出网络异常，由 Repository 返回缓存或错误消息。
维护说明: 当前逻辑较薄，保留是为了匹配客户端开发文档中的分层结构。
*/
class GetLatestGoldPriceUseCase(
    private val repository: GoldRepository,
) {
    suspend operator fun invoke(symbol: String = "XAU"): GoldRefreshResult =
        repository.refreshGoldPrice(symbol)
}
