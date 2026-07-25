import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Required for apps/web/Dockerfile (standalone server.js output).
  output: "standalone",
};

export default nextConfig;
