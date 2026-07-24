import { TodoPanel } from "@/features/todo/TodoPanel";

export default function TodosPage() {
  return (
    <section>
      <h1>待办</h1>
      <p className="app-lead">记下今天要完成的事，做完就勾掉。</p>
      <TodoPanel />
    </section>
  );
}
