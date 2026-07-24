import { MemoryPanel } from "@/features/memory/MemoryPanel";

export default function MemoryPage() {
  return (
    <section>
      <h1>长期记忆</h1>
      <p className="app-lead">
        AI 自动建立对你的理解，并随每一天更新。你不需要维护这份名单。
      </p>
      <MemoryPanel />
    </section>
  );
}
