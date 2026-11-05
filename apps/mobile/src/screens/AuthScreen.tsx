import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { AuthTokenResponse } from "@today/contracts";
import { TodayApi, normalizeServerUrl } from "../api";
import { colors, layout } from "../theme";
import { Action, Avatar, Field, Notice, messageOf } from "../ui";

export function AuthScreen({ onAuthenticated, initialServer = "", initialEmail = "", expired = false }: {
  onAuthenticated: (baseUrl: string, result: AuthTokenResponse) => Promise<void>;
  initialServer?: string; initialEmail?: string; expired?: boolean;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [server, setServer] = useState(initialServer || process.env.EXPO_PUBLIC_API_URL || "");
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setError("");
    if (!email.trim() || password.length < 6 || (mode === "register" && !displayName.trim())) {
      setError("请填写邮箱、至少 6 位密码和所需的昵称");
      return;
    }
    setBusy(true);
    try {
      const url = normalizeServerUrl(server);
      const api = new TodayApi(url);
      const result = mode === "login"
        ? await api.login(email.trim(), password)
        : await api.register(email.trim(), password, displayName.trim());
      await onAuthenticated(url, result);
    } catch (failure) {
      setError(messageOf(failure));
    } finally {
      setBusy(false);
    }
  }

  return <SafeAreaView style={styles.root}>
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={styles.panel}>
          <Avatar size={68} />
          <Text style={styles.title}>Today</Text>
          <Text style={styles.subtitle}>从今天开始，慢慢聊。</Text>
          {expired ? <Notice>登录已过期，请重新登录。原账号的未发送草稿会尝试从本机恢复。</Notice> : null}
          <View style={styles.form}>
            <Field label="服务器地址" autoCapitalize="none" keyboardType="url" value={server}
              onChangeText={setServer} placeholder="https://api.example.com" />
            <Field label="邮箱" autoCapitalize="none" keyboardType="email-address" autoComplete="email"
              value={email} onChangeText={setEmail} placeholder="你常用的邮箱" />
            {mode === "register" ? <Field label="昵称" autoComplete="name" value={displayName}
              onChangeText={setDisplayName} placeholder="怎么称呼你" /> : null}
            <Field label="密码" secureTextEntry autoComplete={mode === "login" ? "current-password" : "new-password"}
              value={password} onChangeText={setPassword} placeholder="至少 6 位" />
            {error ? <Notice danger>{error}</Notice> : null}
            <Action label={mode === "login" ? "登录" : "创建账号"} onPress={() => void submit()} busy={busy} />
            <Action variant="quiet" label={mode === "login" ? "创建新账号" : "已有账号，去登录"}
              onPress={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }} />
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  scroll: { flexGrow: 1, justifyContent: "center", alignItems: "center", padding: layout.gutter },
  panel: { width: "100%", maxWidth: 430, paddingVertical: 36, alignItems: "flex-start" },
  title: { fontSize: 37, lineHeight: 48, color: colors.ink, fontWeight: "700", marginTop: 18 },
  subtitle: { color: colors.muted, fontSize: 16, marginTop: 4 },
  form: { marginTop: 36, width: "100%", gap: 4 },
});
