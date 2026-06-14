package com.example.goldnotifier.data.model

/*
模块名: AppConfigDto
功能概述: 承载服务端下发的客户端刷新与通知配置。
对外接口: AppConfigDto
依赖关系: Retrofit Gson converter
输入输出: 输入 /api/v1/app/config JSON，输出客户端配置 DTO。
异常与错误: 缺失字段由调用方按本地默认值兜底。
维护说明: MVP 首屏不强依赖该接口，保留给后续服务端动态配置。
*/
data class AppConfigDto(
    val minRefreshInterval: Int?,
    val defaultRefreshInterval: Int?,
    val nonTradingRefreshInterval: Int?,
    val notificationEnabled: Boolean?,
    val latestVersionCode: Int?,
    val forceUpdate: Boolean?,
    val notice: String?,
)
