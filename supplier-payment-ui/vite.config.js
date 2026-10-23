
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const createProxy = (prefix, target) => ({
  target,
  changeOrigin: true,
  rewrite: (path) =>
    path.replace(
      new RegExp(`^${prefix}`),
      ""
    )
});

const proxyConfig = {
  "/security-api": createProxy(
    "/security-api",
    "http://localhost:8081"
  ),

  "/supplier-api": createProxy(
    "/supplier-api",
    "http://localhost:8082"
  ),

  "/payment-api": createProxy(
    "/payment-api",
    "http://localhost:8083"
  ),

  "/configuration-api": createProxy(
    "/configuration-api",
    "http://localhost:8086"
  ),

  "/risk-api": createProxy(
    "/risk-api",
    "http://localhost:8087"
  ),

  "/approval-api": createProxy(
    "/approval-api",
    "http://localhost:8089"
  ),

  "/audit-api": createProxy(
    "/audit-api",
    "http://localhost:8088"
  ),

  "/processing-api": createProxy(
    "/processing-api",
    "http://localhost:8085"
  )
};

export default defineConfig({
  plugins: [react()],

  server: {
    port: 4173,
    allowedHosts: true,
    proxy: proxyConfig
  },

  preview: {
    port: 4173,
    allowedHosts: true,
    proxy: proxyConfig
  },

  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: "./src/test/setup.js",
    css: true,
    include: [
      "src/**/*.{test,spec}.{js,jsx,ts,tsx}"
    ],
    exclude: [
      "e2e/**",
      "node_modules/**",
      "dist/**"
    ]
  }
});
