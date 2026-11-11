import { useState } from "react";
import type { MessageFeedback } from "../types";

export function MessageFeedbackActions({ feedback, onSave }: {
  feedback?: MessageFeedback;
  onSave: (rating: "up" | "down", note?: string) => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  async function save(rating: "up" | "down") {
    const note = window.prompt("反馈说明（可留空）", feedback?.note ?? "");
    if (note === null) return;
    setBusy(true);
    try { await onSave(rating, note); } finally { setBusy(false); }
  }
  return <div className="message-feedback" aria-label="回答反馈">
    <button type="button" disabled={busy} aria-pressed={feedback?.rating === "up"} onClick={() => void save("up")}>有帮助</button>
    <button type="button" disabled={busy} aria-pressed={feedback?.rating === "down"} onClick={() => void save("down")}>需改进</button>
    {feedback?.note && <span title={feedback.note}>{feedback.note}</span>}
  </div>;
}
