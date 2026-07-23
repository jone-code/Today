import { RegisterForm } from "@/components/RegisterForm";
import Link from "next/link";

export default function RegisterPage() {
  return (
    <div className="auth-shell">
      <header className="landing-top">
        <Link href="/" className="brand-mark">
          Today
        </Link>
        <Link href="/login" className="ghost-link">
          登录
        </Link>
      </header>
      <RegisterForm />
    </div>
  );
}
