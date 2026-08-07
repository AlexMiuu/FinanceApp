import path from 'node:path'
import { defineConfig } from 'vite'

// Bundles the DOM-free logic modules so `scripts/logic-check.mjs` can exercise the
// real shipped code under Node. See that file for why this exists.
export default defineConfig({
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  build: {
    outDir: '.logic-check',
    lib: {
      entry: path.resolve(__dirname, 'scripts/logic-check-entry.ts'),
      formats: ['es'],
      fileName: 'bundle',
    },
    minify: false,
  },
})
