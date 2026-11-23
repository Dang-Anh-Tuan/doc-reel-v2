/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import { docreelDevServer } from "./dev/docreelDevServer.ts";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));

export default defineConfig(({ command }) => ({
  // Dev chạy ở "/", bản build được Spring phục vụ ở /admin/
  base: command === "build" ? "/admin/" : "/",
  plugins: [react(), docreelDevServer({ repoRoot })],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: 5173, // bận thì Vite tự chuyển sang 5174, 5175...
    // Trang quản trị (bản 2) gọi REST của Spring Boot
    proxy: {
      "/api": "http://localhost:8080",
      "/actuator": "http://localhost:8080",
    },
  },
  build: {
    // Spring Boot phục vụ static/admin/** → http://localhost:8080/admin/
    outDir: fileURLToPath(new URL("../src/main/resources/static/admin", import.meta.url)),
    emptyOutDir: true,
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
  },
}));
