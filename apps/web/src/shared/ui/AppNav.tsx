"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/shared/lib/auth-context";

const links = [
  { href: "/app", label: "今天" },
  { href: "/app/todos", label: "待办" },
  { href: "/app/punch", label: "打卡" },
  { href: "/app/timeline", label: "时间轴" },
  { href: "/app/memory", label: "记忆" },
  { href: "/app/reminders", label: "提醒" },
];

export function AppNav() {
  const pathname = usePathname();
  const { user, logout, ready } = useAuth();

  return (
    <nav className="app-nav" aria-label="主导航">
      <div className="app-nav-links">
        {links.map((link) => {
          const active =
            link.href === "/app"
              ? pathname === "/app"
              : pathname.startsWith(link.href);
          return (
            <Link
              key={link.href}
              href={link.href}
              className={active ? "nav-link active" : "nav-link"}
            >
              {link.label}
            </Link>
          );
        })}
      </div>
      <div className="app-nav-auth">
        {!ready ? null : user ? (
          <>
            <span className="nav-user">{user.displayName}</span>
            <button type="button" className="text-btn" onClick={logout}>
              退出
            </button>
          </>
        ) : (
          <>
            <Link href="/login" className="nav-link">
              登录
            </Link>
            <Link href="/register" className="nav-link">
              注册
            </Link>
          </>
        )}
      </div>
    </nav>
  );
}
