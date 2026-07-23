import { TimelineList } from "@/features/timeline/TimelineList";

export default function TimelinePage() {
  return (
    <section>
      <h1>时间轴</h1>
      <p className="app-lead">每天一张卡片。以后可以快速回看，你是怎么一路走来的。</p>
      <TimelineList />
    </section>
  );
}
