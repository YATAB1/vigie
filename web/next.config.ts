import type { NextConfig } from "next";

// The browser only ever talks to this Next.js origin. Calls to /api/* are
// forwarded to the Spring Boot API, so there is no CORS to configure and
// session cookies stay same-origin.
const apiUrl = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
};

export default nextConfig;
