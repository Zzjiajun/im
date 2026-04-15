import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import { networkInterfaces } from "node:os";

function printLanLoginUrls() {
  let printed = false;
  return {
    name: "print-lan-login-urls",
    configureServer(server: {
      httpServer?: {
        once: (event: string, cb: () => void) => void;
        address: () => string | { port?: number } | null;
      };
    }) {
      server.httpServer?.once("listening", () => {
        if (printed) return;
        printed = true;

        const addr = server.httpServer?.address();
        const actualPort = typeof addr === "object" && addr?.port ? addr.port : 5173;

        const nets = networkInterfaces();
        const urls = new Set<string>();

        for (const list of Object.values(nets)) {
          for (const item of list || []) {
            if (item.family !== "IPv4" || item.internal) continue;
            urls.add(`http://${item.address}:${actualPort}/login`);
          }
        }

        if (!urls.size) return;

        console.log("\n可在局域网设备打开以下前端地址：");
        for (const url of urls) {
          console.log(`  ${url}`);
        }
        console.log("");
      });
    },
  };
}

export default defineConfig({
  plugins: [vue(), printLanLoginUrls()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    host: "0.0.0.0",
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
