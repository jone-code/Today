import type { Metadata } from "next";
import { Providers } from "@/shared/ui/Providers";
import "./globals.css";

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
    <html lang="zh-CN" className="h-full">
      <body className="site-shell antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
