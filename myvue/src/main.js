import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 中文语言包：不引入时 Element Plus 默认英文，
// 分页会显示 "Total 1 / 10/page / Go to"，与全站中文界面不一致。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 全局样式
import './styles/design-system.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus, { locale: zhCn })
app.use(router)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

// 在 Pinia 安装后才导入 request，确保拦截器能正确使用 useUserStore
import request from './utils/request'
app.config.globalProperties.$http = request

app.mount('#app')
