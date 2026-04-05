'use strict';

const STATE = {
  config: null,
  siteRule: null,
  activeInput: null,
  debounceTimer: null,
  lastIgnoredHash: null,
  lastIgnoredAt: 0,
  fragments: [],
  mutationSignals: [],
  mutationObserver: null,
  lastChainId: null,
};

const FRAGMENT_WINDOW_MS = 30000;
const MUTATION_WINDOW_MS = 20000;

const INJECTION_PATTERNS = [
  /ignore\s+(all|previous|prior)\s+instructions?/i,
  /system\s+prompt/i,
  /developer\s+mode/i,
  /bypass\s+(policy|guard|safety|filter)/i,
  /do\s+not\s+follow\s+safety/i,
];

function nowMs() {
  return Date.now();
}

function trimWindow(list, maxAgeMs) {
  const ts = nowMs();
  return list.filter((item) => ts - item.ts <= maxAgeMs);
}

function recordFragment(text) {
  const normalized = String(text || '').trim();
  if (!normalized) return;
  STATE.fragments.push({ text: normalized.slice(-180), ts: nowMs() });
  STATE.fragments = trimWindow(STATE.fragments, FRAGMENT_WINDOW_MS).slice(-12);
}

function getFragmentSignals(currentText) {
  const fragments = trimWindow(STATE.fragments, FRAGMENT_WINDOW_MS);
  const joined = fragments.map((item) => item.text).join(' ');
  const signals = [];
  if (fragments.length >= 4 && INJECTION_PATTERNS.some((pattern) => pattern.test(joined))) {
    signals.push('fragmented_prompt_chain');
  }
  if (joined.length > 300 && /[A-Za-z0-9+/=]{180,}/.test(joined)) {
    signals.push('fragmented_encoded_payload');
  }
  if (String(currentText || '').length > 600 && fragments.length >= 3) {
    signals.push('staged_long_composition');
  }
  return signals;
}

function addMutationSignal(signal) {
  STATE.mutationSignals.push({ signal, ts: nowMs() });
  STATE.mutationSignals = trimWindow(STATE.mutationSignals, MUTATION_WINDOW_MS).slice(-20);
}

function getMutationSignals() {
  return trimWindow(STATE.mutationSignals, MUTATION_WINDOW_MS).map((item) => item.signal);
}

function inspectMutation(nodes) {
  for (const node of nodes || []) {
    if (!(node instanceof HTMLElement)) continue;
    const text = String(node.textContent || '').slice(0, 1200);
    const hidden = node.hidden || node.getAttribute('aria-hidden') === 'true' || node.style?.display === 'none';
    if (hidden && text && INJECTION_PATTERNS.some((pattern) => pattern.test(text))) {
      addMutationSignal('hidden_prompt_injection_dom');
      continue;
    }
    const role = String(node.getAttribute('role') || '').toLowerCase();
    if ((role.includes('tooltip') || role.includes('note')) && text && INJECTION_PATTERNS.some((pattern) => pattern.test(text))) {
      addMutationSignal('ui_overlay_prompt_injection');
    }
  }
}

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
          riskCategory: detection.riskCategory,
          confidence: detection.confidence,
          reasons: detection.reasons || [],
          chainId: detection?.chain?.chainId,
          chainLevel: detection?.chain?.level,
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
        reasons: ['user_override'],
        chainId: STATE.lastChainId || null,
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
      recordFragment(text);
      if (!text) {
        hideBanner();
        return;
      }

      const detection = await sendRuntimeMessage({
        type: 'detect-content',
        text,
        context: {
          fragmentSignals: getFragmentSignals(text),
          mutationSignals: getMutationSignals(),
        },
      });
      STATE.lastChainId = detection?.chain?.chainId || null;
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
  const observer = new MutationObserver((mutations) => {
    bindAllInputs();
    for (const mutation of mutations || []) {
      inspectMutation(mutation.addedNodes);
    }
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
  STATE.mutationObserver = observer;
}

init().catch(() => {});
