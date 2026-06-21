import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

/**
 * Flat-config ESLint setup for the plugin-prov Vue bundle. Mirrors the
 * host's eslint.config.js so plugin code follows the same rules.
 */
export default [
  {
    ignores: [
      'node_modules/**',
      // Built bundle is emitted into the maven module's resources dir.
      '../src/main/resources/META-INF/resources/webjars/prov/vue/**',
    ],
  },
  js.configs.recommended,
  // Match the host's eslint.config.js — essential-only Vue rules.
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,mjs,cjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      // Vuetify data-table uses dotted slot names (`#item.foo`).
      'vue/valid-v-slot': ['error', { allowModifiers: true }],
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
]
