"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/shared/lib/auth-context";

export function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      await register(email.trim(), password, displayName.trim());
      router.push("/app");
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="auth-form reveal" onSubmit={onSubmit}>
      <h1>创建 Today</h1>
      <p className="app-lead">留下你的名字。从今天开始，被认真记住。</p>
      <label>
        昵称
        <input
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          maxLength={64}
          autoComplete="nickname"
        />
      </label>
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
        密码（至少 6 位）
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={6}
          autoComplete="new-password"
        />
      </label>
      {error ? <p className="form-error">{error}</p> : null}
      <button type="submit" className="btn-primary" disabled={saving}>
        {saving ? "创建中…" : "注册并进入"}
      </button>
      <p className="muted">
        已有账号？ <Link href="/login">登录</Link>
      </p>
    </form>
  );
}
