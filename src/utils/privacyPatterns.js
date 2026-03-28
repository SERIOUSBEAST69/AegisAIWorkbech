/**
 * privacyPatterns.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Shared client-side privacy detection patterns.
 *
 * Used by:
 *  - src/views/Home.vue  (inline AI workbench quick-check)
 *  - src/components/AIPrivacyShield.vue  (floating privacy shield component)
 *
 * These patterns intentionally mirror the backend regex in
 * AiModelAccessGuardService.java so client-side checks are consistent.
 * The backend remains the authoritative gate; these patterns provide
 * immediate UX feedback before the request is sent.
 */

/** 中国大陆 18 位身份证号 */
export const ID_CARD_RE = /[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/;

/** 手机号（1[3-9]xxxxxxxxx） */
export const PHONE_RE = /(?<!\d)1[3-9]\d{9}(?!\d)/;

/** 电子邮箱 */
export const EMAIL_RE = /[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/;

/** 银行卡号（12–19 位纯数字） */
export const BANK_RE = /(?<!\d)\d{12,19}(?!\d)/;

/** 隐私相关关键词 */
export const KEYWORDS_RE = /(身份证|银行卡|信用卡|手机号|住址|真实姓名|密码|验证码)/;

/** Debounce delay (ms) for real-time input checking */
export const PRIVACY_CHECK_DEBOUNCE_MS = 500;

/**
 * Run all patterns against text and return an array of detected field types.
 *
 * @param {string} text - Input text to scan
 * @returns {string[]} Array of detected privacy field type labels (Chinese)
 */
export function quickPrivacyCheck(text) {
  if (!text) return [];
  const found = [];
  if (ID_CARD_RE.test(text)) found.push('身份证号');
  if (PHONE_RE.test(text))   found.push('手机号');
  if (EMAIL_RE.test(text))   found.push('电子邮箱');
  if (BANK_RE.test(text))    found.push('银行卡号');
  if (KEYWORDS_RE.test(text)) found.push('隐私关键词');
  return found;
}
