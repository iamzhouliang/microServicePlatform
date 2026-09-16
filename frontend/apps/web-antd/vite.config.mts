import { defineConfig } from '@vben/vite-config';

// dev 代理目标地址（与 .env.development 的 VITE_PROXY_TARGET 对应）；
// 生产部署时 .env.production 配 VITE_GLOB_API_URL=/api，由 nginx 把 /api 反代到后端
const proxyTarget =
  process.env.VITE_PROXY_TARGET || 'http://localhost:15000';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      optimizeDeps: {
        exclude: ['@vue-office/docx', '@vue-office/excel', '@vue-office/pdf'],
      },
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            target: proxyTarget,
            ws: true,
          },
        },
      },
    },
  };
});
