package com.yenaly.han1meviewer.logic.network

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.UUID

/**
 * Cloudflare 验证页与内嵌 WebExtension 之间的桥接。
 *
 * GeckoView 无公开 Cookie 读取 API，故用扩展 background.js 的
 * browser.cookies.getAll() 轮询 cf_clearance，再通过 native messaging 上报。
 * 协议详见 assets/gecko_cf_bridge/background.js 头注释。
 *
 * 由于扩展是进程级单例、一次安装终身运行，这里用 [sessionId] 区分每次验证会话：
 * 每次 [beginSession] 生成新 id，扩展据此复位它的暂停状态。
 */
class GeckoCfBridge(
    private val runtime: GeckoRuntime,
    /** 收到 cf_clearance 时的回调，参数为实现层拼好的 cookie 字符串。 */
    private val onCookie: (String) -> Unit,
) {
    companion object {
        private const val TAG = "GeckoCfBridge"
        private const val NATIVE_APP = "hanime_cf"
        private const val EXTENSION_ID = "hanime-cf-bridge@yenaly.local"
        private const val EXTENSION_LOCATION = "resource://android/assets/gecko_cf_bridge/"
    }

    /** 本次验证会话 id；null 表示当前无活跃会话。 */
    @Volatile
    private var sessionId: String? = null

    /** 本次会话是否已收到 Cookie。 */
    @Volatile
    private var cookieReceived = false

    init {
        installExtension()
    }

    private fun installExtension() {
        runCatching {
            runtime.webExtensionController
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(
                    { ext -> bindDelegate(ext) },
                    { e -> Log.w(TAG, "ensureBuiltIn 失败", e) },
                )
        }.onFailure { Log.w(TAG, "ensureBuiltIn 抛异常", it) }
    }

    private fun bindDelegate(ext: WebExtension?) {
        if (ext == null) {
            Log.w(TAG, "ensureBuiltIn 返回空扩展，跳过 delegate 绑定")
            return
        }
        runCatching {
            // extension 级绑定：扩展 background 的 sendNativeMessage 会到这里
            ext.setMessageDelegate(object : WebExtension.MessageDelegate {
                override fun onMessage(
                    nativeApp: String,
                    message: Any,
                    sender: WebExtension.MessageSender,
                ): GeckoResult<Any>? {
                    if (nativeApp != NATIVE_APP) return null
                    val reply = handleMessage(message)
                    return GeckoResult.fromValue(reply)
                }
            }, NATIVE_APP)
            Log.i(TAG, "MessageDelegate 已绑定: $EXTENSION_ID")
        }.onFailure { Log.w(TAG, "setMessageDelegate 失败", it) }
    }

    /** 开始一次新的验证会话。 */
    fun beginSession() {
        sessionId = UUID.randomUUID().toString()
        cookieReceived = false
        Log.i(TAG, "beginSession: $sessionId")
    }

    /** 结束会话：之后 probe 一律回 watch=false，扩展停止上报。 */
    fun endSession() {
        sessionId = null
    }

    private fun toJson(message: Any): JSONObject? = when (message) {
        is JSONObject -> message
        is String -> runCatching { JSONObject(message) }.getOrNull()
        else -> null
    }

    private fun handleMessage(message: Any): Any {
        val obj = toJson(message) ?: return JSONObject()
        return when (obj.optString("type")) {
            "probe" -> {
                val active = sessionId != null && !cookieReceived
                JSONObject()
                    .put("watch", active)
                    .put("session", sessionId ?: "")
            }

            "cf_clearance" -> {
                val msgSession = obj.optString("session")
                // 只接受当前会话、且尚未收到的上报，避免旧会话 Cookie 污染
                if (sessionId != null && msgSession == sessionId && !cookieReceived) {
                    val cookie = obj.optString("cookie")
                    if (cookie.isNotBlank()) {
                        cookieReceived = true
                        Log.i(TAG, "收到 cf_clearance，长度=${cookie.length}")
                        onCookie(cookie)
                    }
                }
                JSONObject().put("accepted", cookieReceived)
            }

            else -> JSONObject()
        }
    }
}
