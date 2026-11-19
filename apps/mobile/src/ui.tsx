import { ReactNode, useEffect, useRef } from "react";
import { ActivityIndicator, Animated, Easing, Image, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import type { TextInputProps } from "react-native";
import type { LucideIcon } from "lucide-react-native";
import Svg, { Circle } from "react-native-svg";
import { colors, layout, radius, type } from "./theme";

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

export function Surface({ children, tone = "default", style }: {
  children: ReactNode; tone?: "default" | "sage" | "lavender" | "attention" | "dark"; style?: object;
}) {
  const toneStyle = tone === "sage" ? styles.surfaceSage : tone === "lavender" ? styles.surfaceLavender :
    tone === "attention" ? styles.surfaceAttention : tone === "dark" ? styles.surfaceDark : styles.surfaceDefault;
  return <View style={[styles.surface, toneStyle, style]}>{children}</View>;
}

export function SectionLabel({ title, action }: { title: string; action?: ReactNode }) {
  return <View style={styles.sectionLabel}><Text style={styles.sectionTitle}>{title}</Text>{action}</View>;
}

export function Pill({ children, tone = "neutral", onPress }: {
  children: ReactNode; tone?: "neutral" | "sage" | "attention" | "lavender"; onPress?: () => void;
}) {
  const toneStyle = tone === "sage" ? styles.pillSage : tone === "attention" ? styles.pillAttention :
    tone === "lavender" ? styles.pillLavender : styles.pillNeutral;
  const textStyle = tone === "sage" ? styles.pillSageText : tone === "attention" ? styles.pillAttentionText :
    tone === "lavender" ? styles.pillLavenderText : styles.pillNeutralText;
  const content = <Text style={[styles.pillText, textStyle]}>{children}</Text>;
  if (!onPress) return <View style={[styles.pill, toneStyle]}>{content}</View>;
  return <Pressable accessibilityRole="button" onPress={onPress}
    style={({ pressed }) => [styles.pill, toneStyle, pressed && styles.pressed]}>{content}</Pressable>;
}

export function ProgressRing({ value, size = 72, stroke = 7, color = colors.primary }: {
  value: number; size?: number; stroke?: number; color?: string;
}) {
  const radiusValue = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radiusValue;
  const progress = Math.max(0, Math.min(1, value));
  return <View style={{ width: size, height: size, alignItems: "center", justifyContent: "center" }}>
    <Svg width={size} height={size} viewBox={"0 0 " + size + " " + size}>
      <Circle cx={size / 2} cy={size / 2} r={radiusValue} stroke={colors.line} strokeWidth={stroke} fill="none" />
      <Circle cx={size / 2} cy={size / 2} r={radiusValue} stroke={color} strokeWidth={stroke} fill="none"
        strokeLinecap="round" strokeDasharray={circumference + " " + circumference}
        strokeDashoffset={circumference * (1 - progress)} rotation={-90}
        origin={(size / 2) + ", " + (size / 2)} />
    </Svg>
    <Text style={styles.ringText}>{Math.round(progress * 100)}%</Text>
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
    {label ? <Text style={styles.label}>{label}</Text> : null}
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
  return <DefaultAvatar size={size} frame={frame} />;
}

function DefaultAvatar({ size, frame }: { size: number; frame: { width: number; height: number; borderRadius: number } }) {
  const shine = useRef(new Animated.Value(0)).current;
  const pulse = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const shineLoop = Animated.loop(Animated.timing(shine, {
      toValue: 1, duration: 6200, easing: Easing.inOut(Easing.cubic), useNativeDriver: true,
    }));
    const pulseLoop = Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 1, duration: 2200, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 0, duration: 2200, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
    ]));
    shineLoop.start(); pulseLoop.start();
    return () => { shineLoop.stop(); pulseLoop.stop(); };
  }, [pulse, shine]);
  const shineX = shine.interpolate({ inputRange: [0, 1], outputRange: [-size * 0.7, size * 1.45] });
  const pulseOpacity = pulse.interpolate({ inputRange: [0, 1], outputRange: [0.08, 0.28] });
  const pulseScale = pulse.interpolate({ inputRange: [0, 1], outputRange: [0.96, 1.03] });
  return <View accessibilityLabel="Today 默认品牌 Logo" style={[styles.avatar, frame, styles.defaultAvatar]}>
    <Image accessibilityLabel="Today 奶白色虚拟宠物 Logo" source={require("../assets/todaylogo.png")}
      resizeMode="contain" style={styles.defaultAvatarImage} />
    <Animated.View pointerEvents="none" style={[styles.avatarShine, { height: size * 1.15, width: size * 0.16 },
      { opacity: pulseOpacity, transform: [{ translateX: shineX }, { rotate: "24deg" }] }]} />
    <Animated.View pointerEvents="none" style={[styles.avatarPulse, { width: size * 0.22, height: size * 0.22,
      borderRadius: size, left: size * 0.69, top: size * 0.16 }, { opacity: pulseOpacity, transform: [{ scale: pulseScale }] }]} />
  </View>;
}

export function messageOf(error: unknown) {
  return error instanceof Error ? error.message : "操作未完成，请稍后重试";
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.background },
  scroll: { flexGrow: 1, paddingHorizontal: layout.gutter, paddingTop: 22, paddingBottom: 38, alignItems: "center" },
  content: { flex: 1, width: "100%", maxWidth: layout.maxWidth },
  heading: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 24 },
  h1: { color: colors.ink, fontSize: type.title, fontWeight: "800", letterSpacing: -0.6, lineHeight: 30 },
  subheading: { color: colors.muted, fontSize: type.caption, lineHeight: 20, marginTop: 5 },
  surface: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.line,
    padding: 16, shadowColor: colors.ink, shadowOpacity: 0.06, shadowRadius: 16, shadowOffset: { width: 0, height: 8 }, elevation: 2 },
  surfaceDefault: {},
  surfaceSage: { backgroundColor: colors.primarySoft, borderColor: "transparent" },
  surfaceLavender: { backgroundColor: colors.lavenderSoft, borderColor: "transparent" },
  surfaceAttention: { backgroundColor: colors.attentionSoft, borderColor: "transparent" },
  surfaceDark: { backgroundColor: colors.primaryDeep, borderColor: "transparent" },
  sectionLabel: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 10 },
  sectionTitle: { color: colors.ink, fontSize: type.section, lineHeight: 23, fontWeight: "800", letterSpacing: -0.2 },
  pill: { minHeight: 32, paddingHorizontal: 12, borderRadius: radius.pill, alignItems: "center", justifyContent: "center",
    backgroundColor: colors.input, borderWidth: 1, borderColor: colors.line },
  pillNeutral: { backgroundColor: colors.input },
  pillSage: { backgroundColor: colors.primarySoft, borderColor: "transparent" },
  pillAttention: { backgroundColor: colors.attentionSoft, borderColor: "transparent" },
  pillLavender: { backgroundColor: colors.lavenderSoft, borderColor: "transparent" },
  pillText: { color: colors.inkSoft, fontSize: type.micro, fontWeight: "700" },
  pillNeutralText: { color: colors.inkSoft },
  pillSageText: { color: colors.primaryDeep },
  pillAttentionText: { color: colors.attention },
  pillLavenderText: { color: colors.lavenderDark },
  ringText: { position: "absolute", color: colors.ink, fontSize: type.caption, fontWeight: "800" },
  action: { minHeight: 48, paddingHorizontal: 18, paddingVertical: 11, borderRadius: radius.md,
    flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8 },
  actionPrimary: { backgroundColor: colors.primaryDeep },
  actionDanger: { backgroundColor: colors.attentionSoft },
  actionText: { color: colors.primaryDeep, fontSize: type.caption, fontWeight: "700" },
  actionPrimaryText: { color: colors.onPrimary },
  dangerText: { color: colors.danger, fontSize: type.caption, lineHeight: 20 },
  iconButton: { height: 46, width: 46, alignItems: "center", justifyContent: "center", borderRadius: radius.pill,
    backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line },
  pressed: { opacity: 0.64 }, disabled: { opacity: 0.5 },
  field: { gap: 7, marginBottom: 17 }, label: { color: colors.inkSoft, fontSize: type.caption, fontWeight: "700" },
  input: { backgroundColor: colors.surface, color: colors.ink, borderWidth: 1, borderColor: colors.line,
    borderRadius: radius.md, minHeight: 52, paddingHorizontal: 15, paddingVertical: 11, fontSize: type.body },
  multiline: { minHeight: 126, textAlignVertical: "top", lineHeight: 24 },
  inputError: { borderColor: colors.danger }, error: { color: colors.danger, fontSize: type.micro, lineHeight: 18 },
  separator: { height: 1, backgroundColor: colors.line, marginVertical: 18 },
  empty: { flex: 1, alignItems: "center", justifyContent: "center", minHeight: 230, padding: 28, gap: 8 },
  emptyTitle: { color: colors.ink, fontSize: type.section, fontWeight: "700", textAlign: "center" },
  muted: { color: colors.muted, fontSize: type.caption, lineHeight: 22 },
  notice: { backgroundColor: colors.primarySoft, borderRadius: radius.md, padding: 14, marginBottom: 16 },
  noticeDanger: { backgroundColor: colors.attentionSoft },
  avatar: { overflow: "hidden" },
  defaultAvatar: { backgroundColor: "#FFF8EF", borderWidth: 1, borderColor: "#E5D2C1", position: "relative" },
  defaultAvatarImage: { width: "100%", height: "100%" },
  avatarShine: { position: "absolute", left: 0, top: -20, backgroundColor: "#FFFFFF", borderRadius: 999 },
  avatarPulse: { position: "absolute", backgroundColor: "#FFE9DD" },
});
