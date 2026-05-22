import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// Library build for the "prov" plugin.
//
// Contract: the main Ligoj Vue host loads this plugin via a dynamic import of
//   /webjars/prov/vue/index.js
// — so the output lives under the Java module's classpath resources, where
// Spring Boot's webjars servlet will serve it at runtime.
//
// Shared deps (vue, pinia, vue-router, vuetify) are kept EXTERNAL: the plugin
// must use the host's module instances or reactivity and plugin registries
// break across SFC boundaries. The host resolves these bare specifiers via an
// import map declared in its HTML entry points.

export default defineConfig({
  plugins: [vue()],

  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.js'),
      formats: ['es'],
      fileName: () => 'index.js',
    },
    outDir: resolve(
      __dirname,
      '../src/main/resources/META-INF/resources/webjars/prov/vue',
    ),
    emptyOutDir: true,
    rollupOptions: {
      external: ['vue', 'vue-router', 'pinia', 'vuetify', '@ligoj/host'],
      output: {
        assetFileNames: 'index.[ext]',
      },
    },
  },

  // Standalone dev server — tests the plugin in isolation against a running
  // Ligoj backend on :8080. `npm run dev` then open http://localhost:5175/.
  server: {
    port: 5175,
    proxy: {
      '/rest': { target: 'http://localhost:8080', changeOrigin: true },
      '/webjars': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
