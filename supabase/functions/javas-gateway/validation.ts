export type ChatRole = "user" | "assistant";

export interface ChatMessage {
  role: ChatRole;
  content: string;
}

export const MAX_MESSAGES = 40;
export const MAX_MESSAGE_CHARS = 12_000;
export const MAX_REQUEST_BYTES = 64 * 1024;

/** Accept only a bounded conversation payload; system prompts are server-owned. */
export function isValidMessages(value: unknown): value is ChatMessage[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > MAX_MESSAGES) {
    return false;
  }

  for (const item of value) {
    if (item === null || typeof item !== "object") return false;
    const message = item as Record<string, unknown>;
    if (message.role !== "user" && message.role !== "assistant") return false;
    if (typeof message.content !== "string") return false;
    if (message.content.trim().length === 0 || message.content.length > MAX_MESSAGE_CHARS) {
      return false;
    }
  }

  return value[value.length - 1].role === "user";
}

/** Fail closed: only the single server-configured Creator identity is allowed. */
export function isCreatorAllowed(userId: string, configuredCreatorId: string | undefined): boolean {
  return configuredCreatorId !== undefined &&
    configuredCreatorId.trim().length > 0 &&
    userId.length > 0 &&
    userId === configuredCreatorId.trim();
}
