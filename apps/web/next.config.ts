import type { NextConfig } from "next";

// Server-side proxy target (Docker Compose service name or local API).
const apiProxyTarget = process.env.API_PROXY_TARGET || "http://127.0.0.1:3001";

const nextConfig: NextConfig = {
  // Required for apps/web/Dockerfile (standalone server.js output).
  output: "standalone",
  async rewrites() {
    return [
      {
        // Browser → same origin /api/* → API container/process
        source: "/api/:path*",
        destination: `${apiProxyTarget.replace(/\/$/, "")}/:path*`,
      },
    ];
  },
};

export default nextConfig;
