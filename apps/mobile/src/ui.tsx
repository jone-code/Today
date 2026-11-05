import { ReactNode } from "react";
import { ActivityIndicator, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import type { TextInputProps } from "react-native";
import type { LucideIcon } from "lucide-react-native";
import { colors, layout } from "./theme";

export function Page({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  if (!scroll) return <View style={styles.page}><View style={styles.content}>{children}</View></View>;
  return <ScrollView style={styles.page} contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
    <View style={styles.content}>{children}</View>
  </ScrollView>;
}

export function Heading({ title, detail, action }: { title: string; detail?: string; action?: ReactNode }) {
  return <View style={styles.heading}>
    <View style={{ flex: 1 }}><Text style={styles.h1}>{title}</Text>{detail ? <Text style={styles.subheading}>{detail}</Text> : null}</View>
    {action}
  </View>;
}

export function Action({ label, onPress, icon: Icon, variant = "primary", busy = false, disabled = false }: {
  label: string; onPress: () => void; icon?: LucideIcon;
  variant?: "primary" | "quiet" | "danger"; busy?: boolean; disabled?: boolean;
}) {
  const inactive = busy || disabled;
  const main = variant === "primary";
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={inactive} onPress={onPress}
    style={({ pressed }) => [styles.action, main && styles.actionPrimary, variant === "danger" && styles.actionDanger,
      inactive && styles.disabled, pressed && styles.pressed]}>
    {busy ? <ActivityIndicator size="small" color={main ? colors.onPrimary : colors.primary} /> : Icon ? <Icon size={18} color={main ? colors.onPrimary : variant === "danger" ? colors.danger : colors.primary} /> : null}
    <Text style={[styles.actionText, main && styles.actionPrimaryText, variant === "danger" && styles.dangerText]}>{label}</Text>
  </Pressable>;
}

export function IconAction({ icon: Icon, label, onPress, disabled = false, busy = false }: {
  icon: LucideIcon; label: string; onPress: () => void; disabled?: boolean; busy?: boolean;
}) {
  const inactive = disabled || busy;
  return <Pressable accessibilityRole="button" accessibilityLabel={label}
    accessibilityState={{ disabled: inactive, busy }} disabled={inactive} onPress={onPress}
    style={({ pressed }) => [styles.iconButton, inactive && styles.disabled, pressed && styles.pressed]}>
    {busy ? <ActivityIndicator size="small" color={colors.primary} /> : <Icon color={colors.ink} size={22} />}
  </Pressable>;
}

export function Field({ label, error, multiline, ...props }: TextInputProps & { label: string; error?: string }) {
  return <View style={styles.field}>
    <Text style={styles.label}>{label}</Text>
    <TextInput {...props} accessibilityLabel={label} multiline={multiline}
      placeholderTextColor={colors.muted} style={[styles.input, multiline && styles.multiline, error && styles.inputError]} />
    {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
  </View>;
}

export function Separator() { return <View style={styles.separator} />; }

export function Empty({ title, detail }: { title: string; detail?: string }) {
  return <View style={styles.empty}><Text style={styles.emptyTitle}>{title}</Text>{detail ? <Text style={styles.muted}>{detail}</Text> : null}</View>;
}

export function Notice({ children, danger = false }: { children: ReactNode; danger?: boolean }) {
  return <View style={[styles.notice, danger && styles.noticeDanger]}><Text accessibilityRole={danger ? "alert" : undefined}
    style={danger ? styles.dangerText : styles.muted}>{children}</Text></View>;
}

export function Avatar({ uri, token, size = 52 }: { uri?: string | null; token?: string | null; size?: number }) {
  const frame = { width: size, height: size, borderRadius: size / 2 };
  if (uri && token) return <Image accessibilityLabel="个人助手形象" source={{ uri, headers: { Authorization: `Bearer ${token}` } }}
    style={[styles.avatar, frame]} />;
  return <View accessibilityLabel="默认的非真人助手形象" style={[styles.avatar, frame, styles.defaultAvatar]}>
    <View style={[styles.avatarEye, { left: size * 0.29, top: size * 0.39 }]} />
    <View style={[styles.avatarEye, { left: size * 0.62, top: size * 0.39 }]} />
    <View style={[styles.avatarSmile, { left: size * 0.38, top: size * 0.56, width: size * 0.25, height: size * 0.13 }]} />
  </View>;
}

export function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "操作未完成，请稍后重试";
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.background },
  scroll: { flexGrow: 1, paddingHorizontal: layout.gutter, paddingTop: 24, paddingBottom: 32, alignItems: "center" },
  content: { flex: 1, width: "100%", maxWidth: layout.maxWidth },
  heading: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 24 },
  h1: { color: colors.ink, fontSize: 27, fontWeight: "700", lineHeight: 36 },
  subheading: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 3 },
  action: { minHeight: 48, paddingHorizontal: 17, paddingVertical: 11, borderRadius: 7,
    flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8 },
  actionPrimary: { backgroundColor: colors.primary },
  actionDanger: { backgroundColor: colors.attentionSoft },
  actionText: { color: colors.primary, fontSize: 15, fontWeight: "600" },
  actionPrimaryText: { color: colors.onPrimary },
  dangerText: { color: colors.danger, fontSize: 14, lineHeight: 21 },
  iconButton: { height: 48, width: 48, alignItems: "center", justifyContent: "center", borderRadius: 7 },
  pressed: { opacity: 0.64 }, disabled: { opacity: 0.5 },
  field: { gap: 6, marginBottom: 16 }, label: { color: colors.ink, fontSize: 14, fontWeight: "600" },
  input: { backgroundColor: colors.surface, color: colors.ink, borderWidth: 1, borderColor: colors.line,
    borderRadius: 7, minHeight: 50, paddingHorizontal: 14, paddingVertical: 10, fontSize: 16 },
  multiline: { minHeight: 120, textAlignVertical: "top", lineHeight: 24 },
  inputError: { borderColor: colors.danger }, error: { color: colors.danger, fontSize: 13, lineHeight: 19 },
  separator: { height: 1, backgroundColor: colors.line, marginVertical: 16 },
  empty: { flex: 1, alignItems: "center", justifyContent: "center", minHeight: 230, padding: 28, gap: 8 },
  emptyTitle: { color: colors.ink, fontSize: 18, fontWeight: "600", textAlign: "center" },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 22 },
  notice: { backgroundColor: colors.primarySoft, borderRadius: 7, padding: 12, marginBottom: 16 },
  noticeDanger: { backgroundColor: colors.attentionSoft },
  avatar: { overflow: "hidden" },
  defaultAvatar: { backgroundColor: "#B9D8CB", borderWidth: 2, borderColor: colors.primary, position: "relative" },
  avatarEye: { position: "absolute", backgroundColor: colors.ink, width: 4, height: 5, borderRadius: 3 },
  avatarSmile: { position: "absolute", borderBottomWidth: 2, borderColor: colors.ink, borderBottomLeftRadius: 15, borderBottomRightRadius: 15 },
});
