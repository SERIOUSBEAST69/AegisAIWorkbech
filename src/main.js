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

async function bootstrap() {
  const userStore = useUserStore(pinia);
  await userStore.bootstrapSession();
  app.mount('#app');
}

bootstrap();
