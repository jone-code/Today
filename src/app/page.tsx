import Link from "next/link";

export default function HomePage() {
  return (
    <div className="landing">
      <header className="landing-top">
        <span className="brand-mark">Today</span>
        <Link href="/app" className="ghost-link">
          打开今天
        </Link>
      </header>

      <section className="hero" aria-label="产品主视觉">
        <div className="hero-atmosphere" aria-hidden />
        <div className="hero-copy">
          <p className="hero-brand">Today</p>
          <h1 className="hero-title">AI 记住你的每一天。</h1>
          <p className="hero-support">
            每天只用 30 秒留下今天。整理、总结与长期记忆，交给一直记得你的 AI。
          </p>
          <div className="cta-row">
            <Link href="/app" className="btn-primary">
              留下今天
            </Link>
            <a href="#why" className="btn-secondary">
              为什么存在
            </a>
          </div>
        </div>
      </section>

      <section id="why" className="section">
        <h2>不是日记，也不是聊天。</h2>
        <p className="section-lead">
          传统日记坚持困难，传统 AI 每次都会失忆。Today
          想成为那个长期记住你、并每天陪伴你的存在。
        </p>
        <div className="belief-grid">
          <div className="belief">
            <h3>不是</h3>
            <p>写日记</p>
          </div>
          <div className="belief">
            <h3>而是</h3>
            <p>留下今天</p>
          </div>
          <div className="belief">
            <h3>不是</h3>
            <p>AI 聊天</p>
          </div>
          <div className="belief">
            <h3>而是</h3>
            <p>AI 记住你</p>
          </div>
        </div>
      </section>

      <section className="slogan-band">
        <p>AI 不只是和你聊天，而是一直记得你。</p>
      </section>

      <footer className="landing-footer">Today · 产品愿景与 MVP 原型</footer>
    </div>
  );
}
