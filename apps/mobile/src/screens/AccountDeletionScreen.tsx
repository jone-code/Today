import { useEffect, useState } from "react";
import type { DataRequest } from "@today/contracts";
import { useSession } from "../session";
import { Action, Heading, Notice, Page, messageOf } from "../ui";

export function AccountDeletionScreen() {
  const { api, signOut } = useSession();
  const [task, setTask] = useState<DataRequest | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    const refresh = async () => {
      try {
        const tasks = await api.dataRequests();
        if (!active) return;
        const deletion = tasks.find(value => value.type === "delete_account");
        if (deletion) setTask(deletion);
        if (deletion?.status === "succeeded") await signOut();
        setError("");
      } catch (failure) { if (active) setError(messageOf(failure)); }
    };
    void refresh();
    const timer = setInterval(() => void refresh(), 5000);
    return () => { active = false; clearInterval(timer); };
  }, [api, signOut]);
  async function retry() {
    if (!task || busy) return;
    setBusy(true);
    try { setTask(await api.retryDataRequest(task.id)); setError(""); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setBusy(false); }
  }
  return <Page><Heading title="账户删除" />
    <Notice>{task?.status === "failed" ? "清理尚未完成。账户已停止普通访问，请重试剩余清理步骤。" : "正在清理你的记录、记忆、媒体与导出包。完成后会退出登录。"}</Notice>
    {error ? <Notice danger>{error}</Notice> : null}
    {task?.retryable ? <Action label="继续清理" busy={busy} onPress={() => void retry()} /> : null}
    <Action label="退出登录" variant="quiet" disabled={busy} onPress={() => void signOut()} />
  </Page>;
}
