"use client";

import { AppNav } from "@/components/AppNav";
import { TodayProvider } from "@/lib/today-context";
import { useAuth } from "@/lib/auth-context";
import Link from "next/link";
import type { ReactNode } from "react";

export function AppShell({ children }: { children: ReactNode }) {
  const { user } = useAuth();

  return (
    <TodayProvider key={user?.id ?? "guest"}>
      <div className="app-shell">
        <header className="app-header">
          <Link href="/" className="brand-mark">
            Today
          </Link>
          <span className="tag">
            {user ? `你好，${user.displayName}` : "一直记得你"}
          </span>
        </header>
        <AppNav />
        <main className="app-main">{children}</main>
      </div>
    </TodayProvider>
  );
}
