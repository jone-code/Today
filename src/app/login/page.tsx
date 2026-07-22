import { LoginForm } from "@/components/LoginForm";
import Link from "next/link";

export default function LoginPage() {
  return (
    <div className="auth-shell">
      <header className="landing-top">
        <Link href="/" className="brand-mark">
          Today
        </Link>
        <Link href="/register" className="ghost-link">
          注册
        </Link>
      </header>
      <LoginForm />
    </div>
  );
}
