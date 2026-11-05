import type { Conversation, ConversationMessage } from "@today/contracts";

export function mergeConversationHistory(previous: Conversation | null, updated: Conversation | null) {
  if (!previous || !updated || previous.id !== updated.id) return updated;
  const seen = new Set(updated.messages.map(message => message.id));
  return { ...updated, messages: [...updated.messages,
    ...previous.messages.filter(message => !seen.has(message.id))] };
}

export function appendOlderMessages(conversation: Conversation, older: ConversationMessage[]) {
  const seen = new Set(conversation.messages.map(message => message.id));
  return { ...conversation, messages: [...conversation.messages,
    ...older.filter(message => !seen.has(message.id))] };
}
