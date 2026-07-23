"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/shared/lib/auth-context";

export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      await login(email.trim(), password);
      router.push("/app");
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="auth-form reveal" onSubmit={onSubmit}>
      <h1>登录 Today</h1>
      <p className="app-lead">登录后，今天会被长期记住在你的账户里。</p>
      <label>
        邮箱
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoComplete="email"
        />
      </label>
      <label>
        密码
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={6}
          autoComplete="current-password"
        />
      </label>
      {error ? <p className="form-error">{error}</p> : null}
      <button type="submit" className="btn-primary" disabled={saving}>
        {saving ? "登录中…" : "登录"}
      </button>
      <p className="muted">
        还没有账号？ <Link href="/register">注册</Link>
      </p>
    </form>
  );
}
