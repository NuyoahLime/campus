import pluginVue from 'eslint-plugin-vue';
import tsEslint from '@vue/eslint-config-typescript';

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  ...pluginVue.configs['flat/essential'],
  ...tsEslint(),
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
];
