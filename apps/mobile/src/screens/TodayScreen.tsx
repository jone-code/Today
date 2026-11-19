import { useCallback, useRef, useState } from "react";
import { Alert, StyleSheet, Text, View } from "react-native";
import {
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
} from "expo-audio";
import * as FileSystem from "expo-file-system";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { ArrowRight, RefreshCw, Sparkles, Clock3, BrainCircuit, Mic, Square } from "lucide-react-native";
import { randomUUID } from "expo-crypto";
import type { CheckinMemoryCandidate, DaySummaryDto } from "@today/contracts";
import { useSession } from "../session";
import type { RootStackParams } from "../navigation";
import { checkinDraftKey, readCheckinDraft, writeCheckinDraft, type CheckinDraft } from "../checkinDraft";
import { ApiRequestError } from "../api";
import { colors } from "../theme";
import { Action, Field, Heading, Notice, Page, Pill, SectionLabel, Separator, Surface, IconAction, messageOf } from "../ui";

export function TodayScreen() {
  const { api, session } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const draftKey = checkinDraftKey(session.baseUrl, session.auth.user.id);
  const edited = useRef(false);
  const summaryTracked = useRef<string | null>(null);
  const draft = useRef<CheckinDraft | null>(null);
  const draftWrite = useRef<Promise<void>>(Promise.resolve());
  const [draftReady, setDraftReady] = useState(false);
  const [checkinId, setCheckinId] = useState<string | null>(null);
  const [savedText, setSavedText] = useState("");
  const [text, setText] = useState("");
  const [date, setDate] = useState("");
  const [jobStatus, setJobStatus] = useState("");
  const [summary, setSummary] = useState<DaySummaryDto | null>(null);
  const [home, setHome] = useState<Awaited<ReturnType<typeof api.todayHome>> | null>(null);
  const [candidates, setCandidates] = useState<CheckinMemoryCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [candidateBusy, setCandidateBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [draftError, setDraftError] = useState("");
  const [draftNotice, setDraftNotice] = useState("");
  const [recording, setRecording] = useState(false);
  const [voiceBusy, setVoiceBusy] = useState(false);
  const [voiceRetryJobId, setVoiceRetryJobId] = useState<string | null>(null);
  const recordingRef = useRef(false);
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recordingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const MAX_VOICE_BYTES = 10 * 1024 * 1024;

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
      const result = await api.todayHome();
      setHome(result);
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
        const pending = await api.checkinCandidates(result.checkin.id);
        setSummary(result.summary); setCandidates(pending);
        if (result.summary && summaryTracked.current !== result.summary.checkinId) {
          summaryTracked.current = result.summary.checkinId;
          void api.track([{ eventName: "summary_viewed", screen: "Today", entityType: "summary", entityId: result.summary.checkinId }]).catch(() => {});
        }
      } else { setSummary(null); setCandidates([]); }
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
  }, [api, draftKey]);

  useFocusEffect(useCallback(() => {
    void api.track([{ eventName: "today_viewed", screen: "Today" }]).catch(() => {});
    void refresh();
    const timer = setInterval(() => void refresh(true), 8000);
    return () => {
      clearInterval(timer);
      if (recordingTimer.current) {
        clearTimeout(recordingTimer.current);
        recordingTimer.current = null;
      }
      if (recordingRef.current) {
        recordingRef.current = false;
        setRecording(false);
        void recorder.stop().catch(() => {});
        void setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true }).catch(() => {});
      }
    };
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
      void api.track([{ eventName: "checkin_started", screen: "Today", entityType: "checkin", clientRequestId: `${stable.requestId}:started` }]).catch(() => {});
      await api.submitCheckin(stable.text, undefined, stable.requestId);
      void api.track([{ eventName: "checkin_saved", screen: "Today", entityType: "checkin", clientRequestId: `${stable.requestId}:saved` }]).catch(() => {});
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

  async function startVoiceRecording() {
    if (voiceBusy || recording) return;
    setError("");
    const permission = await requestRecordingPermissionsAsync();
    if (!permission.granted) {
      setError("需要麦克风权限才能语音记录；你也可以直接输入文字。");
      void api.track([{ eventName: "voice_recording_failed", screen: "Today",
        propertiesJson: JSON.stringify({ errorCode: "permission_denied" }) }]).catch(() => {});
      return;
    }
    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      recorder.record();
      recordingRef.current = true;
      setRecording(true);
      recordingTimer.current = setTimeout(() => {
        setError("录音已达到 120 秒上限，正在转写；你可以检查文字后再保存。");
        void stopVoiceRecording();
      }, 120_000);
      void api.track([{ eventName: "voice_recording_started", screen: "Today" }]).catch(() => {});
    } catch (failure) {
      setError(`无法开始录音：${messageOf(failure)}`);
      void api.track([{ eventName: "voice_recording_failed", screen: "Today",
        propertiesJson: JSON.stringify({ errorCode: "recording_start_failed" }) }]).catch(() => {});
    }
  }

  async function stopVoiceRecording() {
    if (!recordingRef.current && !recording) return;
    setVoiceBusy(true); setError("");
    recordingRef.current = false;
    if (recordingTimer.current) {
      clearTimeout(recordingTimer.current);
      recordingTimer.current = null;
    }
    setRecording(false);
    try {
      await recorder.stop();
      const status = recorder.getStatus();
      const uri = recorder.uri;
      if (!uri) throw new Error("录音文件不可用");
      const fileInfo = await FileSystem.getInfoAsync(uri);
      if (!fileInfo.exists || (fileInfo.size ?? 0) > MAX_VOICE_BYTES) {
        throw new Error("录音文件超过 10 MB 限制，请缩短录音后重试");
      }
      const job = await api.transcribeVoice(uri, status.durationMillis || 0, randomUUID());
      setVoiceRetryJobId(job.retryable ? job.id : null);
      if (job.transcript) {
        edit(job.transcript);
        setVoiceRetryJobId(null);
        void api.track([{ eventName: "voice_transcription_succeeded", screen: "Today", entityType: "voice_transcription", entityId: job.id }]).catch(() => {});
      } else {
        setError("语音转写服务尚未配置，请直接编辑文字记录。");
        void api.track([{ eventName: "voice_transcription_failed", screen: "Today", entityType: "voice_transcription", entityId: job.id,
          propertiesJson: JSON.stringify({ errorCode: job.errorCode || "unknown" }) }]).catch(() => {});
      }
      void api.track([{ eventName: "voice_transcription_requested", screen: "Today", entityType: "voice_transcription", entityId: job.id }]).catch(() => {});
    } catch (failure) {
      setError(`语音记录未完成：${messageOf(failure)}；你可以直接输入文字。`);
      void api.track([{ eventName: "voice_recording_failed", screen: "Today",
        propertiesJson: JSON.stringify({ errorCode: "recording_upload_failed" }) }]).catch(() => {});
    } finally {
      setVoiceBusy(false);
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true }).catch(() => {});
    }
  }

  async function retryVoiceTranscription() {
    if (!voiceRetryJobId || voiceBusy) return;
    setVoiceBusy(true); setError("");
    const jobId = voiceRetryJobId;
    void api.track([{ eventName: "voice_transcription_retried", screen: "Today", entityType: "voice_transcription", entityId: jobId }]).catch(() => {});
    try {
      const job = await api.retryVoiceTranscription(jobId);
      setVoiceRetryJobId(job.retryable ? job.id : null);
      if (job.transcript) {
        edit(job.transcript);
        setVoiceRetryJobId(null);
        void api.track([{ eventName: "voice_transcription_succeeded", screen: "Today", entityType: "voice_transcription", entityId: job.id }]).catch(() => {});
      } else {
        setError("语音转写仍未完成，你可以继续编辑文字记录。");
        void api.track([{ eventName: "voice_transcription_failed", screen: "Today", entityType: "voice_transcription", entityId: job.id,
          propertiesJson: JSON.stringify({ errorCode: job.errorCode || "unknown" }) }]).catch(() => {});
      }
    } catch (failure) {
      if (failure instanceof ApiRequestError && failure.status === 409) setVoiceRetryJobId(null);
      setError(`语音转写重试失败：${messageOf(failure)}；你可以直接输入文字。`);
    } finally {
      setVoiceBusy(false);
    }
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
    <Heading title={"今天，感觉\n怎么样？"} detail={date || "Tuesday · 留下一点今天的事"}
      action={<IconAction icon={ArrowRight} label="回看时间轴" onPress={() => navigation.navigate("Timeline")} />} />
    {error ? <Notice danger>{error}</Notice> : null}
    {draftNotice ? <Notice>{draftNotice}</Notice> : null}
    {draftError ? <Notice danger>{draftError}</Notice> : null}
    {!draftReady ? <View style={styles.row}>
      <Action label="重新读取" variant="quiet" onPress={() => void refresh()} />
      {draftError ? <Action label="清除本机草稿" variant="danger" onPress={confirmClearDraft} /> : null}
    </View> : null}
    <Surface tone="dark" style={styles.hero}>
      <View style={styles.heroTop}><Text style={styles.heroEyebrow}>给自己 30 秒</Text><Sparkles color={colors.primarySoft} size={18} /></View>
      <Text style={styles.heroTitle}>把今天留给{"\n"}明天的你。</Text>
      <Text style={styles.heroDetail}>好的、难的、普通的，都值得被记住。</Text>
    </Surface>
    <Surface style={styles.editor}>
      <View style={styles.editorTop}><Text style={styles.editorLabel}>今天的记录</Text><Clock3 color={colors.muted} size={17} /></View>
      <Field label="" multiline numberOfLines={6} value={text} onChangeText={edit} editable={!busy && draftReady}
        placeholder="今天发生了什么？" maxLength={2000} />
      <View style={styles.voiceRow}>
        <Action label={recording ? "结束录音" : "语音记录"} icon={recording ? Square : Mic}
          variant={recording ? "danger" : "quiet"} onPress={() => void (recording ? stopVoiceRecording() : startVoiceRecording())}
          busy={voiceBusy} disabled={!draftReady || busy} />
        {voiceRetryJobId ? <Action label="重试转写" variant="quiet" onPress={() => void retryVoiceTranscription()}
          busy={voiceBusy} disabled={!draftReady || busy} /> : null}
        <Text style={styles.voiceHint}>{recording ? "正在录音，最多 120 秒" : "转写后请检查文字，再保存"}</Text>
      </View>
      <Text style={styles.editorPrompt}>今天最想记住哪一刻？</Text>
    </Surface>
    {savedText && text.trim() !== savedText ? <Notice danger>保存会替换今天已有的记录，并使未确认的旧记忆建议失效。</Notice> : null}
    <Action label={savedText ? "保存修改" : "保存今天"} icon={Sparkles} onPress={confirmSave}
      busy={busy} disabled={!draftReady || !text.trim() || text.trim() === savedText} />
    {jobStatus === "failed" ? <View style={styles.status}>
      <Notice danger>这次整理未完成，原始记录已保存。</Notice>
      <Action label="重新整理" icon={RefreshCw} variant="quiet"
        onPress={() => void api.retryToday().then(() => refresh(true)).catch(e => setError(messageOf(e)))} />
    </View> : jobStatus === "running" || jobStatus === "pending" ?
      <Text style={styles.muted}>正在整理今天的记录</Text> : null}
    {summary ? <Surface tone="sage" style={styles.section}>
      <SectionLabel title="今天的一句话" action={<Pill tone="sage">AI 整理</Pill>} />
      <Text style={styles.body}>{summary.oneLiner}</Text>
      {summary.highlight ? <Text style={styles.muted}>{summary.highlight}</Text> : null}
    </Surface> : null}
    {home ? <Surface style={styles.section}>
      <SectionLabel title="今天的节奏" />
      <View style={styles.statsRow}>
        <Stat label="待办" value={home.openTodoCount} />
        <Stat label="习惯" value={`${home.habitsDone}/${home.habitsTotal}`} />
        <Stat label="未读" value={home.unreadInboxCount} />
      </View>
    </Surface> : null}
    {!!candidates.length ? <Surface tone="lavender" style={styles.section}>
      <SectionLabel title="待确认的记忆" action={<BrainCircuit color={colors.lavender} size={20} />} />
      <Text style={styles.muted}>只有确认后，才会在以后聊天时使用。</Text>
      {candidates.map(item => <MemoryCandidateEditor key={item.id} candidate={item}
        busy={candidateBusy === item.id} disabled={candidateBusy !== null}
        onConfirm={text => void candidateAction(item.id, "confirm", text)}
        onReject={() => void candidateAction(item.id, "reject")} />)}
    </Surface> : null}
  </Page>;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return <View style={styles.stat}><Text style={styles.statValue}>{value}</Text><Text style={styles.muted}>{label}</Text></View>;
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
  hero: { marginBottom: 16, padding: 18, minHeight: 155 },
  heroTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  heroEyebrow: { color: colors.primarySoft, fontSize: 11, fontWeight: "700", letterSpacing: 0.8, textTransform: "uppercase" },
  heroTitle: { color: colors.onPrimary, fontSize: 25, lineHeight: 28, fontWeight: "800", letterSpacing: -0.8, marginTop: 16 },
  heroDetail: { color: colors.onPrimaryMuted, fontSize: 13, lineHeight: 20, marginTop: 10 },
  editor: { padding: 16, marginBottom: 14 },
  editorTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  editorLabel: { color: colors.ink, fontSize: 15, fontWeight: "800" },
  editorPrompt: { color: colors.primary, fontSize: 12, fontWeight: "700", marginTop: -8 },
  status: { marginTop: 20 }, section: { marginTop: 22, gap: 10 },
  statsRow: { flexDirection: "row", justifyContent: "space-between", gap: 12 },
  stat: { flex: 1, minHeight: 64, borderRadius: 16, backgroundColor: colors.input, alignItems: "center", justifyContent: "center", gap: 3 },
  statValue: { color: colors.ink, fontSize: 20, fontWeight: "800" },
  body: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21, marginTop: 12 },
  row: { flexDirection: "row", gap: 8, marginTop: 10, flexWrap: "wrap" },
  voiceRow: { flexDirection: "row", alignItems: "center", gap: 10, marginTop: 8, flexWrap: "wrap" },
  voiceHint: { color: colors.muted, fontSize: 12, flex: 1, minWidth: 180 },
});
