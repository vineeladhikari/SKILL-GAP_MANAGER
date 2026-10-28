import { defineConfig } from "vite";

export default defineConfig({
  base: "./",
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
        secure: false
      }
    },
    watch: {
      usePolling: true,
      interval: 1000
    }
  }
});
