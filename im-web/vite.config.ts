import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8075",
        changeOrigin: true,
      },
      // STOMP 与 HTTP 同一后端端口，前端用 ws://localhost:5173/ws-chat 即可（勿再写死 8080）
      "/ws-chat": {
        target: "http://127.0.0.1:8075",
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
