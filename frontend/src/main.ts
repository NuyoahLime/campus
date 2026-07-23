import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

import App from './App.vue';
import router from './router';

const app = createApp(App);

const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus);

// Initialize auth store session restore before mounting
import { useAuthStore } from '@/stores/auth';
const auth = useAuthStore();
auth.restoreSession();

app.mount('#app');
