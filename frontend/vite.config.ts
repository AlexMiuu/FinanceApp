import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Vendor code changes on upgrades, app code changes on every deploy, so a
        // returning browser can reuse these across releases instead of re-fetching
        // React because a label was reworded. `realtime` additionally never blocks
        // the first paint — NotificationsBell imports it dynamically.
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (/node_modules[\\/](react|react-dom|scheduler)[\\/]/.test(id)) return 'react-vendor'
          if (id.includes('@stomp')) return 'realtime'
          if (/node_modules[\\/](radix-ui|@radix-ui|lucide-react)[\\/]/.test(id)) return 'ui-vendor'
        },
      },
    },
  },
  server: {
    proxy: {
      // Dev-mode: forward API calls to the gateway (same path nginx proxies in docker)
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
