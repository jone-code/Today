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
