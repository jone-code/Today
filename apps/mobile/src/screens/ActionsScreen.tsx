import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Image, Pressable, StyleSheet, Switch, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { randomUUID } from "expo-crypto";
import { Camera, Check, Pencil, Plus, RotateCcw, Trash2 } from "lucide-react-native";
import type { AgentAction, PunchHabitDto, ReminderDto, TodoDto } from "@today/contracts";
import { actionDraftKey, deleteActionDraftAfterWrites, readActionDraft, writeActionDraft, type ActionDraft } from "../actionDraft";
import { deletePhotoDraft, photoDraftExists, photoDraftUri, prunePhotoDrafts, savePhotoDraft } from "../punchPhotoDraft";
import { useSession } from "../App";
import { ApiRequestError } from "../api";
import { colors } from "../theme";
import { Action, Empty, Field, Heading, Notice, Page, Separator, messageOf } from "../ui";

type Mode = "todos" | "habits" | "reminders";
type HabitForm = { id?: string; requestId: string; title: string; description: string };
type ReminderForm = { id?: string; requestId: string; title: string; message: string; time: string; timezone: string };

function dateInZone(timezone: string) {
  try {
    const parts = new Intl.DateTimeFormat("en-US", { timeZone: timezone, year: "numeric",
      month: "2-digit", day: "2-digit" }).formatToParts(new Date());
    const value = (part: string) => parts.find(item => item.type === part)?.value;
    return `${value("year")}-${value("month")}-${value("day")}`;
  } catch {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  }
}

export function ActionsScreen() {
  const { api, session } = useSession();
  const draftKey = actionDraftKey(session.baseUrl, session.auth.user.id);
  const draft = useRef<ActionDraft>({ todo: null, habit: null, reminder: null, photo: null });
  const draftWrite = useRef<Promise<void>>(Promise.resolve());
  const restoreRevision = useRef(0);
  const [draftReady, setDraftReady] = useState(false);
  const [restoredKey, setRestoredKey] = useState("");
  const [draftError, setDraftError] = useState("");
  const [draftNotice, setDraftNotice] = useState("");
  const [mode, setMode] = useState<Mode>("todos");
  const [todos, setTodos] = useState<TodoDto[]>([]);
  const [habits, setHabits] = useState<PunchHabitDto[]>([]);
  const [reminders, setReminders] = useState<ReminderDto[]>([]);
  const [recentActions, setRecentActions] = useState<AgentAction[]>([]);
  const [date, setDate] = useState("");
  const [timezone, setTimezone] = useState("Asia/Shanghai");
  const [todoTitle, setTodoTitle] = useState("");
  const [todoRequestId, setTodoRequestId] = useState(randomUUID);
  const [habitForm, setHabitForm] = useState<HabitForm | null>(null);
  const [reminderForm, setReminderForm] = useState<ReminderForm | null>(null);
  const [photo, setPhoto] = useState<{ habitId: string; uri: string; date: string; requestId: string } | null>(null);
  const [photoMissing, setPhotoMissing] = useState(false);
  const punchRequestIds = useRef(new Map<string, string>());
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");

  const restoreDraft = useCallback(async () => {
    const revision = ++restoreRevision.current;
    setDraftReady(false);
    setDraftError("");
    try {
      await draftWrite.current.catch(() => {});
      const saved = await readActionDraft(draftKey);
      const photoExists = saved?.photo ? await photoDraftExists(draftKey, saved.photo.requestId) : false;
      const savedPhotoUri = saved?.photo ? await photoDraftUri(draftKey, saved.photo.requestId) : null;
      await prunePhotoDrafts(draftKey, saved?.photo?.requestId).catch(() => {});
      if (revision !== restoreRevision.current) return;
      draft.current = saved || { todo: null, habit: null, reminder: null, photo: null };
      setTodoTitle(saved?.todo?.title || "");
      setTodoRequestId(saved?.todo?.requestId || randomUUID());
      setHabitForm(saved?.habit || null);
      setReminderForm(saved?.reminder || null);
      setPhoto(saved?.photo && savedPhotoUri ? { ...saved.photo, uri: savedPhotoUri } : null);
      setPhotoMissing(!!saved?.photo && !photoExists);
      if (saved?.photo) setMode("habits");
      setDraftError("");
      setDraftNotice(saved ? "已恢复本机未完成的操作。" : "");
      setRestoredKey(draftKey);
      setDraftReady(true);
    } catch (failure) { if (revision === restoreRevision.current) setDraftError(messageOf(failure)); }
  }, [draftKey]);
  useEffect(() => {
    void restoreDraft();
    return () => { restoreRevision.current++; };
  }, [restoreDraft]);

  function persistDraft(next: ActionDraft) {
    draft.current = next;
    draftWrite.current = draftWrite.current.catch(() => {}).then(() => writeActionDraft(draftKey, next))
      .then(() => setDraftError(""), () => {
        setDraftError("草稿无法保存在本机，操作已暂停；请检查设备存储空间后重试。");
        throw new Error("草稿尚未安全保存，请重试。");
      });
    return draftWrite.current;
  }

  async function ensureDraftSaved() {
    try { await draftWrite.current; }
    catch { await persistDraft(draft.current); }
  }

  function editTodo(title: string) {
    const requestId = randomUUID();
    setTodoTitle(title);
    setTodoRequestId(requestId);
    setDraftNotice("");
    void persistDraft({ ...draft.current, todo: title ? { title, requestId } : null }).catch(() => {});
  }

  function setHabitDraft(form: HabitForm | null) {
    setHabitForm(form);
    setDraftNotice("");
    void persistDraft({ ...draft.current, habit: form && !form.id ? form : null }).catch(() => {});
  }

  function setReminderDraft(form: ReminderForm | null) {
    setReminderForm(form);
    setDraftNotice("");
    void persistDraft({ ...draft.current, reminder: form && !form.id ? form : null }).catch(() => {});
  }

  function confirmClearDraft() {
    Alert.alert("清除本机行动草稿？", "未确认的创建内容和重试记录可能会丢失。请先尝试重新读取。", [
      { text: "保留", style: "cancel" },
      { text: "清除草稿", style: "destructive", onPress: () => {
        restoreRevision.current++;
        setDraftReady(false);
        setDraftError("");
        draftWrite.current = deleteActionDraftAfterWrites(draftKey, draftWrite.current);
        void draftWrite.current.then(restoreDraft)
          .catch(() => setDraftError("无法清除本机草稿，请检查设备存储后重试。"));
      } },
    ]);
  }

  const refresh = useCallback(async () => {
    try {
      const [todoList, reminderList, actions, preferences] = await Promise.all([
        api.todos(), api.reminders(), api.actions(), api.followupPreferences(),
      ]);
      const day = dateInZone(preferences.timezone);
      const habitList = await api.habits(day);
      setTodos(todoList.items); setReminders(reminderList.items);
      setHabits(habitList.items); setRecentActions(actions); setDate(day);
      setTimezone(preferences.timezone); setError("");
    } catch (failure) { setError(messageOf(failure)); }
  }, [api]);
  useFocusEffect(useCallback(() => { void refresh(); }, [refresh]));

  async function run(key: string, operation: () => Promise<unknown>, success: string) {
    if (busy) return false;
    setBusy(key); setError(""); setFeedback("");
    try {
      await operation(); await refresh(); setFeedback(success);
      return true;
    } catch (failure) { setError(messageOf(failure)); return false; }
    finally { setBusy(null); }
  }

  async function undoAction(proposalId: string) {
    if (busy) return;
    setBusy(`undo:${proposalId}`); setError(""); setFeedback("");
    try {
      await api.undoAction(proposalId);
      await refresh();
      setFeedback("编辑已撤销");
    } catch (failure) {
      setError(failure instanceof ApiRequestError && failure.status === 409
        ? "内容已发生后续修改，无法撤销；请刷新后手动调整。" : messageOf(failure));
    } finally { setBusy(null); }
  }

  async function pickPhoto(habitId: string) {
    if (busy) return;
    setBusy(`photo:${habitId}`);
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) { setError("请允许访问相册后再选择打卡照片"); return; }
      const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images"], quality: 0.9 });
      if (picked.canceled || !picked.assets.length) return;
      const asset = picked.assets[0];
      const resized = await ImageManipulator.manipulateAsync(asset.uri,
        asset.width > 1400 ? [{ resize: { width: 1400 } }] : [],
        { compress: 0.8, format: ImageManipulator.SaveFormat.JPEG });
      const requestId = randomUUID();
      const previous = draft.current.photo;
      const uri = await savePhotoDraft(draftKey, requestId, resized.uri);
      try {
        await persistDraft({ ...draft.current, photo: { habitId, date, requestId } });
      } catch (failure) {
        await deletePhotoDraft(draftKey, requestId).catch(() => {});
        throw failure;
      }
      setPhoto({ habitId, uri, date, requestId });
      setPhotoMissing(false);
      if (previous) await deletePhotoDraft(draftKey, previous.requestId).catch(() => {});
      setError(""); setFeedback("");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(null); }
  }

  async function clearPhotoDraft() {
    const previous = draft.current.photo;
    if (!previous) return;
    try {
      await persistDraft({ ...draft.current, photo: null });
      setPhoto(null);
      setPhotoMissing(false);
      await deletePhotoDraft(draftKey, previous.requestId).catch(() => {});
    } catch (failure) { setError(messageOf(failure)); }
  }

  async function submitPhotoDraft(habitId: string) {
    const pending = photo;
    if (!pending || photoMissing) return;
    await run(`photo-save:${habitId}`, async () => {
      await ensureDraftSaved();
      if (!await photoDraftExists(draftKey, pending.requestId)) {
        setPhotoMissing(true);
        throw new Error("待上传照片已从设备移除，请取消草稿后重新选择照片。");
      }
      await api.punchWithPhoto(habitId, pending.date, pending.uri, pending.requestId);
      await persistDraft({ ...draft.current, photo: null });
      punchRequestIds.current.delete(`${habitId}:${pending.date}`);
      setPhoto(null);
      setPhotoMissing(false);
      await deletePhotoDraft(draftKey, pending.requestId).catch(() => {});
    }, "照片打卡已保存");
  }

  function confirmUnpunch(habit: PunchHabitDto) {
    Alert.alert(`取消 ${habit.title} 的打卡？`, habit.todayPhotoUrl ? "这次打卡的照片也会删除。" : undefined,
      [{ text: "保留", style: "cancel" }, { text: "取消打卡", style: "destructive",
        onPress: () => void run(`punch:${habit.id}`, () => api.unpunch(habit.id, date), "打卡已取消")
          .then(ok => { if (ok) punchRequestIds.current.delete(`${habit.id}:${date}`); }) }]);
  }

  function confirmDeleteHabit(habit: PunchHabitDto) {
    Alert.alert(`删除习惯「${habit.title}」？`, "这条习惯的所有打卡记录和照片都会删除。",
      [{ text: "保留", style: "cancel" }, { text: "删除", style: "destructive",
        onPress: () => void run(`habit:${habit.id}`, () => api.deleteHabit(habit.id), "习惯已删除") }]);
  }

  function confirmDeleteReminder(reminder: ReminderDto) {
    Alert.alert(`删除提醒「${reminder.title}」？`, "删除后不会再按此提醒投递。",
      [{ text: "保留", style: "cancel" }, { text: "删除", style: "destructive",
        onPress: () => void run(`reminder:${reminder.id}`, () => api.deleteReminder(reminder.id), "提醒已删除") }]);
  }

  function saveHabit() {
    if (!habitForm?.title.trim()) return;
    const form = habitForm;
    void run("habit-form", async () => {
      if (!form.id) await ensureDraftSaved();
      return form.id
        ? api.updateHabit(form.id, { title: form.title.trim(), description: form.description.trim() })
        : api.createHabit({ title: form.title.trim(), description: form.description.trim() }, form.requestId);
    }, form.id ? "习惯已更新" : "习惯已创建").then(ok => { if (ok) setHabitDraft(null); });
  }

  function saveReminder() {
    if (!reminderForm?.title.trim() || !reminderForm.message.trim()
        || !/^([01]\d|2[0-3]):[0-5]\d$/.test(reminderForm.time)) {
      setError("请填写标题、内容和有效时间（HH:mm）"); return;
    }
    const form = reminderForm;
    void run("reminder-form", async () => {
      if (!form.id) await ensureDraftSaved();
      return form.id
        ? api.updateReminder(form.id, { title: form.title.trim(), message: form.message.trim(),
          remindTime: form.time, timezone: form.timezone.trim() })
        : api.createReminder({ title: form.title.trim(), message: form.message.trim(),
          remindTime: form.time, timezone: form.timezone.trim(), enabled: true }, form.requestId);
    }, form.id ? "提醒已更新" : "提醒已创建").then(ok => { if (ok) setReminderDraft(null); });
  }

  if (!draftReady || restoredKey !== draftKey) return <Page><Heading title="行动" />
    <Notice danger>{draftError || "正在读取本机草稿"}</Notice>
    {draftError ? <View style={styles.buttons}>
      <Action label="重新读取" onPress={() => void restoreDraft()} />
      <Action label="清除本机草稿" variant="danger" onPress={confirmClearDraft} />
    </View> : null}
  </Page>;

  return <Page><Heading title="行动" />
    <View accessibilityRole="tablist" style={styles.tabs}>
      {([ ["todos", "待办"], ["habits", "习惯"], ["reminders", "提醒"] ] as const).map(([key, label]) =>
        <Pressable key={key} accessibilityRole="tab" accessibilityState={{ selected: mode === key }}
          accessibilityLabel={label} onPress={() => { setMode(key); setError(""); setFeedback(""); }}
          style={({ pressed }) => [styles.tab, mode === key && styles.tabActive, pressed && styles.pressed]}>
          <Text style={[styles.tabText, mode === key && styles.tabTextActive]}>{label}</Text>
        </Pressable>)}
    </View>
    {error ? <Notice danger>{error}</Notice> : null}
    {draftError ? <Notice danger>{draftError}</Notice> : null}
    {draftNotice ? <Notice>{draftNotice}</Notice> : null}
    {feedback ? <Notice>{feedback}</Notice> : null}
    {photo && !habits.some(habit => habit.id === photo.habitId) ? <View style={styles.photoDraft}>
      <Notice danger>照片草稿对应的习惯无法读取。取消草稿后可重新选择习惯。</Notice>
      <Action label="取消照片草稿" variant="quiet" disabled={!!busy} onPress={() => void clearPhotoDraft()} />
    </View> : null}
    {mode === "todos" ? <>
      <Field label="新增待办" placeholder="写下一件要做的事" value={todoTitle}
        onChangeText={editTodo} editable={!busy} maxLength={200} />
      <Action icon={Plus} label="加入待办" onPress={() => void run("todo-add", async () => {
        await ensureDraftSaved();
        return api.createTodo(todoTitle.trim(), todoRequestId);
      }, "待办已创建").then(ok => { if (ok) editTodo(""); })}
        busy={busy === "todo-add"} disabled={!!busy || !todoTitle.trim()} />
      <View style={styles.list}>
        {!todos.length ? <Empty title="暂时没有待办" /> : todos.map(item => <View key={item.id}>
          <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: item.status === "done", disabled: !!busy }}
            accessibilityLabel={`${item.status === "done" ? "取消完成" : "完成"} ${item.title}`}
            disabled={!!busy} onPress={() => void run(`todo:${item.id}`,
              () => api.setTodoStatus(item.id, item.status === "done" ? "open" : "done"), "待办已更新")}
            style={({ pressed }) => [styles.todo, pressed && styles.pressed]}>
            <View style={[styles.checkbox, item.status === "done" && styles.checked]}>
              {item.status === "done" ? <Check size={16} color={colors.onPrimary} /> : null}
            </View>
            <Text style={[styles.itemText, item.status === "done" && styles.done]}>{item.title}</Text>
          </Pressable><Separator />
        </View>)}
      </View>
    </> : null}
    {mode === "habits" ? <>
      <View style={styles.sectionTop}><Text style={styles.sectionTitle}>{date || "今天"}</Text>
        <Action label="新增习惯" icon={Plus} variant="quiet" disabled={!!busy}
          onPress={() => setHabitDraft({ requestId: randomUUID(), title: "", description: "" })} /></View>
      {habitForm ? <View style={styles.form}>
        <Field label="习惯名称" value={habitForm.title} editable={!busy} maxLength={128}
          onChangeText={title => setHabitDraft({ ...habitForm, title, requestId: randomUUID() })} />
        <Field label="描述" value={habitForm.description} editable={!busy} maxLength={512}
          onChangeText={description => setHabitDraft({ ...habitForm, description, requestId: randomUUID() })} />
        <View style={styles.buttons}>
          <Action label={habitForm.id ? "保存修改" : "创建习惯"} onPress={saveHabit}
            busy={busy === "habit-form"} disabled={!!busy || !habitForm.title.trim()} />
          <Action label="取消" variant="quiet" disabled={!!busy} onPress={() => setHabitDraft(null)} />
        </View>
      </View> : null}
      {!habits.length ? <Empty title="还没有习惯" /> : habits.map(habit =>
        <View key={habit.id} style={styles.rowItem}>
          <View style={styles.itemTop}><View style={styles.itemMain}>
            <Text style={styles.itemTitle}>{habit.title}</Text>
            <Text style={styles.itemMeta}>{habit.description || (habit.enabled ? "今天" : "已暂停")}
              {habit.enabled && habit.punchedToday ? ` · 已打卡${habit.streak ? ` · 连续 ${habit.streak} 天` : ""}` : ""}</Text>
          </View>
            <Switch accessibilityLabel={`${habit.enabled ? "暂停" : "启用"}${habit.title}`} value={habit.enabled}
              disabled={!!busy} onValueChange={enabled => void run(`habit:${habit.id}`,
                () => api.updateHabit(habit.id, { enabled }), enabled ? "习惯已启用" : "习惯已暂停")}
              trackColor={{ true: colors.primary, false: colors.line }} />
          </View>
          {habit.todayPhotoUrl ? <Image accessibilityLabel={`${habit.title}的打卡照片`}
            source={{ uri: `${session.baseUrl}${habit.todayPhotoUrl}`,
              headers: { Authorization: `Bearer ${session.auth.token}` } }} style={styles.photo} /> : null}
          {photo?.habitId === habit.id ? <View style={styles.photoDraft}>
            {photoMissing ? <Notice danger>待上传照片已从设备移除，请取消后重新选择。</Notice>
              : <Image source={{ uri: photo.uri }} accessibilityLabel="待保存的打卡照片" style={styles.photo} />}
            {photo.date !== date ? <Text style={styles.itemMeta}>这张照片将记录在 {photo.date}。</Text> : null}
            {habit.punchedToday && habit.todayPhotoUrl ? <Text style={styles.itemMeta}>保存会替换今天原有的照片。</Text> : null}
            <View style={styles.buttons}>
              <Action label="保存照片打卡" icon={Camera} busy={busy === `photo-save:${habit.id}`}
                disabled={!!busy || photoMissing} onPress={() => void submitPhotoDraft(habit.id)} />
              <Action label="取消" variant="quiet" disabled={!!busy} onPress={() => void clearPhotoDraft()} />
            </View>
          </View> : null}
          <View style={styles.buttons}>
            {habit.enabled ? habit.punchedToday
              ? <Action label="取消打卡" variant="quiet" disabled={!!busy} onPress={() => confirmUnpunch(habit)} />
              : <Action label="打卡" icon={Check} disabled={!!busy} busy={busy === `punch:${habit.id}`}
                onPress={() => {
                  const key = `${habit.id}:${date}`;
                  const requestId = punchRequestIds.current.get(key) || randomUUID();
                  punchRequestIds.current.set(key, requestId);
                  void run(`punch:${habit.id}`, () => api.punch(habit.id, date, requestId), "打卡已记录")
                    .then(ok => { if (ok) punchRequestIds.current.delete(key); });
                }} /> : null}
            {habit.enabled ? <Action label={habit.todayPhotoUrl ? "替换照片" : "照片打卡"}
              icon={Camera} variant="quiet" disabled={!!busy} onPress={() => void pickPhoto(habit.id)} /> : null}
            <Action label="编辑" icon={Pencil} variant="quiet" disabled={!!busy}
              onPress={() => setHabitDraft({ id: habit.id, requestId: randomUUID(), title: habit.title, description: habit.description || "" })} />
            <Action label="删除" icon={Trash2} variant="danger" disabled={!!busy}
              onPress={() => confirmDeleteHabit(habit)} />
          </View><Separator />
        </View>)}
    </> : null}
    {mode === "reminders" ? <>
      <View style={styles.sectionTop}><Text style={styles.sectionTitle}>每日提醒</Text>
        <Action label="新增提醒" icon={Plus} variant="quiet" disabled={!!busy}
          onPress={() => setReminderDraft({ requestId: randomUUID(), title: "", message: "", time: "09:00", timezone })} /></View>
      {reminderForm ? <View style={styles.form}>
        <Field label="标题" value={reminderForm.title} editable={!busy} maxLength={128}
          onChangeText={title => setReminderDraft({ ...reminderForm, title, requestId: randomUUID() })} />
        <Field label="提醒内容" value={reminderForm.message} editable={!busy} maxLength={512}
          onChangeText={message => setReminderDraft({ ...reminderForm, message, requestId: randomUUID() })} />
        <Field label="每天的时间（HH:mm）" value={reminderForm.time} editable={!busy} maxLength={5}
          keyboardType="numbers-and-punctuation" onChangeText={time => setReminderDraft({ ...reminderForm, time, requestId: randomUUID() })} />
        <Field label="时区" value={reminderForm.timezone} editable={!busy} maxLength={64}
          autoCapitalize="none" onChangeText={zone => setReminderDraft({ ...reminderForm, timezone: zone, requestId: randomUUID() })} />
        <View style={styles.buttons}>
          <Action label={reminderForm.id ? "保存修改" : "创建提醒"} onPress={saveReminder}
            busy={busy === "reminder-form"} disabled={!!busy || !reminderForm.title.trim()
              || !reminderForm.message.trim() || !reminderForm.timezone.trim()} />
          <Action label="取消" variant="quiet" disabled={!!busy} onPress={() => setReminderDraft(null)} />
        </View>
      </View> : null}
      {!reminders.length ? <Empty title="还没有每日提醒" /> : reminders.map(reminder =>
        <View key={reminder.id} style={styles.rowItem}>
          <View style={styles.itemTop}><View style={styles.itemMain}>
            <Text style={styles.itemTitle}>{reminder.title}</Text>
            <Text style={styles.itemMeta}>每天 {reminder.remindTime} · {reminder.timezone}
              {!reminder.enabled ? " · 已暂停" : ""}</Text>
            <Text style={styles.itemMeta}>{reminder.message}</Text>
          </View>
            <Switch accessibilityLabel={`${reminder.enabled ? "暂停" : "启用"}${reminder.title}提醒`}
              value={reminder.enabled} disabled={!!busy}
              onValueChange={enabled => void run(`reminder:${reminder.id}`,
                () => api.updateReminder(reminder.id, { enabled }), enabled ? "提醒已启用" : "提醒已暂停")}
              trackColor={{ true: colors.primary, false: colors.line }} />
          </View>
          <View style={styles.buttons}>
            <Action label="编辑" icon={Pencil} variant="quiet" disabled={!!busy}
              onPress={() => setReminderDraft({ id: reminder.id, requestId: randomUUID(), title: reminder.title,
                message: reminder.message, time: reminder.remindTime, timezone: reminder.timezone })} />
            <Action label="删除" icon={Trash2} variant="danger" disabled={!!busy}
              onPress={() => confirmDeleteReminder(reminder)} />
          </View><Separator />
        </View>)}
    </> : null}
    {recentActions.length ? <View style={styles.history}>
      <Text style={styles.historyTitle}>Agent 操作记录</Text>
      {recentActions.slice(0, 10).map(item => <View key={item.proposalId} style={styles.historyItem}>
        <Text style={styles.historySummary}>{item.summary}</Text>
        <Text style={styles.itemMeta}>{item.result === "revoked" ? "来源已删除" :
          item.result === "undone" ? "已撤销" : actionName(item.kind)} · {new Date(item.executedAt).toLocaleString("zh-CN")}</Text>
        {item.undoable ? <Action label="撤销" icon={RotateCcw} variant="quiet"
          disabled={busy !== null} busy={busy === `undo:${item.proposalId}`}
          onPress={() => Alert.alert("撤销这次编辑？",
            "仅恢复编辑前的设置；如果内容后来变过，将不会覆盖新修改。已投递的提醒不会撤回。", [
              { text: "取消", style: "cancel" },
              { text: "撤销", onPress: () => void undoAction(item.proposalId) },
            ])} /> : null}
      </View>)}
    </View> : null}
  </Page>;
}

function actionName(kind: string) {
  return ({ memory: "记忆已保存", followup: "关注已开启", todo: "待办已创建", todo_edit: "待办已更新",
    habit: "习惯已创建", habit_edit: "习惯已更新", habit_punch: "打卡已记录", habit_unpunch: "打卡已取消",
    daily_reminder: "每日提醒已设置", daily_reminder_edit: "每日提醒已更新",
    one_off_reminder: "单次约定已保存" } as Record<string, string>)[kind] || "已完成";
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", gap: 8, marginBottom: 24 },
  tab: { flex: 1, minHeight: 48, borderWidth: 1, borderColor: colors.line, borderRadius: 7,
    alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  tabActive: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  tabText: { color: colors.muted, fontSize: 15, fontWeight: "600" },
  tabTextActive: { color: colors.primary },
  pressed: { opacity: 0.65 },
  list: { marginTop: 20 }, todo: { minHeight: 50, flexDirection: "row", gap: 14, alignItems: "center" },
  checkbox: { width: 24, height: 24, borderWidth: 2, borderColor: colors.primary, borderRadius: 5,
    alignItems: "center", justifyContent: "center" },
  checked: { backgroundColor: colors.primary },
  itemText: { flex: 1, color: colors.ink, fontSize: 16, lineHeight: 24 },
  done: { textDecorationLine: "line-through", color: colors.muted },
  sectionTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8, marginBottom: 16 },
  sectionTitle: { color: colors.ink, fontSize: 19, fontWeight: "700", flexShrink: 1 },
  rowItem: { gap: 12 }, itemTop: { flexDirection: "row", alignItems: "center", gap: 12, minHeight: 48 },
  itemMain: { flex: 1, minWidth: 0, gap: 4 },
  itemTitle: { color: colors.ink, fontSize: 16, lineHeight: 24, fontWeight: "600" },
  itemMeta: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  buttons: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  form: { paddingVertical: 16, marginBottom: 16, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.line },
  photoDraft: { gap: 12, paddingVertical: 8 }, photo: { width: 96, height: 96, borderRadius: 7 },
  history: { marginTop: 32, gap: 12 }, historyTitle: { color: colors.ink, fontSize: 16, fontWeight: "700" },
  historyItem: { borderBottomWidth: 1, borderBottomColor: colors.line, paddingVertical: 10, gap: 4 },
  historySummary: { color: colors.ink, fontSize: 15, lineHeight: 22 },
});
