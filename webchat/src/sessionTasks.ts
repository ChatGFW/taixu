/** Keep every outstanding request scoped to its conversation, including queued successors. */
export class SessionTasks {
  private tasks = new Map<string, { session: string; waiting: boolean }>();
  private settled = new Set<string>();
  start(session: string, task: string) { this.tasks.set(task, { session, waiting: false }); }
  active(session: string): string | null {
    return [...this.tasks].reverse().find(([, value]) => value.session === session && !value.waiting)?.[0] ?? null;
  }
  remove(task: string) {
    this.tasks.delete(task);
    this.settled.add(task);
    if (this.settled.size > 256) this.settled.delete(this.settled.values().next().value!);
  }
  observe(session: string, task: string, kind: string) {
    const entry = this.tasks.get(task);
    if (entry && entry.session !== session) return;
    if (kind === "completed" || kind === "error") this.remove(task);
    else if (kind === "waiting_approval" && entry) entry.waiting = true;
  }
  resume(session: string, task: string) {
    if (this.settled.has(task)) return;
    this.tasks.set(task, { session, waiting: false });
  }
}
