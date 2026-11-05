import { useCallback, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { ChevronRight, Trash2 } from "lucide-react-native";
import type { Conversation } from "@today/contracts";
import { useSession, type RootStackParams } from "../App";
import { colors } from "../theme";
import { Empty, Heading, IconAction, Notice, Page, Separator, messageOf } from "../ui";

export function HistoryScreen() {
  const { api } = useSession();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParams>>();
  const [items, setItems] = useState<Conversation[]>([]);
  const [error, setError] = useState("");
  const refresh = useCallback(() => { void api.conversations().then(setItems).catch(e => setError(messageOf(e))); }, [api]);
  useFocusEffect(refresh);

  function remove(item: Conversation) {
    Alert.alert("删除这段对话？", "消息将不可见，相关的记忆和跟进也会失效。", [
      { text: "取消", style: "cancel" },
      { text: "删除", style: "destructive", onPress: () => {
        void api.deleteConversation(item.id).then(refresh).catch(e => setError(messageOf(e)));
      } },
    ]);
  }

  return <Page><Heading title="历史对话" />
    {error ? <Notice danger>{error}</Notice> : null}
    {!items.length ? <Empty title="还没有历史对话" /> : items.map(item => <View key={item.id}>
      <View style={styles.row}>
        <Pressable accessibilityRole="button" accessibilityLabel={`打开${item.title || "对话"}`}
          onPress={() => navigation.navigate("Conversation", { id: item.id })} style={({ pressed }) => [styles.open, pressed && { opacity: 0.6 }]}>
          <View style={{ flex: 1 }}><Text style={styles.title} numberOfLines={2}>{item.title || "新的对话"}</Text>
            <Text style={styles.detail} numberOfLines={1}>{item.messages[0]?.body || "还没有消息"}</Text></View>
          <ChevronRight size={19} color={colors.muted} />
        </Pressable>
        <IconAction icon={Trash2} label={`删除${item.title || "对话"}`} onPress={() => remove(item)} />
      </View><Separator />
    </View>)}
  </Page>;
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center" },
  open: { minHeight: 72, flex: 1, flexDirection: "row", alignItems: "center", gap: 10 },
  title: { color: colors.ink, fontSize: 16, fontWeight: "600" },
  detail: { color: colors.muted, fontSize: 13, marginTop: 6 },
});
