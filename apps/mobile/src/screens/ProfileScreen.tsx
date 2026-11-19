import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Image, Platform, Pressable, StyleSheet, Switch, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import * as FileSystem from "expo-file-system";
import * as Sharing from "expo-sharing";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { randomUUID } from "expo-crypto";
import { Camera, RotateCcw, LogOut, Save, MessageSquarePlus } from "lucide-react-native";
import type { AgentProfile, FollowupPreference, PrivacySettings, DataRequest } from "@today/contracts";
import { useSession } from "../session";
import type { RootStackParams } from "../navigation";
import { avatarDraftExists, avatarDraftKey, avatarDraftUri, deleteAvatarDraft, pruneAvatarDrafts,
  readAvatarDraft, saveAvatarDraft, writeAvatarDraft } from "../avatarDraft";
import { conversationDataNotice, followupDataNotice, privacyDataNotice } from "../dataNotice";
import { colors } from "../theme";
import { ApiRequestError } from "../api";
import { Action, Avatar, Field, Heading, Notice, Page, Separator, messageOf } from "../ui";

export function ProfileScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const { api, session, signOut, onDeletionStarted } = useSession();
  const draftKey = avatarDraftKey(session.baseUrl, session.auth.user.id);
  const draft = useRef<string | null>(null);
  const restoreRevision = useRef(0);
  const [restoredKey, setRestoredKey] = useState("");
  const [draftError, setDraftError] = useState(false);
  const [draftMissing, setDraftMissing] = useState(false);
  const [profile, setProfile] = useState<AgentProfile | null>(null);
  const [followup, setFollowup] = useState<FollowupPreference | null>(null);
  const [privacy, setPrivacy] = useState<PrivacySettings | null>(null);
  const [name, setName] = useState("");
  const [tone, setTone] = useState("");
  const [timezone, setTimezone] = useState("");
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [tasks, setTasks] = useState<DataRequest[]>([]);
  const [deleteConfirmation, setDeleteConfirmation] = useState(false);
  const [deletePhrase, setDeletePhrase] = useState("");

  const refreshTasks = useCallback(async () => {
    const value = await api.dataRequests();
    setTasks(value);
    if (value.some(task => task.type === "delete_account" && task.status === "succeeded")) await signOut();
  }, [api, signOut]);
  useFocusEffect(useCallback(() => {
    void refreshTasks().catch(failure => setError(messageOf(failure)));
    const timer = setInterval(() => void refreshTasks().catch(failure => setError(messageOf(failure))), 5000);
    return () => clearInterval(timer);
  }, [refreshTasks]));

  const restoreDraft = useCallback(async () => {
    const revision = ++restoreRevision.current;
    setRestoredKey("");
    setDraftError(false);
    try {
      const id = await readAvatarDraft(draftKey);
      const exists = id ? await avatarDraftExists(draftKey, id) : false;
      const uri = id && exists ? await avatarDraftUri(draftKey, id) : null;
      await pruneAvatarDrafts(draftKey, id || undefined).catch(() => {});
      if (revision !== restoreRevision.current) return;
      draft.current = id;
      setPreview(uri);
      setDraftMissing(!!id && !exists);
      setRestoredKey(draftKey);
      setDraftError(false);
      if (id && !exists) setError("本机待上传图片已丢失，请取消草稿并重新选择。");
    } catch (failure) {
      if (revision === restoreRevision.current) { setDraftError(true); setError(messageOf(failure)); }
    }
  }, [draftKey]);
  useEffect(() => {
    void restoreDraft();
    return () => { restoreRevision.current++; };
  }, [restoreDraft]);

  async function clearDraft() {
    const previous = draft.current;
    await writeAvatarDraft(draftKey, null);
    draft.current = null;
    setPreview(null);
    setDraftMissing(false);
    if (previous) await deleteAvatarDraft(draftKey, previous).catch(() => {});
  }

  useFocusEffect(useCallback(() => {
    void Promise.all([api.profile(), api.followupPreferences(), api.privacy()]).then(([value, pref, privacyValue]) => {
      setProfile(value); setName(value.name); setTone(value.tone);
      setFollowup(pref); setTimezone(pref.timezone);
      setPrivacy(privacyValue);
    })
      .catch(failure => setError(messageOf(failure)));
  }, [api]));

  async function chooseImage() {
    if (busy || restoredKey !== draftKey) return;
    setBusy(true);
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) { setError("需要允许访问相册，才能选择形象图片"); return; }
      const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images"],
        allowsEditing: true, aspect: [1, 1], quality: 0.9 });
      if (picked.canceled || !picked.assets.length) return;
      const asset = picked.assets[0];
      const side = Math.min(asset.width, asset.height);
      const encoded = await ImageManipulator.manipulateAsync(asset.uri,
        [{ crop: { originX: (asset.width - side) / 2, originY: (asset.height - side) / 2, width: side, height: side } },
          { resize: { width: 640, height: 640 } }],
        { compress: 0.82, format: ImageManipulator.SaveFormat.JPEG });
      const requestId = randomUUID();
      const previous = draft.current;
      const uri = await saveAvatarDraft(draftKey, requestId, encoded.uri);
      try { await writeAvatarDraft(draftKey, requestId); }
      catch (failure) { await deleteAvatarDraft(draftKey, requestId).catch(() => {}); throw failure; }
      draft.current = requestId;
      setPreview(uri); setDraftMissing(false); setError(""); setSuccess("");
      if (previous) await deleteAvatarDraft(draftKey, previous).catch(() => {});
    } catch (failure) { setError(`无法保存形象草稿：${messageOf(failure)}。请检查设备存储后重试。`); }
    finally { setBusy(false); }
  }

  async function upload() {
    if (!preview || !draft.current || busy || restoredKey !== draftKey) return;
    setBusy(true);
    try {
      if (!await avatarDraftExists(draftKey, draft.current)) {
        setDraftMissing(true);
        throw new Error("待上传图片已丢失，请取消草稿并重新选择。");
      }
      await api.uploadAvatar(preview, draft.current);
      setProfile(await api.profile());
      await clearDraft();
      setSuccess("形象已更新"); setError("");
    }
    catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function cancelDraft() {
    if (busy) return;
    setBusy(true);
    try { await clearDraft(); setError(""); }
    catch (failure) { setError(`无法清除本机草稿：${messageOf(failure)}。请检查设备存储后重试。`); }
    finally { setBusy(false); }
  }

  async function discardDamagedDraft() {
    if (busy) return;
    setBusy(true);
    try {
      await writeAvatarDraft(draftKey, null);
      await pruneAvatarDrafts(draftKey).catch(() => {});
      draft.current = null;
      setPreview(null); setDraftMissing(false); setRestoredKey(draftKey);
      setDraftError(false); setError("");
    } catch (failure) { setError(`无法清除本机草稿：${messageOf(failure)}。请检查设备存储后重试。`); }
    finally { setBusy(false); }
  }

  async function save() {
    if (!name.trim() || !tone.trim()) { setError("请填写称呼和交流偏好"); return; }
    setBusy(true);
    try { setProfile(await api.updateProfile(name.trim(), tone.trim())); setSuccess("设置已保存"); setError(""); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function reset() {
    if (busy || restoredKey !== draftKey) return;
    setBusy(true);
    try { await clearDraft(); setProfile(await api.resetAvatar()); setSuccess("已恢复默认形象"); setError(""); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function saveFollowup() {
    if (!followup) return;
    setBusy(true);
    try {
      const updated = await api.updateFollowupPreferences(followup.enabled, followup.maxPerWeek, timezone.trim());
      setFollowup(updated);
      setPrivacy(previous => previous ? { ...previous, followupEnabled: updated.enabled } : previous);
      setSuccess("跟进设置已保存"); setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function savePrivacy(chatMemoryEnabled: boolean) {
    if (!privacy || busy) return;
    setBusy(true);
    try {
      const updated = await api.updatePrivacy({ version: privacy.version, chatMemoryEnabled,
        voiceRetention: privacy.voiceRetention, followupEnabled: followup?.enabled ?? privacy.followupEnabled });
      setPrivacy(updated);
      setSuccess("隐私设置已保存"); setError("");
    } catch (failure) {
      setError(messageOf(failure));
      if (failure instanceof ApiRequestError && failure.status === 409) {
        try { setPrivacy(await api.privacy()); }
        catch { setError("无法读取最新隐私设置，请重新进入此页后再试。"); }
      }
    }
    finally { setBusy(false); }
  }

  async function exportData() {
    setBusy(true);
    try {
      await api.requestDataExport(randomUUID());
      await refreshTasks();
      setSuccess("导出任务已创建，完成后可在下方下载。文件保留 24 小时。"); setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function downloadData(task: DataRequest) {
    setBusy(true);
    let uri: string | null = null;
    try {
      if (Platform.OS !== "android" && !await Sharing.isAvailableAsync()) throw new Error("此设备暂不支持保存或分享文件");
      const { token } = await api.dataDownloadToken(task.id);
      uri = `${FileSystem.cacheDirectory}today-export-${task.id}.zip`;
      const result = await FileSystem.downloadAsync(
        `${api.baseUrl}/v1/data-requests/${encodeURIComponent(task.id)}/download?token=${encodeURIComponent(token)}`,
        uri, { headers: { Authorization: `Bearer ${session.auth.token}` } });
      if (result.status !== 200) throw new Error("下载失败，请重试获取新的下载凭证");
      if (Platform.OS === "android") {
        const folder = await FileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync();
        if (!folder.granted) { setSuccess("已取消保存，可以再次下载。"); return; }
        const target = await FileSystem.StorageAccessFramework.createFileAsync(folder.directoryUri, `today-export-${task.id}.zip`, "application/zip");
        const data = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.Base64 });
        await FileSystem.writeAsStringAsync(target, data, { encoding: FileSystem.EncodingType.Base64 });
        setSuccess("导出包已保存到你选择的文件夹。");
      } else {
        await Sharing.shareAsync(uri, { mimeType: "application/zip", UTI: "public.zip-archive", dialogTitle: "保存我的 Today 数据" });
        setSuccess("导出包已下载，请在系统面板中保存。");
      }
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally {
      if (uri) await FileSystem.deleteAsync(uri, { idempotent: true }).catch(() => {});
      setBusy(false);
    }
  }

  async function retryTask(task: DataRequest) {
    setBusy(true);
    try { await api.retryDataRequest(task.id); await refreshTasks(); setError(""); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  async function deleteAccount() {
    if (deletePhrase !== "DELETE MY ACCOUNT" || busy) return;
    setBusy(true);
    try {
      await api.deleteAccount(deletePhrase);
      onDeletionStarted();
      setDeleteConfirmation(false); setDeletePhrase("");
      setSuccess("删除任务已提交，账户已停止普通访问。请等待清理完成；失败时可在下方重试。");
      await refreshTasks(); setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }

  const ready = restoredKey === draftKey;
  const activePreview = ready ? preview : null;
  const currentProfile = profile?.userId === session.auth.user.id ? profile : null;
  return <Page><Heading title="形象与设置" />
    {error ? <Notice danger>{error}</Notice> : null}
    {success ? <Notice>{success}</Notice> : null}
    <View style={styles.preview}>
      {activePreview ? <Image accessibilityLabel="待上传的形象预览" source={{ uri: activePreview }} style={styles.avatar as any} /> :
        <Avatar size={96} uri={currentProfile?.avatarMediaId ? api.avatarUrl() + `?v=${currentProfile.version}` : null}
          token={session.auth.token} />}
      <View style={styles.previewText}><Text style={styles.name}>{name || "Today"}</Text>
        <Text style={styles.muted}>{activePreview ? "尚未保存的新形象" : "你的个人助手"}</Text></View>
    </View>
    {privacy ? <View style={styles.privacyCard}>
      <Text style={styles.sectionTitle}>隐私与记忆</Text>
      <Text style={styles.body}>关闭后，Chat 不会读取任何长期记忆；已有记忆仍保留在回看页。</Text>
      <Text style={styles.body}>语音保留策略：成功转写后立即清理音频；失败任务只在短期重试期间保留。</Text>
      <View style={styles.privacyRow}><Text style={styles.body}>允许 Chat 使用长期记忆</Text>
        <Switch accessibilityLabel="允许 Chat 使用长期记忆" accessibilityState={{ checked: privacy.chatMemoryEnabled }}
          value={privacy.chatMemoryEnabled} onValueChange={value => void savePrivacy(value)} disabled={busy} />
      </View>
    </View> : null}
    <Action label="从相册选择" icon={Camera} variant="quiet" onPress={() => void chooseImage()}
      disabled={busy || restoredKey !== draftKey} />
    {restoredKey !== draftKey ? <Action label="重新读取本机形象草稿" variant="quiet"
      onPress={() => void restoreDraft()} disabled={busy} /> : null}
    {draftError ? <Action label="清除损坏的本机草稿" variant="danger"
      onPress={() => Alert.alert("清除本机形象草稿？", "未上传的图片和重试记录会丢失。", [
        { text: "保留", style: "cancel" },
        { text: "清除", style: "destructive", onPress: () => void discardDamagedDraft() },
      ])} disabled={busy} /> : null}
    {ready && draftMissing ? <Action label="取消丢失的草稿" variant="quiet" onPress={() => void cancelDraft()}
      disabled={busy} /> : null}
    {activePreview ? <View style={styles.row}>
      <Action label="使用这张图片" onPress={() => void upload()} busy={busy} />
      <Action label="取消" variant="quiet" onPress={() => void cancelDraft()} disabled={busy} />
    </View> : null}
    {currentProfile?.avatarMediaId ? <Action label="恢复默认形象" icon={RotateCcw} variant="quiet"
      onPress={() => void reset()} disabled={busy} /> : null}
    <Separator />
    <Field label="助手的称呼" value={name} onChangeText={setName} maxLength={64} />
    <Field label="交流偏好" value={tone} onChangeText={setTone} maxLength={64}
      placeholder="例如：简短、温和，少一点追问" />
    <Action label="保存设置" icon={Save} onPress={() => void save()} busy={busy}
      disabled={!name.trim() || !tone.trim() || (name === profile?.name && tone === profile?.tone)} />
    <Separator />
    <Text style={styles.sectionTitle}>站内跟进</Text>
    <Text style={styles.body}>跟进授权独立于 Chat 记忆设置。关闭 Chat 记忆不会自动停止已授权的站内跟进。</Text>
    <View style={styles.switchRow}><Text style={styles.label}>允许普通跟进</Text>
      <Switch accessibilityLabel="允许普通跟进" value={followup?.enabled || false} disabled={!followup}
        onValueChange={enabled => setFollowup(previous => previous ? { ...previous, enabled } : null)}
        trackColor={{ true: colors.primary, false: colors.line }} />
    </View>
    {followup?.enabled ? <>
      <Text style={styles.label}>每周最多</Text>
      <View style={styles.frequency}>
        {[1, 2, 3].map(count => <Pressable key={count} accessibilityRole="button"
          accessibilityState={{ selected: followup.maxPerWeek === count }} accessibilityLabel={`每周最多${count}次`}
          onPress={() => setFollowup(previous => previous ? { ...previous, maxPerWeek: count } : null)}
          style={({ pressed }) => [styles.segment, followup.maxPerWeek === count && styles.selectedSegment,
            pressed && { opacity: 0.7 }]}>
          <Text style={[styles.segmentText, followup.maxPerWeek === count && styles.selectedText]}>{count} 次</Text>
        </Pressable>)}
      </View>
      <Field label="跟进时区" value={timezone} onChangeText={setTimezone}
        autoCapitalize="none" placeholder="Asia/Shanghai" />
    </> : null}
    {followup ? <Action label="保存跟进设置" onPress={() => void saveFollowup()} busy={busy} variant="quiet" /> : null}
    <Separator />
    <Text style={styles.sectionTitle}>对话与数据</Text>
    <Action label="导出我的数据" variant="quiet" disabled={busy} onPress={() => void exportData()} />
    {tasks.map(task => <View key={task.id} style={styles.privacyCard}>
      <Text style={styles.sectionTitle}>{task.type === "export" ? "数据导出" : "账户删除"}</Text>
      <Text style={styles.body}>{({ queued: "准备中", running: "处理中", succeeded: "已完成", failed: "处理失败", expired: "已过期", cancelled: "已取消" } as Record<string,string>)[task.status] || task.status}</Text>
      {task.type === "export" && task.status === "succeeded" ? <Action label="下载并保存导出包" variant="quiet" disabled={busy} onPress={() => void downloadData(task)} /> : null}
      {task.retryable ? <><Text style={styles.muted}>可重试当前任务，不会重复创建。删除任务重试会继续清理剩余数据。</Text>
        <Action label="重试任务" variant="quiet" disabled={busy} onPress={() => void retryTask(task)} /></> : null}
    </View>)}
    <Action label="永久删除账户" variant="danger" disabled={busy || tasks.some(task => task.type === "delete_account")} onPress={() => setDeleteConfirmation(true)} />
    {deleteConfirmation ? <View style={styles.privacyCard}>
      <Text style={styles.body}>此操作不可撤销，将删除记录、记忆、对话、媒体及导出包。请输入 DELETE MY ACCOUNT 确认。</Text>
      <Field label="删除确认短语" value={deletePhrase} onChangeText={setDeletePhrase} autoCapitalize="none" autoCorrect={false} />
      <Action label="确认永久删除" variant="danger" disabled={busy || deletePhrase !== "DELETE MY ACCOUNT"} onPress={() => void deleteAccount()} />
      <Action label="取消删除" variant="quiet" disabled={busy} onPress={() => { setDeleteConfirmation(false); setDeletePhrase(""); }} />
    </View> : null}
    <Text style={styles.muted}>{conversationDataNotice}</Text>
    <Text style={styles.muted}>{followupDataNotice}</Text>
    <Text style={styles.muted}>{privacyDataNotice}</Text>
    <Separator />
    <Action label="问题与建议" icon={MessageSquarePlus} variant="quiet"
      onPress={() => navigation.navigate("Feedback")} />
    <Separator />
    <Text style={styles.muted}>{session.auth.user.email}</Text>
    <Action label="退出登录" icon={LogOut} variant="danger" onPress={() => Alert.alert("退出登录？", undefined,
      [{ text: "取消", style: "cancel" }, { text: "退出", onPress: () => void signOut() }])} />
  </Page>;
}

const styles = StyleSheet.create({
  preview: { flexDirection: "row", alignItems: "center", gap: 18, marginBottom: 22, padding: 18,
    backgroundColor: colors.primaryDeep, borderRadius: 22 },
  avatar: { width: 96, height: 96, borderRadius: 48, borderWidth: 3, borderColor: colors.primarySoft },
  previewText: { flex: 1 },
  name: { color: colors.onPrimary, fontSize: 21, fontWeight: "800" },
  muted: { color: colors.onPrimaryMuted, fontSize: 14, lineHeight: 22, marginTop: 4 },
  row: { flexDirection: "row", gap: 10, flexWrap: "wrap", marginTop: 6 },
  sectionTitle: { fontSize: 19, fontWeight: "800", color: colors.ink, marginBottom: 14 },
  privacyCard: { padding: 16, marginBottom: 20, borderRadius: 18, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line },
  privacyRow: { minHeight: 56, flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 12 },
  body: { color: colors.muted, fontSize: 14, lineHeight: 22, marginBottom: 8 },
  label: { color: colors.inkSoft, fontSize: 15, fontWeight: "700" },
  switchRow: { minHeight: 56, flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  frequency: { flexDirection: "row", gap: 8, marginTop: 12, marginBottom: 18 },
  segment: { minWidth: 70, minHeight: 46, paddingHorizontal: 14, alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, borderRadius: 999 },
  selectedSegment: { backgroundColor: colors.primaryDeep, borderColor: colors.primaryDeep },
  segmentText: { color: colors.muted, fontSize: 14, fontWeight: "700" },
  selectedText: { color: colors.onPrimary },
});
