package com.yenaly.han1meviewer.ui.screen.web

import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private const val TAG = "GeckoCloudflareView"

/**
 * 用内嵌 GeckoView 渲染 Cloudflare 验证页。
 *
 * - [url] 变化时重新加载会话；
 * - [onProgress] 回传 0..100 进度；
 * - [onPageStop] 在页面加载停止时回调（用于 UI 判断）。
 *
 * Cookie（cf_clearance）不走这里：由 [com.yenaly.han1meviewer.logic.network.GeckoCfBridge]
 * 通过 WebExtension 独立上报，避免把 Cookie 逻辑耦合进 View。
 */
@Composable
fun GeckoCloudflareView(
    runtime: GeckoRuntime,
    url: String,
    userAgent: String,
    modifier: Modifier = Modifier,
    onProgress: (Int) -> Unit,
    onPageStop: (Boolean) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Log.i(TAG, "创建 GeckoView, url=$url")
            val geckoView = GeckoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }

            val session = GeckoSession().apply {
                settings.setUserAgentOverride(userAgent)
                setProgressDelegate(object : GeckoSession.ProgressDelegate {
                    override fun onPageStart(session: GeckoSession, url: String) {
                        onProgress(10)
                    }

                    override fun onPageStop(session: GeckoSession, success: Boolean) {
                        onProgress(100)
                        onPageStop(success)
                    }

                    override fun onProgressChange(session: GeckoSession, progress: Int) {
                        onProgress(progress)
                    }
                })
            }

            // 先 setSession 再 open，GeckoView 才能拿到 ISession 显示宿主
            geckoView.setSession(session)
            session.open(runtime)
            session.loadUri(url)
            geckoView
        },
        update = { view ->
            // url 变化（理论上不会，Activity 每次拿到固定 url）时重载
            view.session?.let { session ->
                if (session.settings.userAgentOverride != userAgent) {
                    session.settings.setUserAgentOverride(userAgent)
                }
            }
        },
        onRelease = { view ->
            Log.i(TAG, "释放 GeckoView")
            // 只关闭会话；View 随之销毁，无需 setSession(null)
            runCatching { view.session?.close() }
        },
    )
}
