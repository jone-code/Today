import { useCallback, useRef, useState } from "react";
import { Alert, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { ArrowRight, RefreshCw } from "lucide-react-native";
import { randomUUID } from "expo-crypto";
import type { CheckinMemoryCandidate, DaySummaryDto } from "@today/contracts";
import { useSession, type RootStackParams } from "../App";
import { checkinDraftKey, readCheckinDraft, writeCheckinDraft, type CheckinDraft } from "../checkinDraft";
import { colors } from "../theme";
import { Action, Field, Heading, Notice, Page, Separator, messageOf } from "../ui";

export function TodayScreen() {
  const { api, session } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const draftKey = checkinDraftKey(session.baseUrl, session.auth.user.id);
  const edited = useRef(false);
  const draft = useRef<CheckinDraft | null>(null);
  const draftWrite = useRef<Promise<void>>(Promise.resolve());
  const [draftReady, setDraftReady] = useState(false);
  const [checkinId, setCheckinId] = useState<string | null>(null);
  const [savedText, setSavedText] = useState("");
  const [text, setText] = useState("");
  const [date, setDate] = useState("");
  const [jobStatus, setJobStatus] = useState("");
  const [summary, setSummary] = useState<DaySummaryDto | null>(null);
  const [candidates, setCandidates] = useState<CheckinMemoryCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [candidateBusy, setCandidateBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [draftError, setDraftError] = useState("");
  const [draftNotice, setDraftNotice] = useState("");

  function persistDraft(next: CheckinDraft | null) {
    draftWrite.current = draftWrite.current.catch(() => {}).then(async () => {
      await writeCheckinDraft(draftKey, next);
      setDraftError("");
    }).catch(() => {
      setDraftError("草稿无法保存在本机，提交已暂停；请检查设备存储空间后重试。");
      throw new Error("草稿尚未安全保存，请重试。");
    });
    return draftWrite.current;
  }

  function edit(next: string) {
    edited.current = next !== savedText;
    setText(next);
    setDraftNotice("");
    draft.current = edited.current ? { text: next, original: savedText, requestId: randomUUID() } : null;
    void persistDraft(draft.current).catch(() => {});
  }

  const refresh = useCallback(async (keepDraft = false) => {
    try {
      const result = await api.today();
      const serverText = result.checkin?.rawText || "";
      setCheckinId(result.checkin?.id || null);
      setSavedText(serverText);
      if (!keepDraft && !edited.current) {
        await draftWrite.current.catch(() => {});
        let restored;
        try { restored = await readCheckinDraft(draftKey); }
        catch (failure) {
          setDraftReady(false);
          setDraftError(messageOf(failure));
          throw failure;
        }
        if (!edited.current && restored && restored.text !== serverText) {
          const requestId = restored.original === serverText && restored.requestId
            ? restored.requestId : randomUUID();
          draft.current = { text: restored.text, original: serverText, requestId };
          setText(restored.text);
          edited.current = true;
          setDraftNotice(restored.original === serverText ? "已恢复未保存的草稿。" :
            "已恢复本机草稿，服务器记录期间已有变化；保存会替换当前记录。");
          if (requestId !== restored.requestId || restored.original !== serverText) {
            void persistDraft(draft.current).catch(() => {});
          }
        } else if (!edited.current) {
          draft.current = null;
          setText(serverText);
          setDraftNotice("");
          if (restored) await persistDraft(null);
        }
        setDraftReady(true);
      }
      setDate(result.checkin?.date || "");
      setJobStatus(result.aiJob?.status || "");
      if (result.checkin) {
        const [daily, pending] = await Promise.all([
          api.summary(result.checkin.date), api.checkinCandidates(result.checkin.id),
        ]);
        setSummary(daily); setCandidates(pending);
      } else { setSummary(null); setCandidates([]); }
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
  }, [api, draftKey]);

  useFocusEffect(useCallback(() => {
    void refresh();
    const timer = setInterval(() => void refresh(true), 8000);
    return () => clearInterval(timer);
  }, [refresh]));

  async function save() {
    if (!text.trim() || busy || !draftReady) return;
    setBusy(true); setError("");
    try {
      const pending = draft.current;
      if (!pending || pending.text !== text) throw new Error("记录草稿尚未准备好，请重试。");
      await draftWrite.current.catch(() => {});
      const stable = { ...pending, text: pending.text.trim() };
      await persistDraft(stable);
      draft.current = stable;
      await api.submitCheckin(stable.text, undefined, stable.requestId);
      edited.current = false;
      setText(stable.text);
      setSavedText(stable.text);
      draft.current = null;
      await persistDraft(null);
      setDraftNotice("");
      await refresh();
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  function confirmClearDraft() {
    Alert.alert("清除本机记录草稿？", "未收到服务器确认的修改内容和重试记录可能会丢失。", [
      { text: "保留", style: "cancel" },
      { text: "清除草稿", style: "destructive", onPress: () => {
        setDraftReady(false);
        draftWrite.current = draftWrite.current.catch(() => {}).then(() => writeCheckinDraft(draftKey, null));
        void draftWrite.current.then(() => {
          draft.current = null;
          edited.current = false;
          setText(savedText);
          setDraftError("");
          setDraftNotice("");
          setDraftReady(true);
        }).catch(() => setDraftError("无法清除本机草稿，请检查设备存储后重试。"));
      } },
    ]);
  }

  function confirmSave() {
    if (savedText && text.trim() !== savedText) {
      Alert.alert("替换今天的记录？", "旧记录将被新内容替换，基于旧内容的记忆建议会失效。", [
        { text: "取消", style: "cancel" },
        { text: "保存修改", onPress: () => void save() },
      ]);
    } else {
      void save();
    }
  }

  async function candidateAction(id: string, action: "confirm" | "reject", text?: string) {
    if (candidateBusy) return;
    setCandidateBusy(id);
    try {
      await api.updateCheckinCandidate(id, action, text);
      setCandidates(previous => previous.filter(item => item.id !== id));
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setCandidateBusy(null); }
  }

  return <Page>
    <Heading title="今天" detail={date || "留下一点今天的事"}
      action={<Action icon={ArrowRight} variant="quiet" label="回看" onPress={() => navigation.navigate("Timeline")} />} />
    {error ? <Notice danger>{error}</Notice> : null}
    {draftNotice ? <Notice>{draftNotice}</Notice> : null}
    {draftError ? <Notice danger>{draftError}</Notice> : null}
    {!draftReady ? <View style={styles.row}>
      <Action label="重新读取" variant="quiet" onPress={() => void refresh()} />
      {draftError ? <Action label="清除本机草稿" variant="danger" onPress={confirmClearDraft} /> : null}
    </View> : null}
    <Field label="今天的记录" multiline numberOfLines={6} value={text} onChangeText={edit} editable={!busy && draftReady}
      placeholder="今天发生了什么？" maxLength={2000} />
    {savedText && text.trim() !== savedText ? <Notice danger>保存会替换今天已有的记录，并使未确认的旧记忆建议失效。</Notice> : null}
    <Action label={savedText ? "保存修改" : "保存今天"} onPress={confirmSave}
      busy={busy} disabled={!draftReady || !text.trim() || text.trim() === savedText} />
    {jobStatus === "failed" ? <View style={styles.status}>
      <Notice danger>这次整理未完成，原始记录已保存。</Notice>
      <Action label="重新整理" icon={RefreshCw} variant="quiet"
        onPress={() => void api.retryToday().then(() => refresh(true)).catch(e => setError(messageOf(e)))} />
    </View> : jobStatus === "running" || jobStatus === "pending" ?
      <Text style={styles.muted}>正在整理今天的记录</Text> : null}
    {summary ? <View style={styles.section}>
      <Text style={styles.sectionTitle}>今天的一句话</Text>
      <Text style={styles.body}>{summary.oneLiner}</Text>
      {summary.highlight ? <Text style={styles.muted}>{summary.highlight}</Text> : null}
    </View> : null}
    {!!candidates.length ? <View style={styles.section}>
      <Text style={styles.sectionTitle}>待确认的记忆</Text>
      <Text style={styles.muted}>只有确认后，才会在以后聊天时使用。</Text>
      {candidates.map(item => <MemoryCandidateEditor key={item.id} candidate={item}
        busy={candidateBusy === item.id} disabled={candidateBusy !== null}
        onConfirm={text => void candidateAction(item.id, "confirm", text)}
        onReject={() => void candidateAction(item.id, "reject")} />)}
    </View> : null}
  </Page>;
}

function MemoryCandidateEditor({ candidate, busy, disabled, onConfirm, onReject }: {
  candidate: CheckinMemoryCandidate; busy: boolean; disabled: boolean;
  onConfirm: (text: string) => void; onReject: () => void;
}) {
  const [text, setText] = useState(candidate.text);
  return <View>
    <Separator />
    <Field label="来自今天的记录" multiline numberOfLines={3} value={text} onChangeText={setText}
      maxLength={512} />
    <View style={styles.row}>
      <Action label="确认记住" onPress={() => onConfirm(text.trim())} busy={busy}
        disabled={disabled || !text.trim()} />
      <Action label="不保存" variant="quiet" onPress={onReject} disabled={disabled} />
    </View>
  </View>;
}

const styles = StyleSheet.create({
  status: { marginTop: 20 }, section: { marginTop: 32, gap: 10 },
  sectionTitle: { color: colors.ink, fontSize: 19, fontWeight: "700" },
  body: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 12 },
  row: { flexDirection: "row", gap: 8, marginTop: 10, flexWrap: "wrap" },
});
