package com.example.goldnotifier.data.api

import com.example.goldnotifier.data.model.ApiResponse
import com.example.goldnotifier.data.model.AppConfigDto
import com.example.goldnotifier.data.model.GoldCandlesResponseDto
import com.example.goldnotifier.data.model.GoldHistoryResponseDto
import com.example.goldnotifier.data.model.GoldPriceDto
import retrofit2.http.GET
import retrofit2.http.Query

/*
模块名: GoldApi
功能概述: 声明客户端访问自建行情服务端的 Retrofit 接口。
对外接口: getLatestGold、getGoldHistory、getGoldCandles、getAppConfig
依赖关系: Retrofit、ApiResponse、GoldPriceDto、GoldHistoryResponseDto、GoldCandlesResponseDto、AppConfigDto
输入输出: 输入服务端 baseUrl 与查询参数，输出统一响应对象。
异常与错误: 网络、HTTP 和解析异常由 Retrofit 抛出，Repository 统一处理。
维护说明: 客户端只调用自建服务端，不直接访问第三方行情源。
*/
interface GoldApi {
    @GET("/api/v1/gold/latest")
    suspend fun getLatestGold(
        @Query("symbol") symbol: String = "XAU",
    ): ApiResponse<GoldPriceDto>

    @GET("/api/v1/gold/history")
    suspend fun getGoldHistory(
        @Query("symbol") symbol: String = "XAU",
        @Query("date") date: String,
        @Query("startMillis") startMillis: Long,
        @Query("endMillis") endMillis: Long,
        @Query("limit") limit: Int,
    ): ApiResponse<GoldHistoryResponseDto>

    @GET("/api/v1/gold/candles")
    suspend fun getGoldCandles(
        @Query("symbol") symbol: String = "XAU",
        @Query("range") range: String = "5m",
    ): ApiResponse<GoldCandlesResponseDto>

    @GET("/api/v1/app/config")
    suspend fun getAppConfig(): ApiResponse<AppConfigDto>
}
