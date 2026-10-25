const vocabularyDeck = [
  "to distill an idea",
  "to articulate nuance",
  "a steep learning curve",
  "to gain traction",
  "to challenge the premise",
  "a compelling case",
  "to bridge the gap",
  "to think on your feet",
  "to weigh the trade-offs",
  "to be mindful of scope"
];

const state = {
  sessionId: "",
  currentQuestion: null,
  questionSet: [],
  practiced: [],
  skipped: [],
  queue: [],
  selectedTopics: new Set(),
  running: false,
  animating: false,
  loading: false
};

const landingView = document.getElementById("landing-view");
const trainingView = document.getElementById("training-view");
const questionDisplay = document.getElementById("question-display");
const questionWord = document.getElementById("question-word");
const questionTopic = document.getElementById("question-topic");
const practicedCount = document.getElementById("practiced-count");
const skippedCount = document.getElementById("skipped-count");
const summaryBackdrop = document.getElementById("summary-backdrop");
const summaryNote = document.getElementById("summary-note");
const summaryPracticedCount = document.getElementById("summary-practiced-count");
const summarySkippedCount = document.getElementById("summary-skipped-count");
const practicedList = document.getElementById("practiced-list");
const skippedList = document.getElementById("skipped-list");
const sessionError = document.getElementById("session-error");
const topicChips = [...document.querySelectorAll(".topic-chip")];

const startButton = document.getElementById("start-button");
const startButtonLabel = document.getElementById("start-button-label");
const nextButton = document.getElementById("next-button");
const skipButton = document.getElementById("skip-button");
const stopButton = document.getElementById("stop-button");
const restartButton = document.getElementById("restart-button");
const closeSummaryButton = document.getElementById("close-summary-button");

topicChips.forEach((chip) => {
  chip.addEventListener("click", () => toggleTopic(chip));
});
startButton.addEventListener("click", startSession);
restartButton.addEventListener("click", startSession);
nextButton.addEventListener("click", () => advanceQuestion("practiced"));
skipButton.addEventListener("click", () => advanceQuestion("skipped"));
stopButton.addEventListener("click", stopSession);
closeSummaryButton.addEventListener("click", closeSummary);
summaryBackdrop.addEventListener("click", (event) => {
  if (event.target === summaryBackdrop) {
    closeSummary();
  }
});

document.addEventListener("keydown", (event) => {
  if (!state.running || state.animating || state.loading) {
    if (event.key === "Escape" && !summaryBackdrop.hidden) {
      closeSummary();
    }
    return;
  }

  if (event.key === "ArrowRight" || event.key === "Enter") {
    advanceQuestion("practiced");
  } else if (event.key === "ArrowDown") {
    advanceQuestion("skipped");
  } else if (event.key === "Escape") {
    stopSession();
  }
});

async function startSession() {
  if (state.selectedTopics.size === 0 || state.loading) {
    return;
  }

  closeSummary();
  hideSessionError();
  setLoading(true);

  try {
    const response = await fetch("/api/training-sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        topics: [...state.selectedTopics],
        words: vocabularyDeck
      })
    });
    const payload = await readJson(response);

    if (!response.ok) {
      throw new Error(payload.error || "Could not prepare this training session.");
    }
    if (!Array.isArray(payload.questions) || payload.questions.length === 0) {
      throw new Error("The generated session did not contain any questions.");
    }

    resetSession();
    state.sessionId = payload.sessionId;
    state.questionSet = payload.questions;
    state.queue = shuffle([...state.questionSet]);
    state.running = true;

    toggleView("training");
    renderCounts();
    showNextQuestion(true);
  } catch (error) {
    toggleView("landing");
    showSessionError(
      error instanceof Error
        ? error.message
        : "Could not prepare this training session."
    );
  } finally {
    setLoading(false);
  }
}

function toggleTopic(chip) {
  const topic = chip.dataset.topic;

  if (state.selectedTopics.has(topic)) {
    state.selectedTopics.delete(topic);
  } else if (state.selectedTopics.size < 2) {
    state.selectedTopics.add(topic);
  }

  hideSessionError();
  renderTopicPicker();
}

function renderTopicPicker() {
  const selectedCount = state.selectedTopics.size;
  const selectionIsFull = selectedCount === 2;

  topicChips.forEach((chip) => {
    const isSelected = state.selectedTopics.has(chip.dataset.topic);
    chip.setAttribute("aria-pressed", String(isSelected));
    chip.disabled = state.loading || (selectionIsFull && !isSelected);
  });

  startButton.disabled = state.loading || selectedCount === 0;
}

function setLoading(isLoading) {
  state.loading = isLoading;
  startButton.setAttribute("aria-busy", String(isLoading));
  startButtonLabel.textContent = isLoading ? "Preparing session…" : "Start session";
  restartButton.disabled = isLoading;
  renderTopicPicker();
}

function resetSession() {
  state.sessionId = "";
  state.currentQuestion = null;
  state.questionSet = [];
  state.practiced = [];
  state.skipped = [];
  state.queue = [];
  state.running = false;
  state.animating = false;
  questionDisplay.textContent = "";
  questionWord.textContent = "";
  questionTopic.textContent = "";
  questionDisplay.classList.remove("is-visible");
}

function toggleView(viewName) {
  landingView.classList.toggle("is-active", viewName === "landing");
  trainingView.classList.toggle("is-active", viewName === "training");
}

function advanceQuestion(action) {
  if (!state.currentQuestion || state.animating) {
    return;
  }

  const practicedWord = state.currentQuestion.word;
  if (action === "practiced") {
    state.practiced.push(practicedWord);
  } else {
    state.skipped.push(practicedWord);
  }

  renderCounts();
  showNextQuestion();
}

function stopSession() {
  if (!state.running) {
    return;
  }

  state.running = false;
  renderSummary();
  toggleView("landing");
  summaryBackdrop.hidden = false;
}

function closeSummary() {
  summaryBackdrop.hidden = true;
}

function renderCounts() {
  practicedCount.textContent = String(state.practiced.length);
  skippedCount.textContent = String(state.skipped.length);
}

function renderSummary() {
  summaryPracticedCount.textContent = String(state.practiced.length);
  summarySkippedCount.textContent = String(state.skipped.length);
  summaryNote.textContent = state.currentQuestion
    ? `Stopped on “${state.currentQuestion.word}”.`
    : "Session finished.";

  populateWordList(practicedList, state.practiced, "Nothing practiced yet.");
  populateWordList(skippedList, state.skipped, "Nothing skipped.");
}

function populateWordList(container, words, emptyText) {
  container.replaceChildren();

  if (words.length === 0) {
    const emptyItem = document.createElement("span");
    emptyItem.className = "word-pill empty";
    emptyItem.textContent = emptyText;
    container.appendChild(emptyItem);
    return;
  }

  words.forEach((word) => {
    const item = document.createElement("span");
    item.className = "word-pill";
    item.textContent = word;
    container.appendChild(item);
  });
}

function showNextQuestion(isImmediate = false) {
  const nextQuestion = getNextQuestion();
  state.currentQuestion = nextQuestion;
  state.animating = true;
  questionDisplay.classList.remove("is-visible");

  const delay = isImmediate ? 120 : 180;
  window.setTimeout(() => {
    questionWord.textContent = nextQuestion.word;
    questionTopic.textContent = nextQuestion.topic;
    questionDisplay.textContent = nextQuestion.text;
    questionDisplay.classList.add("is-visible");
    state.animating = false;
  }, delay);
}

function getNextQuestion() {
  if (state.queue.length === 0) {
    state.queue = shuffle([...state.questionSet]);

    if (
      state.currentQuestion
      && state.queue[0]?.id === state.currentQuestion.id
      && state.queue.length > 1
    ) {
      [state.queue[0], state.queue[1]] = [state.queue[1], state.queue[0]];
    }
  }

  return state.queue.shift();
}

async function readJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

function showSessionError(message) {
  sessionError.textContent = message;
  sessionError.hidden = false;
}

function hideSessionError() {
  sessionError.hidden = true;
  sessionError.textContent = "";
}

function shuffle(items) {
  for (let index = items.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1));
    [items[index], items[randomIndex]] = [items[randomIndex], items[index]];
  }

  return items;
}
