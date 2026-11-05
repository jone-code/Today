import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, AppState, View } from "react-native";
import { NavigationContainer } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { MessageCircle, CalendarDays, CheckSquare, Inbox, UserRound } from "lucide-react-native";
import * as SecureStore from "expo-secure-store";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { AuthTokenResponseSchema, type AuthTokenResponse } from "@today/contracts";
import { ApiRequestError, TodayApi, normalizeServerUrl } from "./api";
import { colors } from "./theme";
import { AuthScreen } from "./screens/AuthScreen";
import { ChatScreen } from "./screens/ChatScreen";
import { TodayScreen } from "./screens/TodayScreen";
import { ActionsScreen } from "./screens/ActionsScreen";
import { InboxScreen } from "./screens/InboxScreen";
import { HistoryScreen } from "./screens/HistoryScreen";
import { ProfileScreen } from "./screens/ProfileScreen";
import { TimelineScreen } from "./screens/TimelineScreen";
import { FeedbackScreen } from "./screens/FeedbackScreen";

const SESSION_KEY = "today-session";
type Session = { baseUrl: string; auth: AuthTokenResponse };
type LoginHint = { baseUrl: string; email: string; expired: boolean };
type SessionContextValue = { api: TodayApi; session: Session; signOut: () => Promise<void> };
const SessionContext = createContext<SessionContextValue | null>(null);

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error("Session is unavailable");
  return context;
}

export type RootStackParams = {
  Tabs: undefined;
  History: undefined;
  Conversation: { id: string };
  Profile: undefined;
  Timeline: undefined;
  Feedback: undefined;
};

const Stack = createNativeStackNavigator<RootStackParams>();
const Tabs = createBottomTabNavigator();

function MainTabs() {
  const { api } = useSession();
  const [unreadCount, setUnreadCount] = useState(0);
  const unreadRequest = useRef(0);
  const updateUnreadCount = useCallback((count: number) => {
    unreadRequest.current += 1;
    setUnreadCount(count);
  }, []);

  useEffect(() => {
    const refreshUnread = () => {
      const request = ++unreadRequest.current;
      void api.unreadCount().then(({ count }) => {
        if (request === unreadRequest.current) setUnreadCount(count);
      }).catch(() => {});
    };
    if (AppState.currentState === "active") refreshUnread();
    const subscription = AppState.addEventListener("change", state => {
      if (state === "active") refreshUnread();
    });
    const timer = setInterval(() => {
      if (AppState.currentState === "active") refreshUnread();
    }, 60000);
    return () => { unreadRequest.current += 1; subscription.remove(); clearInterval(timer); };
  }, [api]);

  return <Tabs.Navigator screenOptions={({ route }) => ({ headerShown: false, tabBarActiveTintColor: colors.primary,
    tabBarInactiveTintColor: colors.muted, tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.line, height: 64 },
    tabBarLabelStyle: { fontSize: 12, fontWeight: "600", marginBottom: 6 },
    tabBarIcon: ({ color }) => {
      const icons = { Chat: MessageCircle, Today: CalendarDays, Actions: CheckSquare, Inbox: Inbox };
      const Icon = icons[route.name as keyof typeof icons] || UserRound;
      return <Icon color={color} size={22} />;
    },
  })}>
    <Tabs.Screen name="Chat" component={ChatScreen} options={{ tabBarLabel: "对话" }} />
    <Tabs.Screen name="Today" component={TodayScreen} options={{ tabBarLabel: "今天" }} />
    <Tabs.Screen name="Actions" component={ActionsScreen} options={{ tabBarLabel: "行动" }} />
    <Tabs.Screen name="Inbox" options={{ tabBarLabel: "收件箱",
      tabBarBadge: unreadCount > 0 ? unreadCount > 99 ? "99+" : unreadCount : undefined,
      tabBarBadgeStyle: { backgroundColor: colors.attention, color: colors.onPrimary },
      tabBarAccessibilityLabel: unreadCount > 0 ? `收件箱，${unreadCount}条未读站内消息` : "收件箱，没有未读站内消息",
    }}>
      {() => <InboxScreen onUnreadChange={updateUnreadCount} />}
    </Tabs.Screen>
  </Tabs.Navigator>;
}

function AppContent() {
  const [session, setSession] = useState<Session | null>(null);
  const sessionRef = useRef<Session | null>(null);
  const [loginHint, setLoginHint] = useState<LoginHint | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const saved = await SecureStore.getItemAsync(SESSION_KEY);
        if (!saved) return;
        const parsed = JSON.parse(saved) as Session;
        const baseUrl = normalizeServerUrl(parsed.baseUrl);
        const auth = AuthTokenResponseSchema.parse(parsed.auth);
        try {
          await new TodayApi(baseUrl, auth.token).me(8000);
        } catch (failure) {
          if (failure instanceof ApiRequestError && failure.status === 401) {
            if (active) setLoginHint({ baseUrl, email: auth.user.email, expired: true });
            return;
          }
        }
        if (active) {
          sessionRef.current = { baseUrl, auth };
          setSession(sessionRef.current);
        }
      } catch {
        // Invalid local credentials require an explicit login.
      } finally {
        if (active) setRestoring(false);
      }
    })();
    return () => { active = false; };
  }, []);

  const value = useMemo(() => session ? {
    session,
    api: new TodayApi(session.baseUrl, session.auth.token, () => {
      if (sessionRef.current !== session) return;
      sessionRef.current = null;
      setLoginHint({ baseUrl: session.baseUrl, email: session.auth.user.email, expired: true });
      setSession(null);
    }),
    signOut: async () => {
      await SecureStore.deleteItemAsync(SESSION_KEY);
      sessionRef.current = null;
      setLoginHint(null);
      setSession(null);
    },
  } : null, [session]);

  if (restoring) return <View style={{ flex: 1, backgroundColor: colors.background, justifyContent: "center" }}>
    <ActivityIndicator color={colors.primary} /><StatusBar style="dark" />
  </View>;

  if (!value) return <><AuthScreen initialServer={loginHint?.baseUrl} initialEmail={loginHint?.email}
    expired={loginHint?.expired} onAuthenticated={async (baseUrl, auth) => {
    const next = { baseUrl, auth };
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(next));
    sessionRef.current = next;
    setLoginHint(null);
    setSession(next);
  }} /><StatusBar style="dark" /></>;

  return <SessionContext.Provider value={value}>
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerTintColor: colors.ink, headerStyle: { backgroundColor: colors.background },
        headerShadowVisible: false, contentStyle: { backgroundColor: colors.background } }}>
        <Stack.Screen name="Tabs" component={MainTabs} options={{ headerShown: false }} />
        <Stack.Screen name="History" component={HistoryScreen} options={{ title: "历史对话" }} />
        <Stack.Screen name="Conversation" component={ChatScreen} options={{ title: "对话" }} />
        <Stack.Screen name="Profile" component={ProfileScreen} options={{ title: "形象与设置" }} />
        <Stack.Screen name="Timeline" component={TimelineScreen} options={{ title: "回看" }} />
        <Stack.Screen name="Feedback" component={FeedbackScreen} options={{ title: "问题与建议" }} />
      </Stack.Navigator>
    </NavigationContainer>
    <StatusBar style="dark" />
  </SessionContext.Provider>;
}

export default function App() {
  return <SafeAreaProvider><AppContent /></SafeAreaProvider>;
}
