import { useCallback, useState } from "react";
import { Alert, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { ArrowRight, Check, Clock3, Pencil } from "lucide-react-native";
import type { Appointment, FollowupTopic, InboxItem } from "@today/contracts";
import { useSession } from "../session";
import type { RootStackParams } from "../navigation";
import { ApiRequestError } from "../api";
import { colors } from "../theme";
import { Action, Empty, Field, Heading, IconAction, Notice, Page, SectionLabel, Separator, Surface, messageOf } from "../ui";

export function InboxScreen({ onUnreadChange }: { onUnreadChange: (count: number) => void }) {
  const { api } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const [items, setItems] = useState<InboxItem[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [topics, setTopics] = useState<FollowupTopic[]>([]);
  const [timezone, setTimezone] = useState("Asia/Shanghai");
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [cancelingId, setCancelingId] = useState<string | null>(null);
  const [topicBusy, setTopicBusy] = useState<string | null>(null);
  const [editingTopic, setEditingTopic] = useState<FollowupTopic | null>(null);
  const [topicReason, setTopicReason] = useState("");
  const [topicDate, setTopicDate] = useState("");
  const [topicReasonError, setTopicReasonError] = useState("");
  const [topicDateError, setTopicDateError] = useState("");
  const [topicError, setTopicError] = useState("");
  const [snoozingId, setSnoozingId] = useState<string | null>(null);
  const [snoozeDate, setSnoozeDate] = useState("");
  const [itemBusy, setItemBusy] = useState<string | null>(null);
  const [itemAction, setItemAction] = useState<string | null>(null);
  const refresh = useCallback(() => {
    void api.track([{ eventName: "inbox_viewed", screen: "Inbox" }]).catch(() => {});
    void Promise.all([api.inbox(), api.appointments(), api.followupTopics(), api.followupPreferences()])
    .then(([inbox, upcoming, activeTopics, pref]) => {
      setItems(inbox); setAppointments(upcoming); setTopics(activeTopics); setTimezone(pref.timezone); setError("");
      void api.unreadCount().then(({ count }) => onUnreadChange(count)).catch(() => {});
    })
    .catch(e => setError(messageOf(e))); }, [api, onUnreadChange]);
  useFocusEffect(refresh);

  async function act(item: InboxItem, action: "read" | "dismiss" | "continue") {
    if (itemBusy) return;
    setItemBusy(item.id); setItemAction(action);
    try {
      const updated = await api.updateInbox(item.id, action);
      void api.track([{ eventName: action === "continue" ? "followup_continued" : action === "dismiss" ? "inbox_item_dismissed" : "inbox_item_read",
        screen: "Inbox", entityType: "inbox_item", entityId: item.id,
        propertiesJson: JSON.stringify({ sourceKind: item.sourceKind }) }]).catch(() => {});
      refresh();
      setError("");
      if (action === "continue" && updated.conversationId) {
        navigation.navigate("Conversation", { id: updated.conversationId });
      }
    } catch (failure) { setError(messageOf(failure)); }
    finally { setItemBusy(null); setItemAction(null); }
  }

  async function snooze(id: string) {
    if (itemBusy) return;
    const parsed = /^\d{4}-\d{2}-\d{2}$/.test(snoozeDate) ? new Date(`${snoozeDate}T00:00:00Z`) : null;
    if (!parsed || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== snoozeDate) {
      setError("请输入有效日期（YYYY-MM-DD）"); return;
    }
    setItemBusy(id); setItemAction("snooze");
    try {
      await api.snoozeInbox(id, snoozeDate);
      void api.track([{ eventName: "followup_snoozed", screen: "Inbox", entityType: "inbox_item", entityId: id }]).catch(() => {});
      setSnoozingId(null); setSnoozeDate(""); setError(""); refresh();
    } catch (failure) { setError(messageOf(failure)); }
    finally { setItemBusy(null); setItemAction(null); }
  }

  function stop(item: InboxItem) {
    Alert.alert("停止这件事的跟进？", "停止后不会再主动问起这件事。", [
      { text: "取消", style: "cancel" },
      { text: "停止", style: "destructive", onPress: () => {
        if (itemBusy) return;
        setItemBusy(item.id); setItemAction("stop");
        void api.stopTopic(item.sourceId)
          .then(() => { void api.track([{ eventName: "followup_stopped", screen: "Inbox", entityType: "followup_topic", entityId: item.sourceId }]).catch(() => {}); setFeedback("已停止这件事的跟进"); refresh(); })
          .catch(e => setError(messageOf(e))).finally(() => { setItemBusy(null); setItemAction(null); });
      } },
    ]);
  }

  function changeTopic(topic: FollowupTopic) {
    const stopping = topic.state === "authorized";
    Alert.alert(stopping ? "停止这件事的跟进？" : "重新关注这件事？",
      stopping ? "停止后不会再主动问起这件事。" : "重新开启后，最早明天才可能收到站内跟进。", [
        { text: "取消", style: "cancel" },
        { text: stopping ? "停止" : "重新开启", style: stopping ? "destructive" : "default",
          onPress: () => {
            setTopicBusy(topic.id);
            void (stopping ? api.stopTopic(topic.id) : api.resumeTopic(topic.id))
              .then(() => {
                void api.track([{
                  eventName: stopping ? "followup_stopped" : "followup_resumed",
                  screen: "Inbox", entityType: "followup_topic", entityId: topic.id,
                }]).catch(() => {});
                setFeedback(stopping ? "已停止这件事的跟进" : "已重新开启跟进"); refresh();
              })
              .catch(e => setError(messageOf(e))).finally(() => setTopicBusy(null));
          } },
      ]);
  }

  function editTopic(topic: FollowupTopic) {
    setEditingTopic(topic);
    setTopicReason(topic.reason);
    setTopicDate(localDate(topic.nextEligibleAt, timezone));
    setTopicReasonError(""); setTopicDateError(""); setTopicError("");
  }

  async function saveTopic() {
    if (!editingTopic || topicBusy) return;
    const reason = topicReason.trim();
    const parsed = /^\d{4}-\d{2}-\d{2}$/.test(topicDate) ? new Date(`${topicDate}T00:00:00Z`) : null;
    const validDate = parsed && !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === topicDate;
    setTopicReasonError(!reason || reason.length > 200 ? "跟进缘由需为 1 到 200 个字" : "");
    setTopicDateError(!validDate ? "请输入有效日期（YYYY-MM-DD）" : "");
    if (!reason || reason.length > 200 || !validDate) return;
    const oldDate = localDate(editingTopic.nextEligibleAt, timezone);
    if (reason === editingTopic.reason && topicDate === oldDate) {
      setEditingTopic(null); return;
    }
    setTopicBusy(editingTopic.id); setTopicError("");
    try {
      const updated = await api.updateFollowupTopic(editingTopic.id, {
        reason, ...(topicDate === oldDate ? {} : { nextDate: topicDate }),
        expectedReason: editingTopic.reason, expectedNextEligibleAt: editingTopic.nextEligibleAt,
      });
      setTopics(current => current.map(item => item.id === updated.id ? updated : item));
      void api.track([{
        eventName: "followup_updated", screen: "Inbox", entityType: "followup_topic", entityId: updated.id,
        propertiesJson: JSON.stringify({ reasonChanged: reason !== editingTopic.reason, dateChanged: topicDate !== oldDate }),
      }]).catch(() => {});
      setEditingTopic(null); setFeedback("跟进设置已保存"); setError(""); refresh();
    } catch (failure) {
      setTopicError(failure instanceof ApiRequestError && failure.status === 409
        ? "话题已变化或所选日期过早，请刷新后重新编辑" : messageOf(failure));
      if (failure instanceof ApiRequestError && failure.status === 409) refresh();
    } finally { setTopicBusy(null); }
  }

  function cancelAppointment(item: Appointment) {
    Alert.alert("取消这次约定？", `${item.title}\n取消后不会生成站内消息。`, [
      { text: "保留", style: "cancel" },
      { text: "取消约定", style: "destructive", onPress: () => {
        setCancelingId(item.id);
        void api.cancelAppointment(item.id)
          .then(() => {
            void api.track([{ eventName: "appointment_cancelled", screen: "Inbox", entityType: "appointment", entityId: item.id }]).catch(() => {});
            setFeedback("约定已取消"); refresh();
          })
          .catch(e => setError(messageOf(e)))
          .finally(() => setCancelingId(null));
      } },
    ]);
  }

  return <Page><Heading title="收件箱" detail="站内消息与约定" />
    <Surface tone="dark" style={styles.hero}>
      <Text style={styles.heroEyebrow}>A LITTLE NUDGE FROM TODAY</Text>
      <Text style={styles.heroTitle}>{items.length ? "有 " + items.length + " 件事，" : "今天还没有新消息，"}{"\n"}值得你看一眼。</Text>
      <Text style={styles.heroDetail}>所有主动跟进都在这里，不打断你，也不会自动打开聊天。</Text>
    </Surface>
    {error ? <Notice danger>{error}</Notice> : null}
    {feedback ? <Notice>{feedback}</Notice> : null}
    {topics.length ? <View style={styles.upcoming}>
      <SectionLabel title="关注中的话题" />
      {topics.map(topic => <View key={topic.id}>
        <View style={styles.appointment}>
          <View style={styles.appointmentText}>
            <Text style={styles.body}>{topic.reason}</Text>
            <Text style={styles.time}>{topic.state === "authorized"
              ? `预计 ${new Date(topic.nextEligibleAt).toLocaleDateString("zh-CN", { timeZone: timezone })} 后可跟进`
              : "已暂停"}</Text>
          </View>
          {topic.state === "authorized" ? <IconAction label="编辑跟进" icon={Pencil}
            disabled={topicBusy !== null || editingTopic !== null} onPress={() => editTopic(topic)} /> : null}
          <Action label={topic.state === "authorized" ? "停止跟进" : "重新开启"}
            variant="quiet" busy={topicBusy === topic.id}
            disabled={topicBusy !== null || editingTopic?.id === topic.id}
            onPress={() => changeTopic(topic)} />
        </View>
        {editingTopic?.id === topic.id ? <View style={styles.topicForm}>
          <Field label="跟进缘由" value={topicReason} onChangeText={setTopicReason}
            maxLength={200} error={topicReasonError} />
          <Field label={`下次可跟进日期 · ${timezone} 09:00`} value={topicDate}
            onChangeText={setTopicDate} keyboardType="numbers-and-punctuation"
            placeholder="YYYY-MM-DD" maxLength={10} error={topicDateError} />
          {topicError ? <Notice danger>{topicError}</Notice> : null}
          <View style={styles.actions}>
            <Action label="保存修改" busy={topicBusy === topic.id} onPress={() => void saveTopic()} />
            <Action label="取消" variant="quiet" disabled={topicBusy !== null}
              onPress={() => setEditingTopic(null)} />
          </View>
        </View> : null}
      </View>)}
    </View> : null}
    {appointments.length ? <View style={styles.upcoming}>
      <SectionLabel title="待到期约定" />
      {appointments.map(item => <View key={item.id} style={styles.appointment}>
        <View style={styles.appointmentText}>
          <Text style={styles.body}>{item.title}</Text>
          <Text style={styles.time}>{new Date(item.dueAt).toLocaleString("zh-CN", { timeZone: item.timezone, hour12: false })} · {item.timezone}</Text>
        </View>
        <Action label="取消约定" variant="quiet" busy={cancelingId === item.id}
          disabled={cancelingId !== null} onPress={() => cancelAppointment(item)} />
      </View>)}
    </View> : null}
    {!items.length ? <Empty title="还没有站内消息" /> : items.map(item => <View key={item.id}>
      <View style={styles.item}>
        <Text style={styles.state}>{item.state === "unread" ? "未读" : item.state === "snoozed" ?
          `稍后 · ${item.snoozedUntil ? new Date(item.snoozedUntil).toLocaleDateString("zh-CN", { timeZone: timezone }) : ""}` :
          item.state === "handled" ? "已处理" : "已读"}</Text>
        <View style={styles.itemMeta}><Text style={styles.sourceLabel}>{item.sourceKind === "followup_topic" ? "主动跟进" : "一次性约定"}</Text>
          <Text style={styles.body}>{item.body}</Text></View>
        <Text style={styles.time}>{new Date(item.scheduledAt).toLocaleDateString("zh-CN")}</Text>
        <View style={styles.actions}>
          {item.state === "unread" ? <Action label="标记已读" icon={Check} variant="quiet"
            busy={itemBusy === item.id && itemAction === "read"} disabled={itemBusy !== null}
            onPress={() => void act(item, "read")} /> : null}
          {item.conversationId && item.state !== "handled" ? <Action label="继续聊" icon={ArrowRight}
            busy={itemBusy === item.id && itemAction === "continue"}
            disabled={itemBusy !== null} onPress={() => void act(item, "continue")} /> : null}
          {item.sourceKind === "followup_topic" && item.conversationId && item.state !== "handled" ? <Action label="稍后" icon={Clock3} variant="quiet"
            disabled={itemBusy !== null} onPress={() => { setSnoozingId(item.id); setSnoozeDate(""); }} /> : null}
          {item.sourceKind === "followup_topic" && item.conversationId ?
            <Action label="停止话题" variant="danger" busy={itemBusy === item.id && itemAction === "stop"}
              disabled={itemBusy !== null} onPress={() => stop(item)} /> : null}
          {item.state !== "handled" ? <Action label="关闭" variant="quiet" disabled={itemBusy !== null}
            busy={itemBusy === item.id && itemAction === "dismiss"}
            onPress={() => void act(item, "dismiss")} /> : null}
        </View>
        {snoozingId === item.id ? <View style={styles.snooze}>
          <Field label={`哪天再看 · ${timezone} 09:00`} placeholder="YYYY-MM-DD" keyboardType="numbers-and-punctuation"
            value={snoozeDate} onChangeText={setSnoozeDate} maxLength={10} />
          <View style={styles.actions}>
            <Action label="确认稍后" onPress={() => void snooze(item.id)} busy={itemBusy === item.id}
              disabled={itemBusy !== null} />
            <Action label="取消" variant="quiet" disabled={itemBusy !== null}
              onPress={() => setSnoozingId(null)} />
          </View>
        </View> : null}
      </View><Separator />
    </View>)}
  </Page>;
}

const styles = StyleSheet.create({
  hero: { marginBottom: 22, padding: 18, minHeight: 150 },
  heroEyebrow: { color: colors.primarySoft, fontSize: 10, letterSpacing: 0.8, fontWeight: "800" },
  heroTitle: { color: colors.onPrimary, fontSize: 23, lineHeight: 26, fontWeight: "800", letterSpacing: -0.6, marginTop: 16 },
  heroDetail: { color: colors.onPrimaryMuted, fontSize: 12, lineHeight: 18, marginTop: 10, maxWidth: 280 },
  item: { padding: 16, gap: 9, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: 18, marginBottom: 10 },
  state: { color: colors.attention, fontSize: 11, fontWeight: "800", letterSpacing: 0.3 },
  itemMeta: { gap: 6 },
  sourceLabel: { color: colors.primary, fontSize: 11, fontWeight: "800" },
  body: { color: colors.ink, fontSize: 16, lineHeight: 25 },
  time: { color: colors.muted, fontSize: 13 },
  actions: { flexDirection: "row", gap: 8, flexWrap: "wrap", marginTop: 6 },
  snooze: { paddingTop: 12 },
  upcoming: { marginBottom: 24, gap: 8 },
  sectionTitle: { color: colors.ink, fontSize: 16, fontWeight: "800" },
  appointment: { padding: 14, borderWidth: 1, borderColor: colors.line, borderRadius: 16, backgroundColor: colors.surface,
    flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 10 },
  appointmentText: { flex: 1, minWidth: 200, gap: 4 },
  topicForm: { paddingVertical: 16 },
});

function localDate(value: string, timezone: string): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: timezone, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date(value));
  const part = (name: string) => parts.find(item => item.type === name)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}
