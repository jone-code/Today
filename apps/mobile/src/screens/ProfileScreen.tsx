import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Image, Pressable, StyleSheet, Switch, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { randomUUID } from "expo-crypto";
import { Camera, RotateCcw, LogOut, Save, MessageSquarePlus } from "lucide-react-native";
import type { AgentProfile, FollowupPreference } from "@today/contracts";
import { useSession } from "../App";
import type { RootStackParams } from "../App";
import { avatarDraftExists, avatarDraftKey, avatarDraftUri, deleteAvatarDraft, pruneAvatarDrafts,
  readAvatarDraft, saveAvatarDraft, writeAvatarDraft } from "../avatarDraft";
import { conversationDataNotice, followupDataNotice } from "../dataNotice";
import { colors } from "../theme";
import { Action, Avatar, Field, Heading, Notice, Page, Separator, messageOf } from "../ui";

export function ProfileScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const { api, session, signOut } = useSession();
  const draftKey = avatarDraftKey(session.baseUrl, session.auth.user.id);
  const draft = useRef<string | null>(null);
  const restoreRevision = useRef(0);
  const [restoredKey, setRestoredKey] = useState("");
  const [draftError, setDraftError] = useState(false);
  const [draftMissing, setDraftMissing] = useState(false);
  const [profile, setProfile] = useState<AgentProfile | null>(null);
  const [followup, setFollowup] = useState<FollowupPreference | null>(null);
  const [name, setName] = useState("");
  const [tone, setTone] = useState("");
  const [timezone, setTimezone] = useState("");
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

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
    void Promise.all([api.profile(), api.followupPreferences()]).then(([value, pref]) => {
      setProfile(value); setName(value.name); setTone(value.tone);
      setFollowup(pref); setTimezone(pref.timezone);
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
      setFollowup(await api.updateFollowupPreferences(followup.enabled, followup.maxPerWeek, timezone.trim()));
      setSuccess("跟进设置已保存"); setError("");
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
      {activePreview ? <Image accessibilityLabel="待上传的形象预览" source={{ uri: activePreview }} style={styles.avatar} /> :
        <Avatar size={96} uri={currentProfile?.avatarMediaId ? api.avatarUrl() + `?v=${currentProfile.version}` : null}
          token={session.auth.token} />}
      <View style={styles.previewText}><Text style={styles.name}>{name || "Today"}</Text>
        <Text style={styles.muted}>{activePreview ? "尚未保存的新形象" : "你的个人助手"}</Text></View>
    </View>
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
    <Text style={styles.muted}>{conversationDataNotice}</Text>
    <Text style={styles.muted}>{followupDataNotice}</Text>
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
  preview: { flexDirection: "row", alignItems: "center", gap: 18, marginBottom: 20 },
  avatar: { width: 96, height: 96, borderRadius: 48 },
  previewText: { flex: 1 },
  name: { color: colors.ink, fontSize: 21, fontWeight: "700" },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 22, marginTop: 4 },
  row: { flexDirection: "row", gap: 10, flexWrap: "wrap", marginTop: 6 },
  sectionTitle: { fontSize: 19, fontWeight: "700", color: colors.ink, marginBottom: 14 },
  label: { color: colors.ink, fontSize: 15, fontWeight: "600" },
  switchRow: { minHeight: 56, flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  frequency: { flexDirection: "row", gap: 8, marginTop: 12, marginBottom: 18 },
  segment: { minWidth: 70, minHeight: 48, paddingHorizontal: 14, alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, borderRadius: 7 },
  selectedSegment: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  segmentText: { color: colors.muted, fontSize: 14, fontWeight: "600" },
  selectedText: { color: colors.primary },
});
