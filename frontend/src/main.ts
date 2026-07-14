import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'
import { authState } from './auth/useAuth'
import { createStocketRouter } from './router'
import { registerServiceWorker } from './pwa/registerServiceWorker'

const router = createStocketRouter(authState)

createApp(App).use(ElementPlus).use(router).mount('#app')

registerServiceWorker((update) => {
  window.dispatchEvent(new CustomEvent('stocket:update-available', { detail: update }))
})
