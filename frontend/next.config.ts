import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Lean production image (frontend/Dockerfile) — copies .next/standalone
  // instead of the full node_modules tree.
  output: "standalone",
};

export default nextConfig;
