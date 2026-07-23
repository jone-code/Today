"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/shared/lib/auth-context";
import { makeQueryClient } from "@/shared/lib/query-client";
import { useState, type ReactNode } from "react";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => makeQueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
