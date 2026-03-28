import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiProxyTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8080';

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('echarts')) return 'charts-echarts';
          if (id.includes('zrender')) return 'charts-zrender';
          if (id.includes('element-plus')) return 'ui-element-plus';
          if (id.includes('@element-plus/icons-vue')) return 'ui-icons';
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'framework-vue';
          if (id.includes('axios')) return 'http-client';
          if (id.includes('gsap')) return 'motion-gsap';
          return 'vendor';
        }
      }
    }
  },
  server: {
    port: 5173,
    host: "0.0.0.0",
    open: false,
    proxy: {
  '/api': {
    target: apiProxyTarget,
    changeOrigin: true
  }
}
  }
});