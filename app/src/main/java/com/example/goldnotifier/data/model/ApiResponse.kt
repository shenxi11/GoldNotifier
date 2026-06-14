package com.example.goldnotifier.data.model

/*
模块名: ApiResponse
功能概述: 定义服务端统一响应 envelope，避免 UI 直接依赖接口包装细节。
对外接口: ApiResponse
依赖关系: Retrofit Gson converter
输入输出: 输入服务端 JSON，输出可映射的数据响应对象。
异常与错误: data 允许为空，由 Repository 统一转换为业务错误。
维护说明: code=0 视为成功，其余错误码保留 message 供 UI 兜底提示。
*/
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?,
)
