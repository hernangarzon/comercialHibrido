import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// El build se publica dentro del jar de Spring Boot (src/main/resources/static).
// En desarrollo (npm run dev) se hace proxy al backend en :8084.
const backend = 'http://localhost:8084'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // sockjs-client espera la variable global de Node.
  define: { global: 'globalThis' },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    // Dos páginas: la landing pública en "/" y el panel en "/app/".
    rollupOptions: {
      input: {
        landing: 'index.html',
        app: 'app/index.html',
        privacidad: 'privacidad/index.html',
        terminos: 'terminos/index.html',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/webhook': backend,
      '/ws': { target: backend, ws: true },
    },
  },
})
