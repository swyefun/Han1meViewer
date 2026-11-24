/*
 * hanime-cf-bridge — GeckoView 内嵌扩展（background 脚本）
 *
 * GeckoView 没有公开的 Cookie 读取 API，因此用 WebExtension 的
 * browser.cookies.getAll()（可读到 HttpOnly 的 cf_clearance）轮询，
 * 一旦出现 cf_clearance 就通过 native messaging 上报给 App。
 *
 * 关键约束（Sinytra 真机验证结论）：GeckoView 没有 Java -> 扩展 的单向发送 API
 * （Port.postMessage 回环到 Java 自己）。因此扩展不能等 Java 下发指令，
 * 必须自启动、主动 sendNativeMessage；sendNativeMessage 的 Promise 返回值
 * 就是 Java MessageDelegate 应答的 GeckoResult —— 用它做双向握手。
 *
 * 协议（native app = "hanime_cf"）：
 *   扩展 -> Java  {type:"probe"}                       每轮询问是否需要监视
 *   Java -> 扩展  {watch:true|false, session:<string>}  应答；session 变化代表新验证会话
 *   扩展 -> Java  {type:"cf_clearance", cookie, domain, session}  发现目标 Cookie（watch=true 时）
 *   Java -> 扩展  {accepted:true}                      确认收到（扩展随后暂停该会话）
 *
 * session 由 Java 每次打开验证页时生成，扩展据此在会话切换时复位 paused。
 */

"use strict";

const NATIVE_APP = "hanime_cf";
const POLL_INTERVAL_MS = 1000;

let busy = false;
let paused = false;      // 当前会话已上报并获 Java 确认
let currentSession = null; // 当前会话 id，变化则复位 paused

/** 把 cookie 数组拼成 "name=value; ..."，与 CookieString 解析格式兼容。 */
function serialize(cookies) {
  return cookies
    .filter(c => c && c.name)
    .map(c => c.name + "=" + (c.value || ""))
    .join("; ");
}

/** 向 Java 发一条消息并等待它的 GeckoResult 应答。 */
function ask(message) {
  return browser.runtime.sendNativeMessage(NATIVE_APP, message)
    .then(reply => reply || {})
    .catch(() => ({}));
}

async function tick() {
  if (busy) return;
  busy = true;
  try {
    // 先问 Java 当前是否处于活跃验证会话
    const handshake = await ask({ type: "probe" });
    // 会话切换：复位暂停状态
    if (typeof handshake.session === "string" && handshake.session !== currentSession) {
      currentSession = handshake.session;
      paused = false;
    }
    if (handshake.watch === true) {
      try {
        const cookies = await browser.cookies.getAll({});
        const cf = cookies.find(c => c && c.name === "cf_clearance");
        if (cf && !paused) {
          const domain = cf.domain || "";
          const sameDomain = cookies.filter(c => c && c.domain &&
            (c.domain === domain || c.domain.endsWith(domain) || domain.endsWith(c.domain)));
          const reply = await ask({
            type: "cf_clearance",
            cookie: serialize(sameDomain && sameDomain.length ? sameDomain : cookies),
            domain: domain,
            session: currentSession,
          });
          if (reply.accepted === true) {
            paused = true; // 本会话已上报，等 Java 端清理/结束后再继续
          }
        }
      } catch (e) {
        // cookies 权限/时机问题忽略
      }
    }
  } finally {
    busy = false;
  }
}

// 扩展一加载就自启动轮询（无需 Java 指令）
setInterval(tick, POLL_INTERVAL_MS);
tick();
