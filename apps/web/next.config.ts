import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Required for apps/web/Dockerfile (standalone server.js output).
  output: "standalone",
  // API proxy is implemented as app/api/[...path]/route.ts (runtime API_PROXY_TARGET).
};

export default nextConfig;
