import type { NextConfig } from "next";
import path from "path";

const nextConfig: NextConfig = {
  // Pin the workspace root to this app's own directory.
  //
  // web-app/ and directorate-app/ are two standalone Next projects in one
  // repository. Turbopack infers the root by walking up for a lockfile, so a
  // stray package-lock.json at the repo root makes it treat the whole repo as
  // the workspace and resolve modules from the wrong node_modules.
  turbopack: {
    root: path.join(__dirname),
  },
};

export default nextConfig;
