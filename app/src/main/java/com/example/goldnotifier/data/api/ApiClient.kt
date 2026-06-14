package com.example.goldnotifier.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/*
模块名: ApiClient
功能概述: 创建 Retrofit 客户端并配置网络超时、日志与 JSON 解析。
对外接口: ApiClient.create
依赖关系: OkHttp、Retrofit、GsonConverterFactory
输入输出: 输入服务端 baseUrl，输出 GoldApi 实例。
异常与错误: baseUrl 非法时 Retrofit 会抛出配置异常。
维护说明: Debug 日志只记录基础请求信息，避免输出敏感密钥。
*/
object ApiClient {
    fun create(baseUrl: String): GoldApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoldApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"
}
