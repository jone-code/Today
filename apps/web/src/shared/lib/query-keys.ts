export const todayKeys = {
  all: ["today"] as const,
  bundle: () => [...todayKeys.all, "bundle"] as const,
};

export const reminderKeys = {
  all: ["reminders"] as const,
  list: () => [...reminderKeys.all, "list"] as const,
  deliveries: (limit = 20) => [...reminderKeys.all, "deliveries", limit] as const,
};

export const authKeys = {
  all: ["auth"] as const,
  me: () => [...authKeys.all, "me"] as const,
};

export const todoKeys = {
  all: ["todos"] as const,
  list: (status: "open" | "done" | "all" = "open") =>
    [...todoKeys.all, "list", status] as const,
};

export const punchKeys = {
  all: ["punch"] as const,
  habits: (date?: string) => [...punchKeys.all, "habits", date ?? "today"] as const,
};

export const memoryKeys = {
  all: ["memories"] as const,
  list: (includeArchived = false) =>
    [...memoryKeys.all, "list", includeArchived] as const,
};
