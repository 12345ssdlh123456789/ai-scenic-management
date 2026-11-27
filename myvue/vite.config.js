import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    strictPort: true,
    hmr: {
      protocol: 'ws',
      host: '127.0.0.1',
      port: 5173
    },
    // 代理只用于开发期把 /v1 开头的 API 转给后端。
    //
    // 注意必须带 /v1 前缀：Vite 的 proxy key 是「前缀匹配」，若只写 '/project'，
    // 它会连前端路由 /projects（复数）一起劫持；同理 '/user' 会吃掉 /users，
    // '/vip' 会吃掉 /vipApply，'/recharge' 会吃掉 /rechargeHistory。
    // 被劫持后浏览器拿到的是后端的 401 JSON 而不是 Vue 应用，
    // 表现为页面错乱、按钮点不动。
    //
    // 实际 API 调用全部形如 /v1/xxx（见 src/api/*.js），所以按 /v1 前缀代理即可。
    proxy: {
      '/v1': { target: 'http://localhost:9090', changeOrigin: true }
    }
  }
})
