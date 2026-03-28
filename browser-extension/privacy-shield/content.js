'use strict';

const STATE = {
  config: null,
  siteRule: null,
  activeInput: null,
  debounceTimer: null,
  lastIgnoredHash: null,
  lastIgnoredAt: 0,
};

function hashText(text) {
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash) + text.charCodeAt(i);
    hash |= 0;
  }
  return String(hash);
}

function sendRuntimeMessage(payload) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(payload, (response) => {
      resolve(response || { ok: false });
    });
  });
}

function getInputText(el) {
  if (!el) return '';
  if (el instanceof HTMLTextAreaElement || el instanceof HTMLInputElement) {
    return el.value || '';
  }
  if (el.isContentEditable) {
    return el.textContent || '';
  }
  return '';
}

function setInputText(el, value) {
  if (!el) return;
  if (el instanceof HTMLTextAreaElement || el instanceof HTMLInputElement) {
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return;
  }
  if (el.isContentEditable) {
    el.textContent = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }
}

function ensureBanner() {
  let banner = document.getElementById('aegis-privacy-banner');
  if (banner) return banner;

  banner = document.createElement('div');
  banner.id = 'aegis-privacy-banner';
  banner.innerHTML = `
    <div class="aegis-privacy-inner">
      <span class="aegis-privacy-title">检测到敏感信息，是否脱敏后发送？</span>
      <span class="aegis-privacy-types" id="aegis-privacy-types"></span>
      <div class="aegis-privacy-actions">
        <button id="aegis-privacy-desense" type="button">一键脱敏</button>
        <button id="aegis-privacy-ignore" type="button">忽略警告</button>
      </div>
    </div>
  `;
  document.body.appendChild(banner);

  banner.querySelector('#aegis-privacy-desense').addEventListener('click', async () => {
    const input = STATE.activeInput;
    if (!input) return;
    const text = getInputText(input);
    const detection = await sendRuntimeMessage({ type: 'detect-content', text });
    if (detection?.blocked) {
      setInputText(input, detection.maskedText || text);
      hideBanner();
      await sendRuntimeMessage({
        type: 'report-event',
        payload: {
          eventType: 'EXTENSION_SENSITIVE',
          content: text,
          action: 'desensitize',
          matchedTypes: detection.detected || [],
        },
      });
    }
  });

  banner.querySelector('#aegis-privacy-ignore').addEventListener('click', async () => {
    const input = STATE.activeInput;
    if (!input) return;
    const text = getInputText(input);
    STATE.lastIgnoredHash = hashText(text);
    STATE.lastIgnoredAt = Date.now();
    hideBanner();
    await sendRuntimeMessage({
      type: 'report-event',
      payload: {
        eventType: 'EXTENSION_SENSITIVE',
        content: text,
        action: 'ignore',
      },
    });
  });

  return banner;
}

function showBanner(detected) {
  const banner = ensureBanner();
  const typesNode = banner.querySelector('#aegis-privacy-types');
  typesNode.textContent = Array.isArray(detected) && detected.length > 0
    ? `命中类型：${detected.join(', ')}`
    : '';
  banner.classList.add('visible');
}

function hideBanner() {
  const banner = document.getElementById('aegis-privacy-banner');
  if (banner) {
    banner.classList.remove('visible');
  }
}

function findSiteRule(config) {
  const host = window.location.hostname;
  const rules = Array.isArray(config?.siteSelectors) ? config.siteSelectors : [];
  return rules.find((rule) => (rule.hosts || []).some((h) => host.includes(h)));
}

function bindInput(el) {
  if (!el || el.dataset.aegisBound === '1') return;
  el.dataset.aegisBound = '1';

  el.addEventListener('focus', () => {
    STATE.activeInput = el;
  });

  el.addEventListener('input', () => {
    STATE.activeInput = el;
    if (STATE.debounceTimer) {
      clearTimeout(STATE.debounceTimer);
    }
    STATE.debounceTimer = setTimeout(async () => {
      const text = getInputText(el).trim();
      if (!text) {
        hideBanner();
        return;
      }

      const detection = await sendRuntimeMessage({ type: 'detect-content', text });
      const currentHash = hashText(text);
      if (STATE.lastIgnoredHash === currentHash && Date.now() - STATE.lastIgnoredAt < 45 * 1000) {
        return;
      }

      if (detection?.blocked) {
        showBanner(detection.detected || []);
      } else {
        hideBanner();
      }
    }, 450);
  });
}

function bindAllInputs() {
  if (!STATE.siteRule) return;
  const selectors = Array.isArray(STATE.siteRule.inputSelectors) ? STATE.siteRule.inputSelectors : [];
  selectors.forEach((selector) => {
    document.querySelectorAll(selector).forEach((el) => bindInput(el));
  });
}

async function init() {
  const cfgResp = await sendRuntimeMessage({ type: 'get-config' });
  STATE.config = cfgResp?.config || {};
  STATE.siteRule = findSiteRule(STATE.config);
  if (!STATE.siteRule) return;

  bindAllInputs();
  const observer = new MutationObserver(() => bindAllInputs());
  observer.observe(document.documentElement, { childList: true, subtree: true });
}

init().catch(() => {});
