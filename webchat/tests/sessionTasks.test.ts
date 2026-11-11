import assert from "node:assert/strict";
import test from "node:test";
import { SessionTasks } from "../src/sessionTasks.ts";

test("switching sessions isolates stop controls and preserves queued requests", () => {
  const tasks = new SessionTasks();
  tasks.start("a", "first");
  tasks.start("a", "queued");
  tasks.start("b", "other");
  tasks.observe("a", "queued", "completed");
  assert.equal(tasks.active("a"), "first");
  assert.equal(tasks.active("b"), "other");
  assert.equal(tasks.active("new"), null);
  tasks.observe("b", "first", "error");
  assert.equal(tasks.active("a"), "first");
});

test("completion before an approval receipt cannot resurrect a settled task", () => {
  const tasks = new SessionTasks();
  tasks.start("a", "run");
  tasks.observe("a", "run", "waiting_approval");
  assert.equal(tasks.active("a"), null);
  tasks.observe("a", "run", "completed");
  tasks.resume("a", "run");
  assert.equal(tasks.active("a"), null);
});

test("failed additional input leaves the original run controllable", () => {
  const tasks = new SessionTasks();
  tasks.start("a", "run");
  tasks.start("a", "failed-input");
  tasks.remove("failed-input");
  assert.equal(tasks.active("a"), "run");
});

test("restored task and approval resume remain scoped to their session", () => {
  const tasks = new SessionTasks();
  tasks.resume("a", "restored");
  tasks.observe("a", "restored", "waiting_approval");
  tasks.resume("a", "restored");
  assert.equal(tasks.active("a"), "restored");
  assert.equal(tasks.active("b"), null);
});
