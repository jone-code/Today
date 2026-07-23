import { TodoPanel } from "@/components/TodoPanel";

export default function TodosPage() {
  return (
    <section>
      <h1>待办</h1>
      <p className="app-lead">把今天要推进的事列出来，完成就勾掉。</p>
      <TodoPanel />
    </section>
  );
}
