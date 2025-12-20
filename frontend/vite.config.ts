import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            '/ws': {
                target: 'http://localhost:8081',
                changeOrigin: true,
                ws: true
            }
        }
    },
    build: {
        // Performance optimizations for production builds
        target: 'es2020',
        minify: 'terser',
        terserOptions: {
            compress: {
                drop_console: true,
                drop_debugger: true,
            },
        },
        rollupOptions: {
            output: {
                manualChunks: {
                    // Vendor chunk for stable dependencies
                    vendor: ['react', 'react-dom', '@mui/material'],
                    // Visualization chunk for Three.js and D3
                    visualization: ['three', 'd3'],
                    // Analysis chunk for analysis tools
                    analysis: [],
                },
            },
        },
        // Optimize chunk size
        chunkSizeWarningLimit: 1000,
        // Enable source maps for production debugging
        sourcemap: true,
    },
    optimizeDeps: {
        // Pre-bundle heavy dependencies
        include: [
            'react',
            'react-dom',
            '@mui/material',
            'three',
            'd3',
        ],
        // Exclude problematic dependencies from pre-bundling
        exclude: ['monaco-editor'],
    },

    define: {
        'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development')
    }
})