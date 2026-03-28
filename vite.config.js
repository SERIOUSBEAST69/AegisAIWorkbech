import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('echarts')) return 'echarts';
          if (id.includes('element-plus')) return 'element-plus';
          if (id.includes('axios')) return 'http-client';
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
    target: 'http://aegisai-backend:8080',
    changeOrigin: true,
    rewrite: path => path.replace(/^\/api/, '')
  }
}
  }
});