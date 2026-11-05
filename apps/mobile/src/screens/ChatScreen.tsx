import { memo, useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Alert, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { useFocusEffect, useNavigation, useRoute } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { History, Plus, Send, Settings2 } from "lucide-react-native";
import { randomUUID } from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import type { AgentProposal, Conversation, ConversationMessage, AgentProfile } from "@today/contracts";
import { ApiRequestError } from "../api";
import { appendOlderMessages, mergeConversationHistory } from "../chatHistory";
import { readChatDraft, writeChatDraft } from "../chatDraft";
import { conversationDataNotice, followupDataNotice } from "../dataNotice";
import { useSession, type RootStackParams } from "../App";
import { deleteSecureDraft } from "../secureDraft";
import { colors, layout } from "../theme";
import { Action, Avatar, Empty, IconAction, Notice, messageOf } from "../ui";

export function ChatScreen() {
  const { api, session } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const route = useRoute();
  const routeId = route.name === "Conversation" ? (route.params as { id: string }).id : null;
  const [current, setCurrent] = useState<Conversation | null>(null);
  const [profile, setProfile] = useState<AgentProfile | null>(null);
  const [proposals, setProposals] = useState<AgentProposal[]>([]);
  const [draft, setDraft] = useState("");
  const [retryId, setRetryId] = useState<string | null>(null);
  const [draftReady, setDraftReady] = useState(false);
  const [draftError, setDraftError] = useState(false);
  const [draftErrorMessage, setDraftErrorMessage] = useState("");
  const [reconcilingDraft, setReconcilingDraft] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [error, setError] = useState("");
  const [actionFeedback, setActionFeedback] = useState("");
  const [proposalBusy, setProposalBusy] = useState<string | null>(null);
  const [introReady, setIntroReady] = useState(false);
  const [introSeen, setIntroSeen] = useState(false);
  const [introBusy, setIntroBusy] = useState(false);
  const scroll = useRef<FlatList<ConversationMessage>>(null);
  const loadedConversationId = useRef<string | null>(null);
  const refreshRevision = useRef(0);
  const moreInFlight = useRef(false);
  const newestMessageId = useRef<string | null>(null);
  const draftWrite = useRef<Promise<void>>(Promise.resolve());
  const draftRevision = useRef(0);
  const succeededClearInFlight = useRef<string | null>(null);
  const activeDraftKey = useRef("");
  const newConversationRequestId = useRef<string | null>(null);
  const creationInFlight = useRef(false);
  const [creatingConversation, setCreatingConversation] = useState(false);
  const storageScope = [session.baseUrl, session.auth.user.id]
    .map(value => encodeURIComponent(value).replace(/_/g, "_5F").replace(/%/g, "_"))
    .join("-");
  const draftKey = `today-draft-v2-${storageScope}-${routeId || "latest"}`;
  activeDraftKey.current = draftKey;
  const introKey = `today-chat-intro-v1-${storageScope}`;

  useEffect(() => {
    setDraftReady(false); setDraftError(false); setDraftErrorMessage("");
    setDraft(""); setRetryId(null);
    setCurrent(null); setProfile(null); setProposals([]);
    loadedConversationId.current = null;
  }, [draftKey]);

  useEffect(() => {
    let active = true;
    void SecureStore.getItemAsync(introKey)
      .then(value => { if (active) setIntroSeen(value === "seen"); })
      .catch(() => {})
      .finally(() => { if (active) setIntroReady(true); });
    return () => { active = false; };
  }, [introKey]);

  async function acknowledgeIntro() {
    setIntroBusy(true);
    try {
      await SecureStore.setItemAsync(introKey, "seen");
      setError("");
    } catch {
      setError("本次可以继续对话，但暂时无法保存已读状态，下次打开可能再次看到这条说明。");
    } finally {
      setIntroSeen(true);
      setIntroBusy(false);
    }
  }

  function saveDraft(text: string, id: string | null, conversationId: string | null) {
    draftWrite.current = draftWrite.current.catch(() => {}).then(() =>
      writeChatDraft(draftKey, { text, id, conversationId }));
    return draftWrite.current;
  }

  function clearDraft() {
    draftRevision.current++;
    draftWrite.current = draftWrite.current.catch(() => {}).then(() => deleteSecureDraft(draftKey));
    return draftWrite.current;
  }

  const refresh = useCallback(async () => {
    const revision = draftRevision.current;
    const refreshId = refreshRevision.current;
    let draftFailed = false;
    let pendingDraft = null as Awaited<ReturnType<typeof readChatDraft>>;
    try {
      await draftWrite.current;
      pendingDraft = await readChatDraft(draftKey);
      if (refreshId === refreshRevision.current) {
        setDraftReady(true); setDraftError(false); setDraftErrorMessage("");
        if (pendingDraft && revision === draftRevision.current) {
          setDraft(pendingDraft.text); setRetryId(pendingDraft.id);
        }
      }
    } catch (failure) {
      draftFailed = true;
      if (refreshId === refreshRevision.current) {
        setDraftError(true);
        setDraftErrorMessage(`无法读取本机对话草稿：${messageOf(failure)}`);
      }
    }
    try {
      const [list, agent, pending] = await Promise.all([api.conversations(), api.profile(), api.proposals()]);
      const id = routeId || (pendingDraft?.id && pendingDraft.conversationId) || list[0]?.id;
      const conversation = id ? await api.conversation(id) : null;
      if (refreshId !== refreshRevision.current) return;
      if (loadedConversationId.current !== conversation?.id) {
        loadedConversationId.current = conversation?.id || null;
        setHasMore((conversation?.messages.length || 0) >= 50);
        setMoreError("");
      }
      setCurrent(previous => mergeConversationHistory(previous, conversation));
      setProfile(agent);
      setProposals(conversation ? pending.filter(p => conversation.messages.some(m => m.id === p.sourceMessageId)) : []);
      if (!draftFailed) setError("");
    } catch (failure) {
      if (refreshId === refreshRevision.current) {
        if (failure instanceof ApiRequestError && failure.status === 404) {
          loadedConversationId.current = null;
          setCurrent(null); setProposals([]); setHasMore(false);
          setError("这段对话已删除，可新建对话继续。");
        } else if (!draftFailed) {
          setError(messageOf(failure));
        }
      }
    } finally { setLoading(false); }
  }, [api, routeId, draftKey]);

  async function loadMore() {
    const oldest = current?.messages[current.messages.length - 1];
    if (!current || !oldest || !hasMore || moreInFlight.current) return;
    const conversationId = current.id;
    moreInFlight.current = true;
    setLoadingMore(true);
    setMoreError("");
    try {
      const page = await api.conversationMessages(conversationId, oldest.createdAt, oldest.id);
      if (loadedConversationId.current !== conversationId) return;
      setCurrent(previous => previous?.id === conversationId
        ? appendOlderMessages(previous, page.messages) : previous);
      setHasMore(page.hasMore);
    } catch (failure) {
      if (loadedConversationId.current === conversationId) {
        if (failure instanceof ApiRequestError && failure.status === 404) {
          loadedConversationId.current = null;
          setCurrent(null); setProposals([]); setHasMore(false);
          setError("这段对话已删除，可新建对话继续。");
        } else {
          setMoreError(messageOf(failure));
        }
      }
    } finally {
      moreInFlight.current = false;
      setLoadingMore(false);
    }
  }

  useFocusEffect(useCallback(() => {
    setLoading(true);
    void refresh();
    return () => { refreshRevision.current++; };
  }, [refresh]));

  useEffect(() => {
    if (!current?.messages.some(message => message.status === "processing")) return;
    let active = true;
    const timer = setInterval(() => {
      void Promise.all([api.conversation(current.id), api.proposals()])
        .then(([updated, pending]) => {
          if (!active || loadedConversationId.current !== updated.id) return;
          setCurrent(previous => previous?.id === updated.id
            ? mergeConversationHistory(previous, updated) : previous);
          setProposals(pending.filter(proposal =>
            updated.messages.some(message => message.id === proposal.sourceMessageId)));
          setError("");
        })
        .catch(failure => {
          if (active && loadedConversationId.current === current.id) setError(messageOf(failure));
        });
    }, 8000);
    return () => { active = false; clearInterval(timer); };
  }, [api, current]);

  useEffect(() => {
    const latest = current?.messages[0]?.id || null;
    if (latest && newestMessageId.current && latest !== newestMessageId.current) {
      scroll.current?.scrollToOffset({ offset: 0, animated: true });
    }
    newestMessageId.current = latest;
  }, [current?.messages[0]?.id]);

  useEffect(() => {
    if (!retryId || !current?.messages.some(message =>
      message.clientMessageId === retryId && message.status === "succeeded")) return;
    const key = `${draftKey}:${retryId}`;
    if (succeededClearInFlight.current === key) return;
    succeededClearInFlight.current = key;
    setReconcilingDraft(true);
    void clearDraft().then(() => {
      if (activeDraftKey.current === draftKey) {
        setDraft(""); setRetryId(null);
        setDraftError(false); setDraftErrorMessage("");
      }
    }).catch(() => {
      if (activeDraftKey.current === draftKey) {
        setDraftError(true);
        setDraftErrorMessage("无法清除已发送的本机草稿，请检查设备存储后重试。");
      }
    }).finally(() => {
      if (succeededClearInFlight.current === key) succeededClearInFlight.current = null;
      if (activeDraftKey.current === draftKey) setReconcilingDraft(false);
    });
    void api.proposals().then(pending => setProposals(pending.filter(proposal =>
      current.messages.some(message => message.id === proposal.sourceMessageId)))).catch(() => {});
  }, [api, current, draftKey, retryId]);

  function edit(text: string) {
    if (succeededClearInFlight.current) return;
    draftRevision.current++;
    setDraft(text);
    setRetryId(null);
    void saveDraft(text, null, null).then(() => {
      setDraftError(false); setDraftErrorMessage("");
    }, () => {
      setDraftError(true);
      setDraftErrorMessage("对话草稿未能保存在本机，请检查设备存储后重新保存。");
    });
  }

  async function retryDraftSave() {
    try {
      if (retryId && current?.messages.some(message =>
        message.clientMessageId === retryId && message.status === "succeeded")) {
        await clearDraft();
        setDraft(""); setRetryId(null);
      } else {
        await saveDraft(draft, retryId, retryId ? current?.id || null : null);
      }
      setDraftError(false); setDraftErrorMessage(""); setDraftReady(true); setError("");
    } catch (failure) { setDraftErrorMessage(messageOf(failure)); }
  }

  async function discardDraft() {
    try {
      refreshRevision.current++;
      await clearDraft();
      setDraft(""); setRetryId(null);
      setDraftError(false); setDraftErrorMessage(""); setDraftReady(true); setError("");
    } catch (failure) { setDraftErrorMessage(messageOf(failure)); }
  }

  async function send() {
    const body = draft.trim();
    if (!introReady || !introSeen || !draftReady || draftError || reconcilingDraft || !body || busy || creatingConversation || failedMessage ||
        current?.messages.some(message => message.status === "processing")) return;
    setBusy(true); setError("");
    if (!current) refreshRevision.current++;
    const id = retryId || randomUUID();
    setRetryId(id);
    let conversationId = current?.id;
    try {
      await saveDraft(body, id, conversationId || null);
      setDraftError(false); setDraftErrorMessage("");
      const conversation = current || await api.createConversation(`first-message-${id}`);
      conversationId = conversation.id;
      if (!current) {
        loadedConversationId.current = conversation.id;
        setHasMore(false);
        setCurrent(conversation);
      }
      await saveDraft(body, id, conversation.id);
      const result = await api.send(conversation.id, body, id);
      const updated = await api.conversation(conversation.id);
      setCurrent(previous => mergeConversationHistory(previous, updated));
      setProposals(previous => [...previous, ...result.proposals.filter(p => p.status === "pending")]);
      await clearDraft();
      setDraft(""); setRetryId(null);
    } catch (failure) {
      if (draftWrite.current) {
        try { await draftWrite.current; }
        catch {
          setDraftError(true);
          setDraftErrorMessage("对话草稿未能保存在本机，请检查设备存储后重新保存。");
        }
      }
      setError(messageOf(failure));
      if (conversationId) {
        void api.conversation(conversationId).then(updated =>
          setCurrent(previous => mergeConversationHistory(previous, updated))).catch(() => {});
      }
    } finally { setBusy(false); }
  }

  async function retryFailed(messageId: string) {
    if (!introReady || !introSeen || !current || busy) return;
    setBusy(true); setError("");
    try {
      const result = await api.retryMessage(current.id, messageId);
      const updated = await api.conversation(current.id);
      setCurrent(previous => mergeConversationHistory(previous, updated));
      setProposals(previous => [...previous, ...result.proposals.filter(p => p.status === "pending")]);
      await clearDraft();
      setDraft(""); setRetryId(null);
    } catch (failure) {
      setError(messageOf(failure));
      void api.conversation(current.id).then(updated =>
        setCurrent(previous => mergeConversationHistory(previous, updated))).catch(() => {});
    } finally { setBusy(false); }
  }

  async function updateProposal(id: string, action: "confirm" | "reject") {
    if (proposalBusy) return;
    setProposalBusy(id);
    try {
      const updated = await api.proposal(id, action);
      setProposals(previous => previous.filter(p => p.id !== id));
      setActionFeedback(action === "reject" ? "已跳过这项建议" : ({
        checkin: "每日记录已保存", memory: "记忆已保存", followup: "后续关注已开启", todo: "待办已创建",
        todo_edit: "待办已更新",
        habit: "习惯已创建", habit_edit: "习惯已更新", habit_punch: "打卡已记录", habit_unpunch: "打卡已取消",
        daily_reminder: "每日提醒已设置", daily_reminder_edit: "每日提醒已更新",
        one_off_reminder: "单次约定已保存",
      } as Record<string, string>)[updated.kind] || "操作已完成");
      setError("");
    } catch (failure) {
      const kind = proposals.find(p => p.id === id)?.kind;
      setError(failure instanceof ApiRequestError && failure.status === 409
        ? kind === "checkin" ? "记录已有新修改，请重新获取预览后再确认。"
          : kind === "todo_edit" ? "待办已被修改，请跳过旧建议并重新说明要改的内容。"
          : kind === "habit_edit" || kind === "habit_punch" || kind === "habit_unpunch" ? "习惯或打卡记录已有变化，请跳过旧建议并重新说明。"
          : kind === "daily_reminder_edit" ? "提醒已被修改，请跳过旧建议并重新说明要改的内容。"
          : messageOf(failure) : messageOf(failure));
    }
    finally { setProposalBusy(null); }
  }

  async function updateCheckinPreview(id: string, date: string, text?: string) {
    if (proposalBusy) return;
    setProposalBusy(id);
    try {
      const updated = await api.updateCheckinPreview(id, date, text);
      setProposals(previous => previous.map(proposal => proposal.id === id ? updated : proposal));
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setProposalBusy(null); }
  }

  async function newConversation() {
    if (busy || creationInFlight.current || reconcilingDraft || draftError) return;
    creationInFlight.current = true;
    refreshRevision.current++;
    setCreatingConversation(true);
    try {
      const requestId = newConversationRequestId.current || randomUUID();
      newConversationRequestId.current = requestId;
      const created = await api.createConversation(`new-conversation-${requestId}`);
      loadedConversationId.current = created.id;
      setHasMore(false);
      setMoreError("");
      setCurrent(created); setProposals([]); setError("");
      await clearDraft();
      newConversationRequestId.current = null;
      setDraft(""); setRetryId(null);
      if (routeId) navigation.navigate("Tabs");
    } catch (failure) { setError(messageOf(failure)); }
    finally { creationInFlight.current = false; setCreatingConversation(false); }
  }

  const name = profile?.name || "Today";
  const failedMessage = current?.messages.find(message => message.role === "user" &&
    message.status === "failed" && (!draft.trim() || draft.trim() === message.body));
  const processing = current?.messages.some(message => message.status === "processing") || false;
  return <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === "ios" ? "padding" : undefined}
    keyboardVerticalOffset={routeId ? 84 : 0}>
    <View style={styles.header}>
      <Avatar size={46} uri={profile?.avatarMediaId ? api.avatarUrl() + `?v=${profile.version}` : null} token={session.auth.token} />
      <View style={styles.headerText}><Text style={styles.name}>{name}</Text><Text style={styles.caption}>在这里</Text></View>
      <IconAction icon={History} label="历史对话" onPress={() => navigation.navigate("History")} />
      <IconAction icon={Plus} label="新建对话" disabled={busy || creatingConversation || reconcilingDraft || draftError}
        onPress={() => void newConversation()} />
      <IconAction icon={Settings2} label="形象设置" onPress={() => navigation.navigate("Profile")} />
    </View>
    {error ? <View style={styles.error}><Notice danger>{error}</Notice></View> : null}
    {draftError ? <View style={styles.error}>
      <Notice danger>{draftErrorMessage || "本机草稿不可用，请重试或确认清除。"}</Notice>
      <Action label={draftReady ? "重新保存对话草稿" : "重新读取本机草稿"} variant="quiet"
        onPress={() => void (draftReady ? retryDraftSave() : refresh())} disabled={busy} />
      <Action label="清除本机对话草稿" variant="danger" disabled={busy}
        onPress={() => Alert.alert("清除本机对话草稿？", "未发送的内容和重试记录会丢失，服务器上的消息不会删除。", [
          { text: "保留", style: "cancel" },
          { text: "清除", style: "destructive", onPress: () => void discardDraft() },
        ])} />
    </View> : null}
    {actionFeedback ? <View style={styles.error}><Notice>{actionFeedback}</Notice></View> : null}
    {introReady && !introSeen ? <View style={styles.intro}>
      <Text style={styles.introTitle}>开始对话前</Text>
      <Text style={styles.introBody}>{conversationDataNotice}</Text>
      <Text style={styles.introBody}>{followupDataNotice}</Text>
      <Action label="我已了解" busy={introBusy} onPress={() => void acknowledgeIntro()} />
    </View> : null}
    <FlatList ref={scroll} inverted style={styles.messages} contentContainerStyle={styles.messageContent}
      data={current?.messages || []} keyExtractor={message => message.id}
      renderItem={({ item }) => <ChatMessage message={item} />}
      maintainVisibleContentPosition={{ minIndexForVisible: 0 }} keyboardShouldPersistTaps="handled"
      ListEmptyComponent={<View style={styles.uprightEmpty}>{loading
        ? <ActivityIndicator color={colors.primary} />
        : <Empty title="今天想从哪里聊起？" detail="说说近况，或者只是把心里的事写下来。" />}
      </View>}
      ListFooterComponent={hasMore && current?.messages.length ? <View style={styles.olderMessages}>
        <Action label="加载更早消息" variant="quiet" busy={loadingMore} onPress={() => void loadMore()} />
        {moreError ? <Notice danger>{moreError}</Notice> : null}
      </View> : null}
      ListHeaderComponent={<View style={styles.trailing}>
      {proposals.map(proposal => proposal.kind === "checkin"
        ? <CheckinProposalCard key={proposal.id} proposal={proposal}
          busy={proposalBusy === proposal.id} disabled={proposalBusy !== null}
          onPreview={(date, text) => void updateCheckinPreview(proposal.id, date, text)}
          onConfirm={() => void updateProposal(proposal.id, "confirm")}
          onReject={() => void updateProposal(proposal.id, "reject")} />
        : <ProposalCard key={proposal.id} proposal={proposal}
          busy={proposalBusy === proposal.id} disabled={proposalBusy !== null}
          onConfirm={() => void updateProposal(proposal.id, "confirm")}
          onReject={() => void updateProposal(proposal.id, "reject")} />)}
      {busy ? <View style={styles.generating}><ActivityIndicator size="small" color={colors.primary} />
        <Text style={styles.caption}>正在回复</Text></View> : null}
      </View>} />
    {failedMessage && !busy && introSeen ? <View style={styles.retryArea}>
      <Text style={styles.retryText}>这条消息已保存，但回复未完成。</Text>
      <Action label="重试这条回复" onPress={() => void retryFailed(failedMessage.id)} />
    </View> : null}
    <View style={styles.composer}>
      <TextInput accessibilityLabel="输入消息" value={draft} onChangeText={edit} editable={!busy && draftReady && !reconcilingDraft} placeholder="给 Today 发消息"
        placeholderTextColor={colors.muted} multiline maxLength={4000} style={styles.messageInput} />
      <Pressable accessibilityRole="button" accessibilityLabel={retryId ? "检查原消息结果" : "发送消息"}
        disabled={!introReady || !introSeen || !draftReady || draftError || reconcilingDraft || !draft.trim() || busy || creatingConversation || !!failedMessage || processing} onPress={() => void send()}
        style={({ pressed }) => [styles.send, (!introReady || !introSeen || !draftReady || draftError || reconcilingDraft || !draft.trim() || busy || creatingConversation || !!failedMessage || processing) && styles.disabled, pressed && styles.pressed]}>
        <Send color={colors.onPrimary} size={20} />
      </Pressable>
    </View>
  </KeyboardAvoidingView>;
}

const ChatMessage = memo(function ChatMessage({ message }: { message: ConversationMessage }) {
  return <View style={[styles.bubble, message.role === "user" ? styles.userBubble : styles.agentBubble]}>
    <Text style={[styles.bubbleText, message.role === "user" && styles.userText]}>{message.body}</Text>
    {message.status === "processing" ? <Text style={styles.userStatus}>正在生成回复</Text> : null}
    {message.status === "failed" ? <Text style={styles.userStatus}>回复未完成</Text> : null}
  </View>;
});

function CheckinProposalCard({ proposal, busy, disabled, onPreview, onConfirm, onReject }: {
  proposal: AgentProposal; busy: boolean; disabled: boolean;
  onPreview: (date: string, text?: string) => void; onConfirm: () => void; onReject: () => void;
}) {
  const parsed = parseCheckinPreview(proposal.payloadJson);
  const preview = parsed ?? { date: "", source: "", previousText: "", text: "" };
  const [date, setDate] = useState(preview.date);
  const [text, setText] = useState(preview.text);
  useEffect(() => { setDate(preview.date); setText(preview.text); }, [proposal.payloadJson]);
  if (!parsed) return <View style={styles.proposal}>
    <Text style={styles.proposalTitle}>每日记录预览无法读取</Text>
    <Action label="暂不保存" variant="quiet" onPress={onReject} disabled={disabled} />
  </View>;
  const dateChanged = date !== preview.date;
  const changed = dateChanged || text !== preview.text;
  const parsedDate = new Date(`${date}T00:00:00Z`);
  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    Number.isFinite(parsedDate.getTime()) && parsedDate.toISOString().slice(0, 10) === date;
  return <View style={styles.proposal}>
    <Text style={styles.proposalTitle}>整理到每日记录</Text>
    <Text style={styles.previewLabel}>原话依据</Text>
    <Text style={styles.proposalExtra}>{preview.source}</Text>
    <Text style={styles.previewLabel}>记录日期</Text>
    <TextInput accessibilityLabel="记录日期，格式为年月日" value={date} onChangeText={setDate}
      placeholder="YYYY-MM-DD" placeholderTextColor={colors.muted} maxLength={10} style={styles.previewDate} />
    {!validDate ? <Text accessibilityRole="alert" style={styles.previewError}>请输入有效日期，格式为 YYYY-MM-DD。</Text> : null}
    {preview.previousText ? <><Text style={styles.previewLabel}>当天已有内容</Text>
      <Text style={styles.proposalExtra}>{preview.previousText}</Text></> : null}
    <Text style={styles.previewLabel}>拟写入正文</Text>
    <TextInput accessibilityLabel="拟写入的每日记录正文" multiline maxLength={2000} value={text}
      editable={!dateChanged} onChangeText={setText} textAlignVertical="top" style={styles.previewText} />
    {dateChanged ? <Text style={styles.proposalExtra}>更新日期后，将重新合并那一天的记录。</Text> : null}
    <View style={styles.proposalActions}>
      {changed ? <Action label="更新预览" onPress={() => onPreview(date, dateChanged ? undefined : text)}
        busy={busy} disabled={disabled || !validDate || (!dateChanged && !text.trim())} />
        : <Action label="确认写入" onPress={onConfirm} busy={busy} disabled={disabled} />}
      {!changed ? <Action label="重新获取预览" variant="quiet" onPress={() => onPreview(date)} disabled={disabled} /> : null}
      <Action label="暂不保存" variant="quiet" onPress={onReject} disabled={disabled} />
    </View>
  </View>;
}

function parseCheckinPreview(value: string) {
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>;
    if (typeof parsed.date !== "string" || typeof parsed.text !== "string") return null;
    return { date: parsed.date, text: parsed.text,
      source: typeof parsed.source === "string" ? parsed.source : "",
      previousText: typeof parsed.previousText === "string" ? parsed.previousText : "" };
  } catch { return null; }
}

function ProposalCard({ proposal, busy, disabled, onConfirm, onReject }: {
  proposal: AgentProposal; busy: boolean; disabled: boolean; onConfirm: () => void; onReject: () => void;
}) {
  let detail = "来自这段对话的建议";
  let extra = "";
  try {
    const payload = JSON.parse(proposal.payloadJson) as {
      text?: string; reason?: string; title?: string; note?: string; dueDate?: string;
      description?: string; remindTime?: string; timezone?: string; suggestedDays?: number;
      scheduledLocal?: string;
      date?: string; hasPhoto?: boolean;
      before?: { title: string; note: string | null; dueDate: string | null; description?: string | null; enabled?: boolean;
        message?: string; remindTime?: string; timezone?: string };
      after?: { title: string; note: string | null; dueDate: string | null; description?: string | null; enabled?: boolean;
        message?: string; remindTime?: string; timezone?: string };
    };
    detail = payload.text || payload.reason || payload.title || detail;
    if (proposal.kind === "todo") extra = [payload.note, payload.dueDate && `截止 ${payload.dueDate}`].filter(Boolean).join("\n");
    if (proposal.kind === "habit") extra = payload.description || "";
    if (proposal.kind === "habit_punch" || proposal.kind === "habit_unpunch") {
      extra = `${payload.date || ""}${proposal.kind === "habit_unpunch" && payload.hasPhoto
        ? "\n取消后，这次打卡的照片也会删除。" : ""}`;
    }
    if (proposal.kind === "daily_reminder") extra = `每天 ${payload.remindTime || ""} · ${payload.timezone || ""}`;
    if (proposal.kind === "one_off_reminder") extra = `${payload.scheduledLocal?.replace("T", " ") || ""} · ${payload.timezone || ""}`;
    if (proposal.kind === "followup" && payload.suggestedDays) extra = `${payload.suggestedDays} 天后再聊`;
    if (proposal.kind === "todo_edit" && payload.before && payload.after) {
      const before = payload.before;
      const after = payload.after;
      detail = `原待办：${before.title}`;
      extra = [
        before.note ? `原备注：${before.note}` : null,
        before.dueDate ? `原截止：${before.dueDate}` : null,
        before.title !== after.title ? `改为：${after.title}` : null,
        before.note !== after.note ? `新备注：${after.note || "无"}` : null,
        before.dueDate !== after.dueDate ? `新截止：${after.dueDate || "无"}` : null,
      ].filter(Boolean).join("\n");
    }
    if (proposal.kind === "habit_edit" && payload.before && payload.after) {
      const before = payload.before;
      const after = payload.after;
      detail = `原习惯：${before.title}`;
      extra = [
        before.title !== after.title ? `新名称：${after.title}` : null,
        before.description !== after.description ? `新描述：${after.description || "无"}` : null,
        before.enabled !== after.enabled ? `状态：${after.enabled ? "启用" : "暂停"}` : null,
      ].filter(Boolean).join("\n");
    }
    if (proposal.kind === "daily_reminder_edit" && payload.before && payload.after) {
      const before = payload.before;
      const after = payload.after;
      detail = `原提醒：${before.title} · 每天 ${before.remindTime}`;
      extra = [
        before.title !== after.title ? `新标题：${after.title}` : null,
        before.message !== after.message ? `新内容：${after.message}` : null,
        before.remindTime !== after.remindTime ? `新时间：每天 ${after.remindTime}` : null,
        before.timezone !== after.timezone ? `新时区：${after.timezone}` : null,
        before.enabled !== after.enabled ? `状态：${after.enabled ? "启用" : "暂停"}` : null,
      ].filter(Boolean).join("\n");
    }
  } catch { /* The server owns the proposal payload. */ }
  const titles: Record<string, string> = {
    memory: "保存为长期记忆？", followup: "以后继续关注？", todo: "创建待办？",
    todo_edit: "修改这条待办？",
    habit: "建立打卡习惯？", habit_edit: "修改这条习惯？", habit_punch: "记录这次打卡？",
    habit_unpunch: "取消这次打卡？", daily_reminder: "设置每日提醒？",
    daily_reminder_edit: "修改这条每日提醒？",
    one_off_reminder: "约定单次提醒？",
  };
  return <View style={styles.proposal}>
    <Text style={styles.proposalTitle}>{titles[proposal.kind] || "确认这项建议？"}</Text>
    <Text style={styles.proposalDetail}>{detail}</Text>
    {extra ? <Text style={styles.proposalExtra}>{extra}</Text> : null}
    <View style={styles.proposalActions}>
      <Action label="确认执行" onPress={onConfirm} busy={busy} disabled={disabled} />
      <Action label="暂不" variant="quiet" onPress={onReject} disabled={disabled} />
    </View>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  header: { minHeight: 72, paddingHorizontal: 12, flexDirection: "row", alignItems: "center", borderBottomWidth: 1, borderBottomColor: colors.line, backgroundColor: colors.surface },
  headerText: { flex: 1, minWidth: 60, marginLeft: 11 },
  name: { color: colors.ink, fontSize: 16, fontWeight: "700" },
  caption: { color: colors.muted, fontSize: 12, marginTop: 2 },
  intro: { paddingHorizontal: layout.gutter, paddingVertical: 16, backgroundColor: colors.primarySoft,
    borderBottomWidth: 1, borderBottomColor: colors.line, gap: 10 },
  introTitle: { color: colors.ink, fontSize: 16, fontWeight: "700" },
  introBody: { color: colors.ink, fontSize: 14, lineHeight: 22 },
  messages: { flex: 1 }, messageContent: { paddingHorizontal: layout.gutter, paddingVertical: 24, gap: 14, flexGrow: 1, width: "100%", maxWidth: 760, alignSelf: "center" },
  uprightEmpty: { transform: [{ rotate: "180deg" }] },
  trailing: { gap: 14 }, olderMessages: { alignItems: "center", gap: 8, paddingVertical: 12 },
  bubble: { maxWidth: "85%", borderRadius: 8, paddingHorizontal: 15, paddingVertical: 11 },
  agentBubble: { backgroundColor: colors.surface, alignSelf: "flex-start", borderWidth: 1, borderColor: colors.line },
  userBubble: { backgroundColor: colors.primary, alignSelf: "flex-end" },
  bubbleText: { fontSize: 16, lineHeight: 24, color: colors.ink }, userText: { color: colors.onPrimary },
  userStatus: { color: colors.onPrimary, fontSize: 12, lineHeight: 18, marginTop: 5 },
  retryArea: { paddingHorizontal: layout.gutter, paddingVertical: 10, backgroundColor: colors.attentionSoft,
    flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 8 },
  retryText: { color: colors.ink, fontSize: 14, flexGrow: 1 },
  generating: { flexDirection: "row", gap: 10, alignItems: "center", padding: 12 },
  proposal: { padding: 16, backgroundColor: colors.attentionSoft, borderRadius: 7, gap: 10, maxWidth: 480 },
  proposalTitle: { color: colors.ink, fontSize: 15, fontWeight: "700" },
  proposalDetail: { color: colors.ink, fontSize: 15, lineHeight: 23 },
  proposalExtra: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  proposalActions: { flexDirection: "row", gap: 8, flexWrap: "wrap" },
  previewLabel: { color: colors.ink, fontSize: 14, fontWeight: "600" },
  previewDate: { backgroundColor: colors.surface, borderColor: colors.line, borderWidth: 1, borderRadius: 7,
    minHeight: 48, paddingHorizontal: 12, fontSize: 16, color: colors.ink },
  previewText: { backgroundColor: colors.surface, borderColor: colors.line, borderWidth: 1, borderRadius: 7,
    minHeight: 112, padding: 12, fontSize: 16, lineHeight: 24, color: colors.ink },
  previewError: { color: colors.danger, fontSize: 13, lineHeight: 19 },
  error: { paddingHorizontal: layout.gutter, paddingTop: 12 },
  composer: { paddingHorizontal: layout.gutter, paddingTop: 10, paddingBottom: 12, backgroundColor: colors.surface,
    borderTopColor: colors.line, borderTopWidth: 1, flexDirection: "row", gap: 10, alignItems: "flex-end" },
  messageInput: { flex: 1, minHeight: 48, maxHeight: 120, color: colors.ink, fontSize: 16, lineHeight: 23,
    borderRadius: 7, paddingHorizontal: 14, paddingVertical: 11, backgroundColor: colors.input, textAlignVertical: "top" },
  send: { width: 48, height: 48, borderRadius: 7, alignItems: "center", justifyContent: "center", backgroundColor: colors.primary },
  disabled: { opacity: 0.42 }, pressed: { opacity: 0.7 },
});
