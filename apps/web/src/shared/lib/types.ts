export type Mood = "great" | "good" | "okay" | "tired" | "low";

export type SummaryStatus = "ready" | "processing";

export type DayEntry = {
  id: string;
  date: string; // YYYY-MM-DD
  rawText: string;
  createdAt: string;
  summary: DaySummary;
  /** async checkin 后 summary 尚未生成时为 processing */
  summaryStatus: SummaryStatus;
};

export type DaySummary = {
  completed: string[];
  mood: Mood;
  moodLabel: string;
  keywords: string[];
  oneLiner: string;
  highlight: string;
};

export type MemoryItem = {
  id: string;
  text: string;
  category: "work" | "health" | "learning" | "life" | "emotion" | "goal";
  strength: number;
  archived: boolean;
  updatedAt: string;
};

export type ProactivePrompt = {
  id: string;
  text: string;
  relatedDate?: string;
  source?: "followup" | "pattern" | "memory" | "gentle";
};

/** 主链路保存阶段（UI 状态机） */
export type CheckinLoopPhase =
  | "idle"
  | "submitting"
  | "processing"
  | "error";
