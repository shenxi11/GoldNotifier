package com.example.goldnotifier

import android.app.Application
import com.example.goldnotifier.data.AppContainer

/*
模块名: GoldNotifierApplication
功能概述: 提供应用级依赖入口，集中创建客户端数据与服务对象。
对外接口: GoldNotifierApplication.appContainer
依赖关系: Android Application、AppContainer
输入输出: 输入应用 Context，输出可复用的客户端依赖集合。
异常与错误: 初始化阶段只创建轻量对象，不执行网络请求。
维护说明: 未引入 DI 框架前，所有跨层单例依赖从这里取得。
*/
class GoldNotifierApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
