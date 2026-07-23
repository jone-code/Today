import type { Metadata } from "next";
import { Fraunces, Noto_Sans_SC } from "next/font/google";
import { Providers } from "@/shared/ui/Providers";
import "./globals.css";

const display = Fraunces({
  variable: "--font-display",
  subsets: ["latin"],
  axes: ["SOFT", "WONK", "opsz"],
});

const body = Noto_Sans_SC({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "Today — AI 记住你的每一天",
  description:
    "一款基于 AI 长期记忆的每日记录与陪伴产品。AI 不只是和你聊天，而是一直记得你。",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" className={`${display.variable} ${body.variable} h-full`}>
      <body className="site-shell antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
