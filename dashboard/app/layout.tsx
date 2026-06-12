import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PhantomGuard — Family DNS Protection",
  description:
    "Real-time encrypted DNS parental control dashboard: live activity, category filtering and per-domain overrides.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
