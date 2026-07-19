export type Mood = "great" | "good" | "okay" | "tired" | "low";

export type DayEntry = {
  id: string;
  date: string; // YYYY-MM-DD
  rawText: string;
  createdAt: string;
  summary: DaySummary;
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
  updatedAt: string;
};

export type ProactivePrompt = {
  id: string;
  text: string;
  relatedDate?: string;
};
