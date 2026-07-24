import { PunchPanel } from "@/features/punch/PunchPanel";

export default function PunchPage() {
  return (
    <section>
      <h1>习惯打卡</h1>
      <p className="app-lead">选几件想坚持的小事，每天留下一个勾选。</p>
      <PunchPanel />
    </section>
  );
}
