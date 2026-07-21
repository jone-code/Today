import { AppNav } from "@/components/AppNav";
import { TodayProvider } from "@/lib/today-context";
import Link from "next/link";

export default function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <TodayProvider>
      <div className="app-shell">
        <header className="app-header">
          <Link href="/" className="brand-mark">
            Today
          </Link>
          <span className="tag">一直记得你</span>
        </header>
        <AppNav />
        <main className="app-main">{children}</main>
      </div>
    </TodayProvider>
  );
}
