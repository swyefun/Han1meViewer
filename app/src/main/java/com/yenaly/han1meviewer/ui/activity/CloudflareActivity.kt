package com.yenaly.han1meviewer.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.yenaly.han1meviewer.Preferences.cloudFlareCookie
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.base.BaseActivity
import com.yenaly.han1meviewer.logic.network.GeckoCfBridge
import com.yenaly.han1meviewer.logic.network.GeckoCoreManager
import com.yenaly.han1meviewer.ui.screen.web.CloudflareScreen
import com.yenaly.han1meviewer.ui.theme.HanimeTheme
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.util.HAnalytics

/**
 * Cloudflare 验证页：使用内嵌 Gecko(Firefox) 内核渲染，不依赖系统 WebView。
 *
 * cf_clearance Cookie 由内嵌 WebExtension 通过 native messaging 上报（见 GeckoCfBridge），
 * 拿到后写入 Preferences.cloudFlareCookie 并回调放行被拦截的 OkHttp 请求。
 */
class CloudflareActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "request_url"
        var onFinished: (() -> Unit)? = null
        private const val TAG = "CloudflareActivity"
    }

    private val progressState = mutableIntStateOf(0)
    private val tipTextState = mutableStateOf("")

    @Volatile
    private var finished = false

    private var bridge: GeckoCfBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finishWithCallback()
            return
        }
        HAnalytics.logScreenView("CloudflareVerification", "CloudflareActivity")

        tipTextState.value = buildTip()

        // 进程级 GeckoRuntime + 本次会话的 Cookie 桥接
        val runtime = GeckoCoreManager.getRuntime(this)
        bridge = GeckoCfBridge(runtime) { cookie ->
            runOnUiThread {
                if (finished || isFinishing || isDestroyed) return@runOnUiThread
                Log.i(TAG, "Cloudflare 验证完成，写入 cf_clearance")
                cloudFlareCookie = CookieString(cookie)
                finishWithCallback()
            }
        }.also { it.beginSession() }

        setContent {
            HanimeTheme {
                CloudflareScreen(
                    runtime = runtime,
                    url = url,
                    userAgent = USER_AGENT,
                    progress = progressState.intValue,
                    tipText = tipTextState.value,
                    onClose = { finishWithCallback() },
                    onProgress = { progressState.intValue = it },
                )
            }
        }
    }

    /** 提示文案：展示 Gecko 内核版本，说明这是内嵌 Firefox 内核。 */
    private fun buildTip(): String {
        val version = GeckoCoreManager.getVersion(this)
        return getString(R.string.complete_cloudflare_verification_gecko, version)
    }

    /** 结束页面并确保等待中的 OkHttp 线程被释放，避免 CountDownLatch 永久阻塞。 */
    private fun finishWithCallback() {
        if (finished) return
        finished = true
        bridge?.endSession()
        onFinished?.invoke()
        onFinished = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 页面被系统销毁（如划掉最近任务）时也要释放等待线程
        bridge?.endSession()
        onFinished?.invoke()
        onFinished = null
    }
}
