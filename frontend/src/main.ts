import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useAuthStore } from './stores/auth';
import './styles/tokens.css';
import './styles/base.css';
import './styles/forms.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);

const auth = useAuthStore(pinia);

auth.restoreSession()
  .catch(() => {
    auth.currentUser = null;
  })
  .finally(() => {
    app.use(router);
    app.mount('#app');
  });
