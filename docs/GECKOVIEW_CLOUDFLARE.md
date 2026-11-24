# Cloudflare 验证页：内嵌 GeckoView 方案

## 为什么换成 GeckoView

Cloudflare 验证页原先使用系统 WebView，低版本内核会循环验证。 originally 也试过腾讯 X5，
但 X5 闭源、无构建文件、静态内核需从微信提取二进制（版本不可控）。最终采用
**Mozilla GeckoView**：开源 MPL 2.0、有完整构建文件、Maven 直接引 AAR、版本随依赖升级、
不需要 root。

- 依赖：`org.mozilla.geckoview:geckoview-arm64-v8a:160.0.20261116130455`（稳定版，2026-11）
- minSdk 26（项目 minSdk 27，兼容 Android 8.1），仓库在 `settings.gradle.kts` 的 Mozilla maven。
- 仅 arm64-v8a：与项目 Release 的 abi split 一致；x86 模拟器调试需临时把依赖换回通用版
  `org.mozilla.geckoview:geckoview`。

## 关键实现

GeckoView 与系统 WebView 的两处能力差异，决定了实现方式：

1. **无 `evaluateJavascript`**：GeckoView 不提供 JS 求值。本项目验证完成判定不依赖 JS，
   改为只认 `cf_clearance` Cookie 出现。
2. **无公开 Cookie 读取 API**：用内嵌 WebExtension（`assets/gecko_cf_bridge/`）的
   `browser.cookies.getAll()`（可读 HttpOnly 的 cf_clearance），通过 native messaging 上报。

数据流：

```
GeckoView 验证页
  └─ 内嵌扩展 background.js 每 1s probe → Java(GeckoCfBridge) 应答 watch/session
        └─ watch=true 时 browser.cookies.getAll() 发现 cf_clearance
              └─ sendNativeMessage → Java 写 Preferences.cloudFlareCookie → OkHttp 放行
```

关键约束（来自 Sinytra 项目真机验证）：GeckoView 没有 Java → 扩展的单向发送 API
（`Port.postMessage` 回环到 Java 自己），因此扩展必须自启动轮询、主动上报，
`sendNativeMessage` 的 Promise = Java `MessageDelegate` 返回的 `GeckoResult`（双向握手）。
每次 Activity 打开生成新的 `sessionId`，扩展据此复位会话状态，避免旧 Cookie 误报。

相关文件：
- `logic/network/GeckoCoreManager.kt`：进程级 GeckoRuntime 单例。
- `logic/network/GeckoCfBridge.kt`：扩展安装、MessageDelegate、probe/cookie 协议。
- `ui/screen/web/GeckoCloudflareView.kt`：GeckoView + GeckoSession + 进度回调。
- `ui/activity/CloudflareActivity.kt`：串起内核、桥接、页面与 OkHttp 回调。
- `assets/gecko_cf_bridge/{manifest.json,background.js}`：内嵌扩展。

## 需要真机验证 / 已知风险

本机无 Android SDK/Java 21，未能编译；所有 Gecko API 签名已用 `javap` 对照 AAR 核对。
以下项必须在真机/CI 验证：

1. **扩展能否访问 cookies**：built-in 扩展的 `cookies` + host 权限在 GeckoView 下是否完整生效。
   若 `browser.cookies.getAll()` 被拒，则本方案拿不到 cf_clearance，需回退方案（见下）。
2. **Cloudflare 是否认可 Gecko 指纹**：Gecko 的 TLS/JA3、HTTP/2、JS 环境与 Chrome 不同，
   Cloudflare 风控**可能不认**这个内核，导致验证不通过或循环。这是换内核方案的固有风险。
   UA override 伪装成 Chrome 也不能改变底层指纹。
3. **网络栈差异**：GeckoView 走 Firefox 自己的网络栈，不使用 App 的 OkHttp DoH / 内置 hosts /
   代理设置。若 App 依赖这些访问站点，验证页可能打不开（README_TECH 中已记录
   "Cloudflare 验证页不支持自定义 hosts"的类似问题）。
4. **扩展是进程级单例**：`ensureBuiltIn` 只安装一次，background.js 不会因新 Activity 重跑；
   会话切换靠 `sessionId` 握手复位，需真机确认多轮验证稳定。

## 回退方案

若上述风险 1/2 命中、验证页无法工作，最稳妥的回退是**保留系统 WebView 验证页**
（原实现），改用手动 Cookie 导入（`ManualInputCookiesActivity`），或确保设备上的
Android System WebView 为高版本。不要把本方案直接发布给用户。

## 构建验证

```powershell
./gradlew :app:compileDebugKotlin
```
