import { PunchPanel } from "@/components/PunchPanel";

export default function PunchPage() {
  return (
    <section>
      <h1>打卡</h1>
      <p className="app-lead">为习惯打卡，看到连续天数，先把节奏稳下来。</p>
      <PunchPanel />
    </section>
  );
}
