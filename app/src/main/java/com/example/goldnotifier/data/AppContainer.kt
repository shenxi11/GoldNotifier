package com.example.goldnotifier.data

import android.content.Context
import com.example.goldnotifier.BuildConfig
import com.example.goldnotifier.data.api.ApiClient
import com.example.goldnotifier.data.local.UserSettingsDataStore
import com.example.goldnotifier.data.repository.GoldRepository

/*
模块名: AppContainer
功能概述: 组装客户端 MVP 所需的数据层依赖，保持单模块工程的依赖入口清晰。
对外接口: AppContainer.goldRepository、AppContainer.userSettingsDataStore
依赖关系: ApiClient、UserSettingsDataStore、GoldRepository
输入输出: 输入应用 Context 与 BuildConfig 配置，输出 Repository 和本地存储对象。
异常与错误: baseUrl 无效会在 Retrofit 初始化阶段暴露，便于尽早发现配置问题。
维护说明: 后续如接入 Hilt，可将此类迁移为 DI Module。
*/
class AppContainer(context: Context) {
    val userSettingsDataStore = UserSettingsDataStore(context.applicationContext)
    private val goldApi = ApiClient.create(BuildConfig.GOLD_API_BASE_URL)

    val goldRepository = GoldRepository(
        api = goldApi,
        localStore = userSettingsDataStore,
    )
}
