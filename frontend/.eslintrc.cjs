/* eslint-env node */
module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
    extraFileExtensions: ['.vue'],
  },
  plugins: ['@typescript-eslint'],
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
  ],
  rules: {
    // Templates legitimately use PascalCase components and kebab-case props.
    'vue/multi-word-component-names': 'off',
    'vue/max-attributes-per-line': 'off',
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-self-closing': 'off',
    'vue/attributes-order': 'warn',

    // Money and identifiers flow between the API and the UI as strings/numbers;
    // an accidental `any` would break that contract silently.
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/no-unused-vars': [
      'warn',
      { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' },
    ],
    '@typescript-eslint/consistent-type-imports': ['warn', { prefer: 'type-imports' }],

    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'error',
    eqeqeq: ['error', 'smart'],
    'prefer-const': 'error',
    'object-shorthand': 'warn',
  },
  overrides: [
    {
      files: ['**/*.config.js', '**/*.config.ts', 'eslint.config.js'],
      rules: { 'no-console': 'off' },
    },
    {
      files: ['src/mock/**/*.ts'],
      rules: {
        // The mock layer is intentionally data-heavy and shared-mutable.
        '@typescript-eslint/no-explicit-any': 'off',
      },
    },
  ],
  ignorePatterns: ['dist', 'node_modules', 'coverage', '*.d.ts'],
}