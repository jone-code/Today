import { useCallback, useRef, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { Archive, ArchiveRestore, ChevronDown, ChevronRight, Pencil, Save, Trash2, X } from "lucide-react-native";
import type { CheckinDto, TimelineItemDto, MemoryDto, MemoryOrigin, MemoryAccessAudit, WeeklyReview } from "@today/contracts";
import { useSession } from "../session";
import type { RootStackParams } from "../navigation";
import { colors } from "../theme";
import { Action, Empty, Field, Heading, IconAction, Notice, Page, Separator, messageOf } from "../ui";

export function TimelineScreen() {
  const { api } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const [items, setItems] = useState<TimelineItemDto[]>([]);
  const [memories, setMemories] = useState<MemoryDto[]>([]);
  const [record, setRecord] = useState<CheckinDto | null>(null);
  const [recordId, setRecordId] = useState<string | null>(null);
  const [recordLoading, setRecordLoading] = useState(false);
  const [recordError, setRecordError] = useState("");
  const [expandedMemoryId, setExpandedMemoryId] = useState<string | null>(null);
  const [origins, setOrigins] = useState<MemoryOrigin[]>([]);
  const [audits, setAudits] = useState<MemoryAccessAudit[]>([]);
  const [originsLoading, setOriginsLoading] = useState(false);
  const [originsError, setOriginsError] = useState("");
  const recordRequest = useRef(0);
  const originsRequest = useRef(0);
  const [view, setView] = useState<"timeline" | "memories">("timeline");
  const [showArchived, setShowArchived] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [scopeBusyId, setScopeBusyId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [weekly, setWeekly] = useState<WeeklyReview | null>(null);
  const [weeklyLoading, setWeeklyLoading] = useState(false);
  const [weeklyError, setWeeklyError] = useState("");

  const refresh = useCallback(async () => {
    try {
      const [time, memory] = await Promise.all([api.timeline(), api.memories(true)]);
      setItems(time.items);
      setMemories(memory.items);
      setError("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setLoading(false); }
  }, [api]);

  useFocusEffect(useCallback(() => { void refresh(); }, [refresh]));

  const currentWeek = weekStart(new Date());
  async function loadWeekly(retry = false) {
    setWeeklyLoading(true); setWeeklyError("");
    try {
      const value = retry ? await api.retryWeeklyReview(currentWeek) : await api.weeklyReview(currentWeek);
      setWeekly(value);
      void api.track([{ eventName: "weekly_review_viewed", screen: "History", entityType: "weekly_review", entityId: currentWeek }]).catch(() => {});
    } catch (failure) { setWeeklyError(messageOf(failure)); }
    finally { setWeeklyLoading(false); }
  }

  async function openRecord(id: string) {
    const request = ++recordRequest.current;
    setView("timeline"); setRecordId(id); setRecord(null); setRecordLoading(true); setRecordError("");
    try {
      const result = await api.checkin(id);
      if (request === recordRequest.current) setRecord(result);
    } catch (failure) {
      if (request === recordRequest.current) setRecordError(messageOf(failure));
    } finally {
      if (request === recordRequest.current) setRecordLoading(false);
    }
  }

  async function toggleOrigins(id: string, retry = false) {
    const request = ++originsRequest.current;
    if (expandedMemoryId === id && !retry) {
      setExpandedMemoryId(null); setOrigins([]); setAudits([]); setOriginsLoading(false); setOriginsError("");
      return;
    }
    setExpandedMemoryId(id); setOrigins([]); setAudits([]); setOriginsLoading(true); setOriginsError("");
    try {
      const [result, audit] = await Promise.all([api.memoryOrigins(id), api.memoryAccessAudit(id)]);
      if (request === originsRequest.current) { setOrigins(result); setAudits(audit); }
    } catch (failure) {
      if (request === originsRequest.current) setOriginsError(messageOf(failure));
    } finally {
      if (request === originsRequest.current) setOriginsLoading(false);
    }
  }

  async function update(id: string, action: "save" | "archive" | "restore" | "delete") {
    if (busyId) return;
    setBusyId(id); setBusyAction(action); setError(""); setFeedback("");
    try {
      if (action === "save") {
        const updated = await api.updateMemory(id, draft);
        setMemories(previous => previous.map(item => item.id === id ? updated : item));
        setEditingId(null);
      } else if (action === "delete") {
        await api.deleteMemory(id);
        setMemories(previous => previous.filter(item => item.id !== id));
        setEditingId(null);
      } else {
        const updated = await api.archiveMemory(id, action === "archive");
        setMemories(previous => previous.map(item => item.id === id ? updated : item));
        setEditingId(null);
      }
      if (expandedMemoryId === id) {
        originsRequest.current += 1;
        setExpandedMemoryId(null); setOrigins([]); setAudits([]); setOriginsLoading(false); setOriginsError("");
      }
      void api.track([{
        eventName: action === "save" ? "memory_updated" : action === "archive" ? "memory_archived" : action === "restore" ? "memory_restored" : "memory_deleted",
        screen: "History", entityType: "memory", entityId: id,
      }]).catch(() => {});
      setFeedback(({ save: "记忆已更新", archive: "记忆已归档", restore: "记忆已恢复", delete: "记忆已删除" })[action]);
    } catch (failure) {
      if (failure instanceof Error && "status" in failure && (failure as Error & { status?: number }).status === 409) {
        await refresh();
        setFeedback("记忆已被其他设备修改，已刷新最新内容。");
      } else setError(messageOf(failure));
    }
    finally { setBusyId(null); setBusyAction(null); }
  }

  async function updateScope(id: string, scope: "chat_and_timeline" | "timeline_only" | "disabled") {
    if (scopeBusyId) return;
    setScopeBusyId(id); setError(""); setFeedback("");
    try {
      const current = memories.find(item => item.id === id);
      const updated = await api.updateMemoryContextScope(id, scope, current?.updatedAt);
      setMemories(previous => previous.map(item => item.id === id ? updated : item));
      void api.track([{
        eventName: "memory_context_scope_updated", screen: "History", entityType: "memory", entityId: id,
        propertiesJson: JSON.stringify({ scope }),
      }]).catch(() => {});
      setFeedback("记忆使用范围已更新");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setScopeBusyId(null); }
  }

  function confirmDelete(item: MemoryDto) {
    Alert.alert("删除这条记忆？", "删除后，后续对话将不再使用这条记忆。", [
      { text: "取消", style: "cancel" },
      { text: "删除", style: "destructive", onPress: () => void update(item.id, "delete") },
    ]);
  }

  const visibleMemories = memories.filter(item => item.archived === showArchived);
  const recordDetail = recordId ? <View style={styles.record}>
    <View style={styles.recordHeader}>
      <Text style={styles.title}>{record?.date ?? "每日记录"}</Text>
      <IconAction icon={X} label="关闭每日记录原文" onPress={() => {
        recordRequest.current += 1; setRecordId(null); setRecord(null); setRecordLoading(false); setRecordError("");
      }} />
    </View>
    {recordLoading ? <Text style={styles.body}>正在加载记录</Text> : record ?
      <Text selectable style={styles.recordText}>{record.rawText}</Text> :
      <View><Notice danger>{recordError || "记录暂时无法显示"}</Notice>
        <Action label="重试" variant="quiet" onPress={() => void openRecord(recordId)} /></View>}
  </View> : null;
  return <Page>
    <Heading title="回看" />
    <View style={styles.tabs} accessibilityRole="tablist">
      {(["timeline", "memories"] as const).map(option => <Pressable key={option}
        accessibilityRole="tab" accessibilityState={{ selected: view === option }}
        accessibilityLabel={option === "timeline" ? "时间轴" : "记忆"}
        onPress={() => { setView(option); setFeedback(""); }}
        style={({ pressed }) => [styles.tab, view === option && styles.activeTab, pressed && styles.pressed]}>
        <Text style={[styles.tabText, view === option && styles.activeTabText]}>
          {option === "timeline" ? "时间轴" : "记忆"}
        </Text>
      </Pressable>)}
    </View>
    {error ? <Notice danger>{error}</Notice> : null}
    {feedback ? <Notice>{feedback}</Notice> : null}
    {loading ? <Text style={styles.body}>正在加载</Text> : view === "timeline" ? <>
      <View style={styles.reviewCard}>
        <View style={styles.reviewHeader}><Text style={styles.reviewTitle}>本周回看</Text><Text style={styles.reviewWeek}>{currentWeek}</Text></View>
        {!weekly && !weeklyLoading ? <Action label="生成本周周报" onPress={() => void loadWeekly()} /> : null}
        {weeklyLoading ? <Text style={styles.body}>正在整理这一周</Text> : null}
        {weeklyError ? <><Notice danger>{weeklyError}</Notice><Action label="重试" variant="quiet" onPress={() => void loadWeekly(true)} /></> : null}
        {weekly?.status === "running" ? <Text style={styles.body}>正在整理这一周，请稍后查看。</Text> : null}
        {weekly && (weekly.status === "failed" || weekly.status === "expired") ? <>
          <Notice danger>{weekly.error || (weekly.status === "expired" ? "这份周报已过期，请重新生成。" : "周报生成失败，请重试。")}</Notice>
          {weekly.retryable ? <Action label="重试周报" variant="quiet" onPress={() => void loadWeekly(true)} disabled={weeklyLoading} /> : null}
        </> : null}
        {weekly?.status === "succeeded" ? <>
          <Text style={styles.reviewReflection}>{weekly.reflection}</Text>
          <Text style={styles.reviewStats}>记录 {weekly.daysRecorded} 天 · 完成待办 {weekly.completedTodos} 件</Text>
          {weekly.highlights.map((highlight, index) => <Text key={`${index}-${highlight}`} style={styles.reviewHighlight}>· {highlight}</Text>)}
        </> : null}
      </View>
      {recordId && !items.some(item => item.checkinId === recordId) ? recordDetail : null}
      {!items.length && !recordId ? <Empty title="时间轴还没有记录" /> : items.map(item => <View key={item.checkinId} style={styles.item}>
        <Pressable accessibilityRole="button" accessibilityLabel={`查看${item.date}的每日记录原文`}
          accessibilityState={{ expanded: recordId === item.checkinId }}
          onPress={() => void openRecord(item.checkinId)}
          style={({ pressed }) => [styles.timelineOpen, pressed && styles.pressed]}>
          <View style={styles.itemContent}>
            <Text style={styles.date}>{item.date}</Text><Text style={styles.title}>{item.oneLiner}</Text>
            <Text style={styles.body}>{item.highlight}</Text>
          </View>
          <ChevronRight size={20} color={colors.primary} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" />
        </Pressable>
        {recordId === item.checkinId ? recordDetail : null}
        <Separator />
      </View>)}
    </> : <>
        <View style={styles.filters}>
          {([false, true] as const).map(archived => <Pressable key={String(archived)}
            accessibilityRole="tab" accessibilityState={{ selected: showArchived === archived }}
            onPress={() => { setShowArchived(archived); setFeedback(""); }}
            style={({ pressed }) => [styles.filter, showArchived === archived && styles.activeFilter, pressed && styles.pressed]}>
            <Text style={[styles.filterText, showArchived === archived && styles.activeFilterText]}>
              {archived ? "已归档" : "在用"}
            </Text>
          </Pressable>)}
        </View>
        {!visibleMemories.length ? <Empty title={showArchived ? "没有已归档的记忆" : "还没有保存的记忆"} /> :
          visibleMemories.map(item => <View key={item.id} style={styles.memory}>
            {editingId === item.id ? <>
              <Field label="修改记忆" value={draft} onChangeText={setDraft} multiline numberOfLines={3}
                maxLength={512} editable={!busyId} error={!draft.trim() ? "请输入记忆内容" : undefined} />
              <View style={styles.editActions}>
                <Action label="保存修改" icon={Save} onPress={() => void update(item.id, "save")}
                  busy={busyId === item.id} disabled={!!busyId || !draft.trim() || draft.trim() === item.text} />
                <Action label="取消" variant="quiet" disabled={!!busyId} onPress={() => setEditingId(null)} />
              </View>
            </> : <View style={styles.memoryRow}>
              <Text style={styles.memoryText}>{item.text}</Text>
              {!item.archived ? <View style={styles.scopeRow}>
                <Text style={styles.scopeLabel}>Chat 使用范围</Text>
                {(["chat_and_timeline", "timeline_only", "disabled"] as const).map(scope =>
                  <Pressable key={scope} accessibilityRole="button"
                    accessibilityState={{ selected: item.chatContextScope === scope }}
                    disabled={!!scopeBusyId} onPress={() => void updateScope(item.id, scope)}
                    style={({ pressed }) => [styles.scopeChip, item.chatContextScope === scope && styles.scopeChipActive, pressed && styles.pressed]}>
                    <Text style={[styles.scopeChipText, item.chatContextScope === scope && styles.scopeChipTextActive]}>
                      {scope === "chat_and_timeline" ? "Chat 可用" : scope === "timeline_only" ? "仅回看" : "停用"}
                    </Text>
                  </Pressable>)}
              </View> : null}
              <View style={styles.memoryActions}>
                <Pressable accessibilityRole="button" accessibilityLabel="查看这条记忆的来源"
                  accessibilityState={{ expanded: expandedMemoryId === item.id }}
                  onPress={() => void toggleOrigins(item.id)}
                  style={({ pressed }) => [styles.originToggle, pressed && styles.pressed]}>
                  {expandedMemoryId === item.id ? <ChevronDown size={19} color={colors.primary} /> : <ChevronRight size={19} color={colors.primary} />}
                  <Text style={styles.originToggleText}>来源</Text>
                </Pressable>
                {!item.archived ? <IconAction icon={Pencil} label="编辑这条记忆" disabled={!!busyId} onPress={() => {
                  setEditingId(item.id); setDraft(item.text); setError(""); setFeedback("");
                }} /> : null}
                <IconAction icon={item.archived ? ArchiveRestore : Archive}
                  label={item.archived ? "恢复这条记忆" : "归档这条记忆"}
                  disabled={!!busyId} busy={busyId === item.id && (busyAction === "archive" || busyAction === "restore")}
                  onPress={() => void update(item.id, item.archived ? "restore" : "archive")} />
                <IconAction icon={Trash2} label="删除这条记忆" disabled={!!busyId}
                  busy={busyId === item.id && busyAction === "delete"}
                  onPress={() => confirmDelete(item)} />
              </View>
            </View>}
            {expandedMemoryId === item.id && editingId !== item.id ? <View style={styles.origins}>
              {originsLoading ? <Text style={styles.body}>正在加载来源和使用记录</Text> : originsError ?
                <View><Notice danger>{originsError}</Notice>
                  <Action label="重试" variant="quiet" onPress={() => void toggleOrigins(item.id, true)} /></View> : origins.length ? origins.map(origin =>
                <Pressable key={`${origin.kind}-${origin.sourceId}`} accessibilityRole="button"
                  accessibilityLabel={origin.kind === "checkin" ? `查看${origin.date ?? "每日记录"}原文` : "打开来源会话"}
                  onPress={() => origin.kind === "checkin" ? void openRecord(origin.sourceId) :
                    origin.conversationId && navigation.navigate("Conversation", { id: origin.conversationId })}
                  style={({ pressed }) => [styles.originLink, pressed && styles.pressed]}>
                  <Text style={styles.originLinkText}>{origin.kind === "checkin" ? `每日记录 · ${origin.date ?? ""}` : "对话记录"}</Text>
                  <ChevronRight size={18} color={colors.primary} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" />
                </Pressable>) : <Text style={styles.body}>没有可查看的来源</Text>}
              {!originsLoading && !originsError ? <View style={styles.auditBlock}>
                <Text style={styles.auditTitle}>最近使用记录</Text>
                {audits.length ? audits.slice(0, 10).map(entry =>
                  <Text key={entry.id} style={styles.auditText}>
                    {entry.source === "chat" ? "Chat" : entry.source === "timeline" ? "时间轴" : entry.source} · {entry.action === "retrieved" ? "被读取" : entry.action} · {formatAuditTime(entry.occurredAt)}
                  </Text>) : <Text style={styles.body}>暂时没有使用记录</Text>}
              </View> : null}
            </View> : null}
            <Separator />
          </View>)}
      </>}
  </Page>;
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", gap: 8, marginBottom: 24 },
  tab: { flex: 1, minHeight: 46, alignItems: "center", justifyContent: "center", borderRadius: 999,
    borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface },
  activeTab: { backgroundColor: colors.primaryDeep, borderColor: colors.primaryDeep },
  tabText: { color: colors.muted, fontSize: 13, fontWeight: "700" },
  activeTabText: { color: colors.onPrimary },
  filters: { flexDirection: "row", gap: 8, marginBottom: 16 },
  filter: { flex: 1, minHeight: 46, alignItems: "center", justifyContent: "center", borderRadius: 999,
    borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface },
  activeFilter: { borderColor: colors.primaryDeep, backgroundColor: colors.primaryDeep },
  filterText: { color: colors.muted, fontSize: 13, fontWeight: "700" },
  activeFilterText: { color: colors.onPrimary },
  item: { gap: 8 }, timelineOpen: { flexDirection: "row", alignItems: "center", minHeight: 64, gap: 8 },
  itemContent: { flex: 1, gap: 8, padding: 16 }, date: { color: colors.attention, fontSize: 11, fontWeight: "800" },
  title: { color: colors.ink, fontSize: 17, fontWeight: "800", lineHeight: 24 },
  body: { color: colors.muted, fontSize: 15, lineHeight: 24 },
  reviewCard: { padding: 16, marginBottom: 20, gap: 10, borderRadius: 18, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line },
  reviewHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  reviewTitle: { color: colors.ink, fontSize: 18, fontWeight: "800" },
  reviewWeek: { color: colors.muted, fontSize: 12 },
  reviewReflection: { color: colors.ink, fontSize: 15, lineHeight: 23 },
  reviewStats: { color: colors.primary, fontSize: 13, fontWeight: "700" },
  reviewHighlight: { color: colors.inkSoft, fontSize: 14, lineHeight: 21 },
  record: { padding: 16, backgroundColor: colors.surface, borderWidth: 1,
    borderColor: colors.line, borderRadius: 18, marginBottom: 24, gap: 12, shadowColor: colors.ink, shadowOpacity: 0.05, shadowRadius: 12, shadowOffset: { width: 0, height: 6 }, elevation: 1 },
  recordHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  recordText: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  memory: { marginBottom: 4 },
  memoryRow: { gap: 8 }, memoryText: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  scopeRow: { flexDirection: "row", alignItems: "center", gap: 6, flexWrap: "wrap" },
  scopeLabel: { color: colors.muted, fontSize: 12, marginRight: 2 },
  scopeChip: { minHeight: 32, paddingHorizontal: 10, justifyContent: "center", borderRadius: 999, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface },
  scopeChipActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  scopeChipText: { color: colors.muted, fontSize: 12, fontWeight: "700" },
  scopeChipTextActive: { color: colors.primaryDeep },
  memoryActions: { flexDirection: "row", justifyContent: "flex-end", alignItems: "center", gap: 8 },
  originToggle: { minHeight: 48, flexDirection: "row", alignItems: "center", paddingHorizontal: 8, gap: 4, marginRight: "auto" },
  originToggleText: { color: colors.primary, fontSize: 14, fontWeight: "600" },
  origins: { gap: 4, paddingVertical: 8 },
  originLink: { minHeight: 48, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 8 },
  originLinkText: { color: colors.primary, fontSize: 15, flexShrink: 1 },
  auditBlock: { marginTop: 12, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.line },
  auditTitle: { color: colors.muted, fontSize: 12, fontWeight: "700", marginBottom: 5 },
  auditText: { color: colors.muted, fontSize: 12, lineHeight: 18 },
  editActions: { flexDirection: "row", alignItems: "center", gap: 8, flexWrap: "wrap" },
  pressed: { opacity: 0.64 },
});

function weekStart(date: Date): string {
  const value = new Date(date); const day = value.getDay() || 7;
  value.setDate(value.getDate() - day + 1);
  return value.toISOString().slice(0, 10);
}

function formatAuditTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", {
    month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}
