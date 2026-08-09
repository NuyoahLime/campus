import { defineConfig } from 'vite';
import type { ProxyOptions } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiProxy: ProxyOptions = {
  target: 'http://localhost:8080',
  changeOrigin: true,
  configure(proxy) {
    proxy.on('proxyReq', (proxyReq) => {
      proxyReq.removeHeader('origin');
    });
  }
};

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxy
    }
  }
});
