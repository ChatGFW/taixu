import path from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
const bridge = path.resolve(__dirname, 'web-bridge.ts');
export default defineConfig({
  root: 'src', plugins: [react()], base: '/',
  build: { outDir: '../dist-web', emptyOutDir: true },
  resolve: { alias: [
    { find: /^@tauri-apps\/api\/(core|event|app|path|webview|window)$/, replacement: bridge },
    { find: /^@tauri-apps\/plugin-(dialog|process|log|updater)$/, replacement: bridge },
    { find: '@', replacement: path.resolve(__dirname, 'src') },
  ]},
});
