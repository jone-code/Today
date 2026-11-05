import { useCallback, useRef, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { Archive, ArchiveRestore, ChevronDown, ChevronRight, Pencil, Save, Trash2, X } from "lucide-react-native";
import type { CheckinDto, TimelineItemDto, MemoryDto, MemoryOrigin } from "@today/contracts";
import { useSession, type RootStackParams } from "../App";
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");

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
      setExpandedMemoryId(null); setOrigins([]); setOriginsLoading(false); setOriginsError("");
      return;
    }
    setExpandedMemoryId(id); setOrigins([]); setOriginsLoading(true); setOriginsError("");
    try {
      const result = await api.memoryOrigins(id);
      if (request === originsRequest.current) setOrigins(result);
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
        setExpandedMemoryId(null); setOrigins([]); setOriginsLoading(false); setOriginsError("");
      }
      setFeedback(({ save: "记忆已更新", archive: "记忆已归档", restore: "记忆已恢复", delete: "记忆已删除" })[action]);
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusyId(null); setBusyAction(null); }
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
              {originsLoading ? <Text style={styles.body}>正在加载来源</Text> : originsError ?
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
            </View> : null}
            <Separator />
          </View>)}
      </>}
  </Page>;
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", marginBottom: 24, borderBottomWidth: 1, borderBottomColor: colors.line },
  tab: { flex: 1, minHeight: 48, alignItems: "center", justifyContent: "center", borderBottomWidth: 2, borderBottomColor: "transparent" },
  activeTab: { borderBottomColor: colors.primary },
  tabText: { color: colors.muted, fontSize: 15, fontWeight: "600" },
  activeTabText: { color: colors.primary },
  filters: { flexDirection: "row", gap: 8, marginBottom: 16 },
  filter: { flex: 1, minHeight: 48, alignItems: "center", justifyContent: "center", borderRadius: 7,
    borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface },
  activeFilter: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  filterText: { color: colors.muted, fontSize: 14, fontWeight: "600" },
  activeFilterText: { color: colors.primary },
  item: { gap: 8 }, timelineOpen: { flexDirection: "row", alignItems: "center", minHeight: 64, gap: 8 },
  itemContent: { flex: 1, gap: 8 }, date: { color: colors.primary, fontSize: 13, fontWeight: "700" },
  title: { color: colors.ink, fontSize: 17, fontWeight: "600", lineHeight: 26 },
  body: { color: colors.muted, fontSize: 15, lineHeight: 24 },
  record: { paddingVertical: 16, paddingHorizontal: 16, backgroundColor: colors.surface, borderWidth: 1,
    borderColor: colors.line, borderRadius: 7, marginBottom: 24, gap: 12 },
  recordHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  recordText: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  memory: { marginBottom: 4 },
  memoryRow: { gap: 8 }, memoryText: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  memoryActions: { flexDirection: "row", justifyContent: "flex-end", alignItems: "center", gap: 8 },
  originToggle: { minHeight: 48, flexDirection: "row", alignItems: "center", paddingHorizontal: 8, gap: 4, marginRight: "auto" },
  originToggleText: { color: colors.primary, fontSize: 14, fontWeight: "600" },
  origins: { gap: 4, paddingVertical: 8 },
  originLink: { minHeight: 48, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 8 },
  originLinkText: { color: colors.primary, fontSize: 15, flexShrink: 1 },
  editActions: { flexDirection: "row", alignItems: "center", gap: 8, flexWrap: "wrap" },
  pressed: { opacity: 0.64 },
});
