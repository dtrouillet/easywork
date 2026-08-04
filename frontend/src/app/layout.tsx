import type { Metadata } from "next";
import Script from "next/script";
import { Fraunces, Inter, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/layout/providers";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

const fraunces = Fraunces({
  variable: "--font-fraunces",
  subsets: ["latin"],
  axes: ["opsz"],
});

const ibmPlexMono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "easywork",
  description: "Enterprise Document Management System",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="en"
      className={`${inter.variable} ${fraunces.variable} ${ibmPlexMono.variable} h-full`}
    >
      <body className="h-full font-[family-name:var(--font-inter)] antialiased">
        {/* Runtime-injected config (see frontend/Dockerfile's docker-entrypoint.sh) —
            beforeInteractive so window.__ENV__ is set before client.ts resolves
            the API base URL. Absent in local dev; falls back to
            NEXT_PUBLIC_API_URL there. next/script hoists this into <head>
            regardless of where it's placed. */}
        <Script src="/env.js" strategy="beforeInteractive" />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
