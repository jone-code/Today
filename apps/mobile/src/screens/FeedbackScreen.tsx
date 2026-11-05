import { useCallback, useEffect, useState } from "react";
import { Alert, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { randomUUID } from "expo-crypto";
import { Send, Trash2 } from "lucide-react-native";
import type { Feedback, FeedbackCreateRequest } from "@today/contracts";
import { useSession } from "../App";
import { ApiRequestError } from "../api";
import { deleteFeedbackDraft, feedbackDraftKey, readFeedbackDraft,
  writeFeedbackDraft, type FeedbackDraft } from "../feedbackDraft";
import { colors } from "../theme";
import { Action, Field, IconAction, Notice, Page, Separator, messageOf } from "../ui";

export function FeedbackScreen() {
  const { api, session } = useSession();
  const key = feedbackDraftKey(session.baseUrl, session.auth.user.id);
  const [kind, setKind] = useState<FeedbackCreateRequest["kind"]>("issue");
  const [body, setBody] = useState("");
  const [savedDraft, setSavedDraft] = useState<FeedbackDraft | null>(null);
  const [ready, setReady] = useState(false);
  const [draftError, setDraftError] = useState(false);
  const [items, setItems] = useState<Feedback[]>([]);
  const [historyError, setHistoryError] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [requestConflict, setRequestConflict] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);

  const restore = useCallback(async () => {
    setReady(false);
    try {
      const draft = await readFeedbackDraft(key);
      setSavedDraft(draft);
      setKind(draft?.kind ?? "issue");
      setBody(draft?.body ?? "");
      setDraftError(false);
      setRequestConflict(false);
      setReady(true);
      setError("");
    } catch (failure) {
      setDraftError(true);
      setError(messageOf(failure));
    }
  }, [key]);

  useEffect(() => { void restore(); }, [restore]);
  useFocusEffect(useCallback(() => {
    let active = true;
    void api.feedback().then(reports => {
      if (active) { setItems(reports); setHistoryError(""); }
    }).catch(failure => { if (active) setHistoryError(messageOf(failure)); });
    return () => { active = false; };
  }, [api]));

  async function clearDamagedDraft() {
    try {
      await deleteFeedbackDraft(key);
      setSavedDraft(null); setBody(""); setKind("issue");
      setDraftError(false); setReady(true); setError("");
      setRequestConflict(false);
    } catch (failure) { setError(messageOf(failure)); }
  }

  async function startNewSubmission() {
    try {
      await deleteFeedbackDraft(key);
      setSavedDraft(null);
      setRequestConflict(false);
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
  }

  async function submit() {
    if (!ready || busy || requestConflict) return;
    const text = body.trim();
    if (!text || text.length > 1000) { setError("请填写 1 到 1000 字的反馈内容"); return; }
    setBusy(true); setError(""); setSuccess("");
    const draft = savedDraft?.kind === kind && savedDraft.body === text
      ? savedDraft : { kind, body: text, requestId: randomUUID() };
    try {
      await writeFeedbackDraft(key, draft);
      setSavedDraft(draft);
      const report = await api.submitFeedback({ kind, body: text }, draft.requestId);
      setItems(previous => [report, ...previous.filter(item => item.id !== report.id)].slice(0, 20));
      setSuccess("反馈已收到");
      setRequestConflict(false);
      try {
        await deleteFeedbackDraft(key);
        setSavedDraft(null); setBody("");
      } catch {
        setError("反馈已收到，但本机草稿未清除。重试仍会返回同一条反馈，请稍后重试。");
      }
    } catch (failure) {
      if (failure instanceof ApiRequestError && failure.status === 409) {
        setRequestConflict(true);
        setError("这次提交已失效。清除重试记录后可重新提交，文字会保留。");
      } else {
        setError(failure instanceof ApiRequestError && failure.status === 429
          ? "当前时段提交次数已达上限，请稍后再试" : messageOf(failure));
      }
    } finally { setBusy(false); }
  }

  async function remove(id: string) {
    if (busy || deleting) return;
    setDeleting(id); setError("");
    try {
      await api.deleteFeedback(id);
      setItems(previous => previous.filter(item => item.id !== id));
      setSuccess("反馈已从列表移除");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setDeleting(null); }
  }

  return <SafeAreaView edges={["bottom"]} style={styles.root}>
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <Page>
        <Text style={styles.title}>试用反馈</Text>
        <Text style={styles.muted}>仅提交你填写的内容，不自动附带对话、照片或设备日志。</Text>
        {success ? <Notice>{success}</Notice> : null}
        {error ? <Notice danger>{error}</Notice> : null}
        {requestConflict ? <Action label="清除重试记录" variant="quiet"
          onPress={() => void startNewSubmission()} /> : null}
        {draftError ? <View style={styles.row}>
          <Action label="重新读取草稿" variant="quiet" onPress={() => void restore()} />
          <Action label="清除本机草稿" variant="danger" onPress={() => Alert.alert(
            "清除本机反馈草稿？", "未确认提交的文字和重试记录会丢失。", [
              { text: "保留", style: "cancel" },
              { text: "清除", style: "destructive", onPress: () => void clearDamagedDraft() },
            ])} />
        </View> : null}
        <Text style={styles.label}>反馈类型</Text>
        <View style={styles.row}>
          {(["issue", "idea"] as const).map(value => <Pressable key={value}
            accessibilityRole="button" accessibilityLabel={value === "issue" ? "问题" : "建议"}
            accessibilityState={{ selected: kind === value, disabled: !ready || busy }}
            disabled={!ready || busy} onPress={() => { setKind(value); setError(""); }}
            style={({ pressed }) => [styles.segment, kind === value && styles.selected,
              pressed && styles.pressed]}>
            <Text style={[styles.segmentText, kind === value && styles.selectedText]}>
              {value === "issue" ? "问题" : "建议"}
            </Text>
          </Pressable>)}
        </View>
        <Field label="反馈内容" multiline value={body} maxLength={1000}
          editable={ready && !busy} onChangeText={value => { setBody(value); setError(""); }}
          placeholder="发生了什么，或者你希望如何改进" />
        <Action label="提交反馈" icon={Send} onPress={() => void submit()} busy={busy}
          disabled={!ready || requestConflict || !body.trim() || body.trim().length > 1000} />
        <Separator />
        <Text style={styles.sectionTitle}>最近提交</Text>
        {historyError ? <Notice danger>{historyError}</Notice> : null}
        {items.length === 0 && !historyError ? <Text style={styles.muted}>还没有反馈记录</Text> : null}
        {items.map(item => <View key={item.id} style={styles.item}>
          <View style={styles.itemHeader}>
            <Text style={styles.meta}>{item.kind === "issue" ? "问题" : "建议"} · {
              new Date(item.createdAt).toLocaleDateString("zh-CN")}</Text>
            <IconAction icon={Trash2} label="移除这条反馈" disabled={busy || deleting !== null}
              busy={deleting === item.id} onPress={() => Alert.alert("移除这条反馈？", undefined, [
                { text: "取消", style: "cancel" },
                { text: "移除", style: "destructive", onPress: () => void remove(item.id) },
              ])} />
          </View>
          <Text style={styles.body}>{item.body}</Text>
        </View>)}
      </Page>
    </KeyboardAvoidingView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  title: { color: colors.ink, fontSize: 22, lineHeight: 30, fontWeight: "700", marginBottom: 8 },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21, marginBottom: 20 },
  label: { color: colors.ink, fontSize: 14, fontWeight: "600", marginBottom: 8 },
  row: { flexDirection: "row", gap: 8, flexWrap: "wrap", marginBottom: 18 },
  segment: { flex: 1, minWidth: 100, minHeight: 48, alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: colors.line, borderRadius: 7, backgroundColor: colors.surface },
  selected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  segmentText: { color: colors.muted, fontSize: 15, fontWeight: "600" },
  selectedText: { color: colors.primary },
  pressed: { opacity: 0.7 },
  sectionTitle: { color: colors.ink, fontSize: 18, fontWeight: "700", marginBottom: 12 },
  item: { borderBottomColor: colors.line, borderBottomWidth: 1, paddingVertical: 12 },
  itemHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  meta: { color: colors.muted, fontSize: 13, lineHeight: 20 },
  body: { color: colors.ink, fontSize: 15, lineHeight: 23, paddingBottom: 6 },
});
