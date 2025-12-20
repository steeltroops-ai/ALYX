import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts']
    },
    resolve: {
        alias: {
            'monaco-editor': 'monaco-editor/esm/vs/editor/editor.api.js'
        }
    },
    optimizeDeps: {
        exclude: ['monaco-editor']
    }
})