import { ReminderPanel } from "@/components/ReminderPanel";

export default function RemindersPage() {
  return (
    <section>
      <h1>定时提醒</h1>
      <p className="app-lead">
        设定每天提醒自己留下今天。到点后会出现在「最近送达」里。
      </p>
      <ReminderPanel />
    </section>
  );
}
