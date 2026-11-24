package com.yenaly.han1meviewer.logic.network

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Mozilla Gecko(Firefox) 内核的进程级管理。
 *
 * Cloudflare 验证页使用内嵌的 GeckoView（开源 MPL 2.0、版本可随依赖升级），
 * 不依赖系统 WebView，也不需要 root。GeckoRuntime 按 GeckoView 要求，
 * 每个宿主进程只有一个实例。
 */
object GeckoCoreManager {

    @Volatile
    private var runtime: GeckoRuntime? = null

    /** 获取进程唯一的 GeckoRuntime（首次调用时创建）。 */
    @Synchronized
    fun getRuntime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
            .build()
        return GeckoRuntime.create(context.applicationContext, settings).also { runtime = it }
    }

    /** 当前 Gecko 内核版本串（取自 runtime 的 userAgent，形如 "Gecko/xxx"）。 */
    fun getVersion(context: Context): String = runCatching {
        org.mozilla.geckoview.GeckoSession.getDefaultUserAgent()
    }.getOrDefault("unknown")
}
