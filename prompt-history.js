// A durable outbox records exposure independently of the learner's self-assessment.
function createPromptHistory({ storage, fetcher, newId }) {
  const profileKey = "wordswith:learner-id";
  const pendingKey = "wordswith:pending-prompt-exposures:v1";
  let learnerId = storage.getItem(profileKey);
  if (!learnerId) {
    learnerId = newId();
    storage.setItem(profileKey, learnerId);
  }
  let inFlight = null;

  function pending() {
    const entries = JSON.parse(storage.getItem(pendingKey) || "[]");
    if (!Array.isArray(entries)) throw new Error("Could not read your prompt history.");
    return entries;
  }

  async function drain() {
    while (true) {
      const entry = pending()[0];
      if (!entry) return;
      const response = await fetcher("/api/dictionary-items/tasks", {
        method: "PATCH",
        headers: { "Content-Type": "application/json", "X-Learner-Id": learnerId },
        body: JSON.stringify(entry),
        keepalive: true
      });
      if (!response.ok) {
        throw new Error("Could not save your prompt history. Please try again before starting another session.");
      }
      // Read again: another tab or a newly displayed question may have added an entry.
      storage.setItem(pendingKey, JSON.stringify(pending().filter(item => item.taskId !== entry.taskId)));
    }
  }

  async function flush() {
    if (inFlight) return inFlight;
    inFlight = drain();
    try {
      await inFlight;
    } finally {
      inFlight = null;
    }
  }

  function record(question, level) {
    const entries = pending();
    if (!entries.some(item => item.taskId === question.id)) {
      entries.push({ word: question.word, taskId: question.id, level });
      storage.setItem(pendingKey, JSON.stringify(entries));
    }
    return flush();
  }

  return { learnerId, record, flush };
}

if (typeof module !== "undefined") module.exports = { createPromptHistory };
