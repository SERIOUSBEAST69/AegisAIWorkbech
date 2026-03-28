import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';
import './assets/theme.css';
import { useUserStore } from './store/user';

// 全局错误处理：忽略浏览器 ResizeObserver 循环告警，其他错误仍上报
window.onerror = function(msg, url, line, col, error) {
  const text = String(msg || '');
  if (text.includes('ResizeObserver loop completed')) return;
  console.error('全局错误:', msg, url, line, col, error);
};

const app = createApp(App);
const pinia = createPinia();
app.use(router);
app.use(pinia);
app.use(ElementPlus);

function reportWebVital(metric) {
  const payload = {
    name: metric.name,
    value: Number(metric.value?.toFixed?.(2) ?? metric.value),
    rating: metric.rating,
    id: metric.id,
    navigationType: metric.navigationType || 'unknown',
    path: window.location.pathname,
    ts: Date.now(),
  };

  const endpoint = '/api/ops-metrics/web-vitals';
  const body = JSON.stringify(payload);
  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' });
    navigator.sendBeacon(endpoint, blob);
    return;
  }

  fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  }).catch(() => {
    // Best-effort telemetry only.
  });
}

function setupWebVitals() {
  if (typeof window === 'undefined' || typeof PerformanceObserver === 'undefined') {
    return;
  }

  const reported = new Set();
  const emit = (name, value, rating = 'unknown', id = '') => {
    const key = `${name}:${id || 'na'}`;
    if (reported.has(key)) return;
    reported.add(key);
    reportWebVital({
      name,
      value,
      rating,
      id,
      navigationType: performance?.getEntriesByType?.('navigation')?.[0]?.type || 'navigate',
    });
  };

  try {
    const poPaint = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.name === 'first-contentful-paint') {
          emit('FCP', entry.startTime, entry.startTime < 1800 ? 'good' : entry.startTime < 3000 ? 'needs-improvement' : 'poor', String(entry.startTime));
        }
      }
    });
    poPaint.observe({ type: 'paint', buffered: true });
  } catch {}

  try {
    const poLcp = new PerformanceObserver((list) => {
      const entries = list.getEntries();
      const last = entries[entries.length - 1];
      if (last) {
        emit('LCP', last.startTime, last.startTime < 2500 ? 'good' : last.startTime < 4000 ? 'needs-improvement' : 'poor', String(last.startTime));
      }
    });
    poLcp.observe({ type: 'largest-contentful-paint', buffered: true });
  } catch {}

  try {
    let clsValue = 0;
    const poCls = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (!entry.hadRecentInput) {
          clsValue += entry.value;
        }
      }
      emit('CLS', clsValue, clsValue < 0.1 ? 'good' : clsValue < 0.25 ? 'needs-improvement' : 'poor', 'cls');
    });
    poCls.observe({ type: 'layout-shift', buffered: true });
  } catch {}

  try {
    const nav = performance?.getEntriesByType?.('navigation')?.[0];
    if (nav) {
      emit('TTFB', nav.responseStart, nav.responseStart < 800 ? 'good' : nav.responseStart < 1800 ? 'needs-improvement' : 'poor', 'ttfb');
    }
  } catch {}
}

async function bootstrap() {
  const userStore = useUserStore(pinia);
  await userStore.bootstrapSession();
  setupWebVitals();
  app.mount('#app');
}

bootstrap();
