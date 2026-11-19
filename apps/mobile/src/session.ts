import { createContext, useContext } from "react";
import type { AuthTokenResponse } from "@today/contracts";
import type { TodayApi } from "./api";

export type Session = { baseUrl: string; auth: AuthTokenResponse };
export type SessionContextValue = {
  api: TodayApi;
  session: Session;
  signOut: () => Promise<void>;
  onDeletionStarted: () => void;
};

export const SessionContext = createContext<SessionContextValue | null>(null);

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error("Session is unavailable");
  return context;
}
