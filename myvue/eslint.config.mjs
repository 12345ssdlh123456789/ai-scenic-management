import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'

/**
 * 重点目标：捕获“引用了未定义的符号”这一类运行时才炸、build 不报错的问题。
 * 本项目历史上因这类问题出现过 4 次整页白屏。
 * 核心规则是 no-undef —— 它能同时覆盖 script 与模板表达式的作用域。
 */
export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      parser: vueParser,
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        // 浏览器全局
        window: 'readonly', document: 'readonly', console: 'readonly',
        localStorage: 'readonly', sessionStorage: 'readonly',
        setTimeout: 'readonly', clearTimeout: 'readonly',
        setInterval: 'readonly', clearInterval: 'readonly',
        requestAnimationFrame: 'readonly', cancelAnimationFrame: 'readonly',
        fetch: 'readonly', URL: 'readonly', URLSearchParams: 'readonly',
        FormData: 'readonly', Blob: 'readonly', FileReader: 'readonly',
        Intl: 'readonly', MutationObserver: 'readonly', IntersectionObserver: 'readonly',
        ResizeObserver: 'readonly', CustomEvent: 'readonly', Event: 'readonly',
        Image: 'readonly', HTMLElement: 'readonly', Node: 'readonly',
        getComputedStyle: 'readonly', alert: 'readonly', confirm: 'readonly',
        globalThis: 'readonly',
      },
    },
    rules: {
      // ★ 核心：未定义标识符。这条能抓到 CheckCircle / Location / CloseCircle 之类
      'no-undef': 'error',

      // 声明但未使用（清理死导入；允许大写开头，可能用于模板）
      'no-unused-vars': ['warn', {
        args: 'none',
        varsIgnorePattern: '^_',
        caughtErrors: 'none',
      }],

      // 模板/组件相关
      // Element Plus 通过 app.use(ElementPlus) 全局注册，ESLint 看不到，
      // 因此把 el-* 与常见图标组件列为已知全局，避免几百条噪声淹没真正的信号。
      'vue/no-undef-components': ['error', {
        ignorePatterns: [
          '^el-[a-z-]+$',
          '^El[A-Z]',
          '^router-(view|link)$',
          '^transition$', '^keep-alive$', '^component$', '^slot$', '^template$',
        ],
      }],
      'vue/no-unused-components': 'warn',
      'vue/no-unused-vars': 'warn',
      'vue/require-v-for-key': 'error',
      'vue/no-parsing-error': 'error',
      'vue/valid-v-slot': 'error',
      'vue/no-side-effects-in-computed-properties': 'error',
      'vue/no-duplicate-attributes': 'error',
      'vue/no-mutating-props': 'error',
      'vue/require-explicit-emits': 'off',
      'vue/no-v-html': 'off',

      // 允许单文件多词组件名等噪声
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/first-attribute-linebreak': 'off',
    },
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'scripts/**'],
  },
]
