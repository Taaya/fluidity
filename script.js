const vocabularyStorageKey = "wordswith:vocabulary-preview";
const lastSessionStorageKey = "wordswith:last-session";
const vocabularyBackupKey = "wordswith:previous-vocabulary";
const onboardingSeenStorageKey = "wordswith:onboarding-seen:v1";
const profileStorageKeys = [vocabularyStorageKey, lastSessionStorageKey,
  "wordswith:learner-id", "wordswith:pending-prompt-exposures:v1"];
const defaultEnglishLevel = "Upper-Intermediate";
const inputCharacterLimit = 1000;
const sessionSize = 5;
const promptHistory = createPromptHistory({
  storage: window.localStorage,
  fetcher: window.fetch.bind(window),
  newId: () => crypto.randomUUID()
});

const state = {
  vocabularyItems: loadVocabularyItems(),
  englishLevel: defaultEnglishLevel,
  lastSession: loadLastSession(),
  sessionId: "",
  sessionLevel: "",
  sessionItems: [],
  pendingResults: new Map(),
  revealedItemIds: new Set(),
  questionPools: new Map(),
  questionPositions: new Map(),
  currentIndex: 0,
  currentQuestion: null,
  results: { used: [], needsPractice: [] },
  running: false,
  animating: false,
  loading: false,
  preparation: null
};

const homeView = document.getElementById("home-view");
const previewView = document.getElementById("preview-view");
const previewChips = document.getElementById("preview-chips");
const readyButton = document.getElementById("ready-button");
const previewError = document.getElementById("preview-error");
const wordSpoiler = document.getElementById("word-spoiler");
const trainingView = document.getElementById("training-view");
const summaryView = document.getElementById("summary-view");
const homeTitle = document.getElementById("home-title");
const homeLead = document.getElementById("home-lead");
const vocabularyList = document.getElementById("vocabulary-list");
const statusSummary = document.getElementById("status-summary");
const newCount = document.getElementById("new-count");
const buildingCount = document.getElementById("building-count");
const comfortableCount = document.getElementById("comfortable-count");
const wordImporter = document.getElementById("word-importer");
const wordInput = document.getElementById("word-input");
const inputStatus = document.getElementById("input-status");
const addWordButton = document.getElementById("add-word-button");
const sessionError = document.getElementById("session-error");
const practiceButton = document.getElementById("practice-button");
const practiceButtonLabel = document.getElementById("practice-button-label");
const questionWord = document.getElementById("question-word");
const questionDisplay = document.getElementById("question-display");
const progressLabel = document.getElementById("progress-label");
const progressFill = document.getElementById("progress-fill");
const usedItButton = document.getElementById("used-it-button");
const needsPracticeButton = document.getElementById("needs-practice-button");
const skipPromptButton = document.getElementById("skip-prompt-button");
const leaveDialog = document.getElementById("leave-dialog");
const leaveSessionButton = document.getElementById("leave-session-button");
const summaryTitle = document.getElementById("summary-title");
const summaryUsedCount = document.getElementById("summary-used-count");
const summaryNeedsCount = document.getElementById("summary-needs-count");
const usedList = document.getElementById("used-list");
const needsList = document.getElementById("needs-list");
const doneForNowButton = document.getElementById("done-for-now-button");
const practiceAgainButton = document.getElementById("practice-again-button");
const brandButton = document.getElementById("brand-button");
const howItWorksButton = document.getElementById("how-it-works-button");
const onboardingDialog = document.getElementById("onboarding-dialog");
const onboardingPrimaryLabel = document.getElementById("onboarding-primary-label");
const wordToast = document.getElementById("word-toast");
const wordToastMessage = document.getElementById("word-toast-message");
const toastClose = document.getElementById("toast-close");
let toastTimer = null;
const shareDialog = document.getElementById("share-dialog");
const shareImage = document.getElementById("share-image");
const shareDownloadButton = document.getElementById("share-download-button");
const shareNativeButton = document.getElementById("share-native-button");
const shareFeedback = document.getElementById("share-feedback");
let shareVersion = 0;
let shareImageUrl = null;
let shareImageFile = null;

document.getElementById("share-library-button").addEventListener("click", openShareProgress);
document.getElementById("share-close-button").addEventListener("click", () => shareDialog.close());
shareDownloadButton.addEventListener("click", downloadShareProgress);
shareNativeButton.addEventListener("click", shareProgressImage);
shareDialog.addEventListener("close", () => {
  shareVersion += 1;
  if (shareImageUrl) URL.revokeObjectURL(shareImageUrl);
  shareImageUrl = null;
  shareImageFile = null;
  shareImage.removeAttribute("src");
});

async function openShareProgress() {
  const progress = buildShareProgress(state.vocabularyItems);
  const version = ++shareVersion;
  shareImage.hidden = true;
  shareNativeButton.hidden = true;
  shareDownloadButton.disabled = true;
  shareFeedback.textContent = progress.practiced === 0
    ? "No recorded practice in the last 7 days. Complete a session to share your progress."
    : "Preparing your image...";
  shareDialog.showModal();
  if (!progress.practiced) return;
  try {
    const blob = await createProgressImage(progress);
    if (version !== shareVersion || !shareDialog.open) return;
    shareImageFile = new File([blob], "wordswith-progress.png", { type: "image/png" });
    shareImageUrl = URL.createObjectURL(blob);
    shareImage.src = shareImageUrl;
    shareImage.alt = `My progress in Wordswith. ${progress.period}. ${progress.practiced} words practiced. ${progress.used} words used in context. Practiced words: ${progress.words.join(", ")}.`;
    shareImage.hidden = false;
    shareDownloadButton.disabled = false;
    shareNativeButton.hidden = !(navigator.share && navigator.canShare?.({ files: [shareImageFile] }));
    shareFeedback.textContent = "";
  } catch {
    if (version === shareVersion) shareFeedback.textContent = "Could not create the image. Close this window and try again.";
  }
}

function downloadShareProgress() {
  if (!shareImageUrl) return;
  const link = document.createElement("a");
  link.href = shareImageUrl;
  link.download = "wordswith-progress.png";
  document.body.appendChild(link);
  link.click();
  link.remove();
}

async function shareProgressImage() {
  if (!shareImageFile) return;
  const version = shareVersion;
  shareNativeButton.disabled = true;
  try {
    await navigator.share({ files: [shareImageFile] });
  } catch (error) {
    if (version === shareVersion && error.name !== "AbortError") {
      shareFeedback.textContent = "Could not share the image. You can download it instead.";
    }
  } finally {
    shareNativeButton.disabled = false;
  }
}

document.getElementById("clear-vocabulary-button").addEventListener("click", clearVocabulary);
document.getElementById("restore-vocabulary-button").addEventListener("click", restoreVocabulary);

toastClose.addEventListener("click", dismissWordToast);

wordImporter.addEventListener("submit", handleWordImport);
wordInput.addEventListener("input", () => renderImportControls());
vocabularyList.addEventListener("change", handleVocabularyEdit);
vocabularyList.addEventListener("click", handleVocabularyClick);
practiceButton.addEventListener("click", () => startSession());
readyButton.addEventListener("click", beginPractice);
document.getElementById("preview-back-button").addEventListener("click", showHome);
wordSpoiler.addEventListener("click", revealWord);
usedItButton.addEventListener("click", () => assessCurrentWord("used"));
needsPracticeButton.addEventListener("click", () => assessCurrentWord("needs-practice"));
skipPromptButton.addEventListener("click", tryAnotherPrompt);
leaveSessionButton.addEventListener("click", requestLeaveSession);
document.getElementById("continue-practice-button").addEventListener("click", () => leaveDialog.close());
document.getElementById("confirm-leave-button").addEventListener("click", () => {
  leaveDialog.close();
  leaveSession();
});
doneForNowButton.addEventListener("click", showHome);
practiceAgainButton.addEventListener("click", () => startSession());
brandButton.addEventListener("click", requestLeaveSession);
howItWorksButton.addEventListener("click", openOnboarding);
document.getElementById("onboarding-close-button").addEventListener("click", () => onboardingDialog.close());
document.getElementById("onboarding-later-button").addEventListener("click", () => onboardingDialog.close());
document.getElementById("onboarding-primary-button").addEventListener("click", () => {
  focusWordInputAfterOnboarding = true;
  onboardingDialog.close();
});
onboardingDialog.addEventListener("click", (event) => {
  if (event.target === onboardingDialog) onboardingDialog.close();
});
onboardingDialog.addEventListener("close", () => {
  rememberOnboardingSeen();
  if (!focusWordInputAfterOnboarding) return;
  focusWordInputAfterOnboarding = false;
  window.setTimeout(() => {
    wordInput.scrollIntoView?.({ behavior: "smooth", block: "center" });
    wordInput.focus({ preventScroll: true });
  }, 0);
});

let focusWordInputAfterOnboarding = false;

function openOnboarding() {
  onboardingPrimaryLabel.textContent = state.vocabularyItems.length
    ? "Add more words"
    : "Add my first words";
  if (!onboardingDialog.open) onboardingDialog.showModal();
}

function rememberOnboardingSeen() {
  try {
    window.localStorage.setItem(onboardingSeenStorageKey, "true");
  } catch {
    // The permanent How it works link remains available when storage is blocked.
  }
}

function showFirstVisitOnboarding() {
  try {
    if (window.localStorage.getItem(onboardingSeenStorageKey)) return;
  } catch {
    // Show the introduction when browser storage cannot be read.
  }
  openOnboarding();
}

document.addEventListener("DOMContentLoaded", showFirstVisitOnboarding);

document.addEventListener("keydown", (event) => {
  if (event.key !== "Escape") {
    return;
  }

  if (state.running && !leaveDialog.open) {
    event.preventDefault();
    requestLeaveSession();
  }
});

async function startSession() {
  if (state.loading) {
    return;
  }

  const selectedItems = selectSessionItems();
  if (selectedItems.length === 0) {
    showSessionError("Add at least one word or phrase before starting a session.");
    wordInput.focus();
    return;
  }

  hideSessionError();
  state.running = true;
  state.currentQuestion = null;
  state.sessionItems = [...selectedItems];
  state.preparation = { items: selectedItems, level: state.englishLevel, readyRequested: false, error: false };
  previewError.hidden = true;
  setLoading(true);
  renderReadyButton();
  renderSessionPreview();
  return preparePreviewQuestions(state.preparation);
}

async function preparePreviewQuestions(preparation) {
  try {
    await promptHistory.flush();
    if (state.preparation !== preparation) return;
    const response = await fetch("/api/training-sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Learner-Id": promptHistory.learnerId },
      body: JSON.stringify({
        words: preparation.items.map((item) => item.text),
        sessionSize: preparation.items.length,
        level: preparation.level
      })
    });
    const payload = await readJson(response);
    if (state.preparation !== preparation) return;

    if (!response.ok) {
      throw new Error(payload.error || "Could not prepare this speaking session.");
    }
    if (!Array.isArray(payload.questions) || payload.questions.length === 0) {
      throw new Error("The session did not contain any speaking prompts.");
    }

    const preparedWords = new Set((payload.words || []).map(normalizedKey));
    const missingItems = preparation.items.filter(item => !preparedWords.has(normalizedKey(item.text)));
    if (missingItems.length) {
      throw new Error(`Could not prepare prompts for ${missingItems.map(item => `“${item.text}”`).join(", ")}. Try again or go back to your words.`);
    }
    prepareSession(preparation.items, payload);
    state.sessionLevel = preparation.level;
  } catch (error) {
    if (state.preparation !== preparation) return;
    preparation.error = true;
    previewError.textContent = error instanceof Error ? error.message : "Could not prepare this speaking session.";
    previewError.hidden = false;
  } finally {
    // Back or a newer session invalidates this request, including its error/loading state.
    if (state.preparation === preparation) {
      setLoading(false);
      renderReadyButton();
      if (!preparation.error && preparation.readyRequested) beginPractice();
    }
  }
}

function prepareSession(selectedItems, payload) {
  const questionsByWord = new Map();
  payload.questions.forEach((question) => {
    const key = normalizedKey(question.word);
    const existing = questionsByWord.get(key) || [];
    existing.push(question);
    questionsByWord.set(key, existing);
  });

  const questionPools = new Map();
  selectedItems.forEach((item) => {
    const questions = questionsByWord.get(normalizedKey(item.text));
    if (!questions?.length) {
      throw new Error(`No speaking prompt was prepared for “${item.text}”.`);
    }
    questionPools.set(item.id, shuffle([...questions]));
  });

  state.sessionId = payload.sessionId || "";
  state.sessionLevel = state.englishLevel;
  state.sessionItems = shuffle([...selectedItems]);
  state.revealedItemIds = new Set();
  state.pendingResults = new Map();
  state.questionPools = questionPools;
  state.questionPositions = new Map(selectedItems.map((item) => [item.id, 0]));
  state.currentIndex = 0;
  state.currentQuestion = null;
  state.results = { used: [], needsPractice: [] };
  state.running = true;
  state.animating = false;
}

function renderSessionPreview() {
  previewChips.replaceChildren();
  shuffle([...state.sessionItems]).forEach(item => {
    const chip = document.createElement("li");
    chip.textContent = item.text;
    previewChips.appendChild(chip);
  });
  showView("preview");
  previewView.focus();
}

function beginPractice() {
  if (!state.running || state.currentQuestion || !previewView.classList.contains("is-active")) return;
  if (state.loading) {
    state.preparation.readyRequested = true;
    renderReadyButton();
    return;
  }
  if (state.preparation?.error) {
    state.preparation = { ...state.preparation, error: false, readyRequested: true };
    previewError.hidden = true;
    setLoading(true);
    renderReadyButton();
    return preparePreviewQuestions(state.preparation);
  }
  showView("training");
  showCurrentQuestion(true);
}

function renderReadyButton() {
  const waiting = state.loading && state.preparation?.readyRequested;
  readyButton.disabled = Boolean(waiting);
  readyButton.setAttribute("aria-busy", String(Boolean(waiting)));
  readyButton.textContent = waiting ? "Preparing…" : state.preparation?.error ? "Try again" : "Ready";
}

function revealWord() {
  if (!state.running || state.animating || !state.currentQuestion) return;
  const item = state.sessionItems[state.currentIndex];
  state.revealedItemIds.add(item.id);
  questionWord.textContent = item.text;
  questionWord.hidden = false;
  wordSpoiler.setAttribute("aria-expanded", "true");
  wordSpoiler.hidden = true;
  questionWord.focus();
}

function selectSessionItems(onlyItemIds = null, limit = sessionSize) {
  const requestedIds = Array.isArray(onlyItemIds) ? new Set(onlyItemIds) : null;
  const ranked = state.vocabularyItems
    .filter(item => !requestedIds || requestedIds.has(item.id))
    .sort(comparePracticeRecency);
  // An explicit retry keeps the user's chosen subset. Default practice reserves
  // two places for new words and three for previously assessed words.
  if (requestedIds) return ranked.slice(0, limit);
  const preferred = [
    ...ranked.filter(item => item.status === "new").slice(0, 2),
    ...ranked.filter(item => item.status !== "new").slice(0, 3)
  ];
  const preferredIds = new Set(preferred.map(item => item.id));
  // Fill missing places from the other group. Practice uses exactly the five
  // selected words so the preview stays consistent with the questions.
  return [...preferred, ...ranked.filter(item => !preferredIds.has(item.id))].slice(0, limit);
}

function comparePracticeRecency(first, second) {
  const firstTime = Date.parse(first.lastPracticedAt || first.addedAt) || 0;
  const secondTime = Date.parse(second.lastPracticedAt || second.addedAt) || 0;
  return firstTime - secondTime;
}

function showCurrentQuestion(isImmediate = false) {
  const item = state.sessionItems[state.currentIndex];
  if (!item) {
    finishSession();
    return;
  }

  const pool = state.questionPools.get(item.id);
  const position = state.questionPositions.get(item.id) || 0;
  const nextQuestion = pool[position % pool.length];
  state.currentQuestion = nextQuestion;
  state.animating = true;
  questionWord.textContent = "";
  questionWord.hidden = true;
  wordSpoiler.setAttribute("aria-expanded", "false");
  wordSpoiler.textContent = "Reveal word";
  wordSpoiler.hidden = false;
  setAssessmentDisabled(true);
  questionDisplay.classList.remove("is-visible");
  skipPromptButton.hidden = position >= pool.length - 1;
  renderProgress();

  window.setTimeout(() => {
    if (!state.running || state.currentQuestion?.id !== nextQuestion.id) return;
    try {
      // Persist before displaying, so refresh, Skip and leaving the session all count as seen.
      promptHistory.record(nextQuestion, state.sessionLevel).catch(() => {
        // Keep the durable entry for retry; startSession flushes it before requesting new tasks.
      });
    } catch {
      leaveSession();
      showSessionError("Could not save your prompt history. Check that browser storage is available.");
      return;
    }
    questionDisplay.textContent = nextQuestion.text;
    questionDisplay.classList.add("is-visible");
    state.animating = false;
    setAssessmentDisabled(false);
    questionDisplay.focus();
  }, isImmediate ? 80 : 170);
}

function setAssessmentDisabled(isDisabled) {
  wordSpoiler.disabled = isDisabled;
  usedItButton.disabled = isDisabled;
  needsPracticeButton.disabled = isDisabled;
  skipPromptButton.disabled = isDisabled;
}

function renderProgress() {
  const current = Math.min(state.currentIndex + 1, state.sessionItems.length);
  const total = state.sessionItems.length;
  progressLabel.textContent = `${current} of ${total}`;
  progressFill.style.width = `${((current - 1) / total) * 100}%`;
}

function assessCurrentWord(result) {
  if (!state.running || state.animating || !state.currentQuestion) {
    return;
  }

  const item = state.sessionItems[state.currentIndex];
  const originalItem = state.vocabularyItems.find((candidate) => candidate.id === item.id);
  if (!originalItem) {
    return;
  }

  const storedItem = { ...originalItem, practiceHistory: [...(originalItem.practiceHistory || [])] };
  storedItem.attempts += 1;
  storedItem.successfulUses += result === "used" ? 1 : 0;
  storedItem.lastPracticedAt = new Date().toISOString();
  // Preserve every assessment, not just the most recent one. Revealing an earlier
  // prompt for the same word still counts when the learner asks for another prompt.
  storedItem.practiceHistory ||= [];
  storedItem.practiceHistory.push({
    sessionId: state.sessionId,
    taskId: state.currentQuestion.id,
    practicedAt: storedItem.lastPracticedAt,
    result,
    wordRevealed: state.revealedItemIds.has(item.id)
  });
  storedItem.status = progressStatus(storedItem);

  if (result === "used") {
    state.results.used.push(storedItem.id);
  } else {
    state.results.needsPractice.push(storedItem.id);
  }

  state.pendingResults.set(storedItem.id, storedItem);
  state.currentIndex += 1;

  if (state.currentIndex >= state.sessionItems.length) {
    finishSession();
  } else {
    showCurrentQuestion();
  }
}

function tryAnotherPrompt() {
  if (!state.running || state.animating || !state.currentQuestion) {
    return;
  }

  const item = state.sessionItems[state.currentIndex];
  const pool = state.questionPools.get(item.id);
  if (!pool || pool.length < 2) {
    return;
  }

  const position = (state.questionPositions.get(item.id) || 0) + 1;
  state.questionPositions.set(item.id, position);
  showCurrentQuestion();
}

function requestLeaveSession() {
  if (state.running && state.currentQuestion) {
    if (!leaveDialog.open) leaveDialog.showModal();
    return;
  }
  showHome();
}

function leaveSession() {
  state.pendingResults.clear();
  state.running = false;
  state.currentQuestion = null;
  showHome();
}

function finishSession() {
  state.vocabularyItems = state.vocabularyItems.map(item => state.pendingResults.get(item.id) || item);
  saveVocabularyItems();
  state.pendingResults.clear();
  state.running = false;
  state.currentQuestion = null;
  state.lastSession = {
    completedAt: new Date().toISOString(),
    usedIds: [...state.results.used],
    needsPracticeIds: [...state.results.needsPractice],
    total: state.sessionItems.length
  };
  saveLastSession();
  renderSummary();
  showView("summary");
}

function renderSummary() {
  const usedItems = itemsForIds(state.results.used);
  const needsItems = itemsForIds(state.results.needsPractice);
  const usedTotal = usedItems.length;
  const needsTotal = needsItems.length;

  summaryUsedCount.textContent = String(usedTotal);
  summaryNeedsCount.textContent = String(needsTotal);
  populateWordList(usedList, usedItems, "No words yet");
  populateWordList(needsList, needsItems, "Nothing to revisit");

  summaryTitle.textContent = usedTotal > 0
    ? "Your vocabulary is growing."
    : "Every attempt is a step forward.";
}

function itemsForIds(ids) {
  const itemById = new Map(state.vocabularyItems.map((item) => [item.id, item]));
  return ids.map((id) => itemById.get(id)).filter(Boolean);
}

function populateWordList(container, items, emptyText) {
  container.replaceChildren();
  if (items.length === 0) {
    const empty = document.createElement("span");
    empty.className = "empty-list";
    empty.textContent = emptyText;
    container.appendChild(empty);
    return;
  }

  items.forEach((item) => {
    const word = document.createElement("span");
    word.className = "word-pill";
    word.textContent = item.text;
    container.appendChild(word);
  });
}

function handleWordImport(event) {
  event.preventDefault();
  const value = wordInput.value.trim();
  if (!canSubmitImport(value)) {
    return;
  }

  const parsedItems = parseVocabularyInput(value);
  const addedCount = addVocabularyItems(parsedItems);
  wordInput.value = "";
  renderImportControls(
    addedCount > 0
      ? ""
      : "Those words are already in your library."
  );
  if (addedCount > 0) {
    showWordToast(addedCount);
  } else {
    dismissWordToast();
  }
}

function showWordToast(count) {
  window.clearTimeout(toastTimer);
  wordToastMessage.textContent = `You added ${count} new ${pluralize(count, "word", "words")}!`;
  wordToast.hidden = false;
  toastTimer = window.setTimeout(dismissWordToast, 4000);
}

function dismissWordToast() {
  window.clearTimeout(toastTimer);
  toastTimer = null;
  if (document.activeElement === toastClose) {
    wordInput.focus({ preventScroll: true });
  }
  wordToast.hidden = true;
  wordToastMessage.textContent = "";
}

function parseVocabularyInput(value) {
  const candidates = value
    .split(/\r?\n/)
    .flatMap((line) => line.split(/[,;]/))
    .map(stripListPrefix)
    .map(stripTranslation)
    .map(normalizeVocabularyText)
    .filter(Boolean);
  const unique = [];
  const seen = new Set();

  candidates.forEach((text) => {
    const key = normalizedKey(text);
    if (!seen.has(key)) {
      seen.add(key);
      unique.push(text);
    }
  });
  return unique.slice(0, 50);
}

function stripListPrefix(value) {
  return String(value).replace(/^\s*(?:[-*•]|\d+[.)])\s*/, "");
}

function stripTranslation(value) {
  return String(value).split(/\t|\s(?:—|–|-)+\s/, 1)[0];
}

function addVocabularyItems(textItems) {
  const existing = new Set(state.vocabularyItems.map((item) => normalizedKey(item.text)));
  const now = new Date().toISOString();
  const newItems = textItems
    .filter((text) => text && !existing.has(normalizedKey(text)))
    .map((text) => {
      existing.add(normalizedKey(text));
      return {
        id: createItemId(),
        text,
        status: "new",
        attempts: 0,
        successfulUses: 0,
        addedAt: now,
        lastPracticedAt: null,
        source: { type: "manual" }
      };
    });

  state.vocabularyItems.push(...newItems);
  saveVocabularyItems();
  renderHome();
  return newItems.length;
}

function handleVocabularyEdit(event) {
  const input = event.target.closest(".vocabulary-word-input");
  if (!input) {
    return;
  }

  const item = state.vocabularyItems.find((candidate) => candidate.id === input.dataset.itemId);
  if (!item) {
    return;
  }

  const nextText = normalizeVocabularyText(input.value);
  const duplicate = state.vocabularyItems.some(
    (candidate) => candidate.id !== item.id && normalizedKey(candidate.text) === normalizedKey(nextText)
  );
  if (!nextText || duplicate || !isEnglishInput(nextText)) {
    input.value = item.text;
    showSessionError(duplicate ? "That item is already in your library." : "Use an English word or phrase.");
    return;
  }

  item.text = nextText;
  hideSessionError();
  saveVocabularyItems();
  renderHome();
}

function handleVocabularyClick(event) {
  const removeButton = event.target.closest("[data-remove-item]");
  if (!removeButton) {
    return;
  }

  state.vocabularyItems = state.vocabularyItems.filter(
    (item) => item.id !== removeButton.dataset.removeItem
  );
  saveVocabularyItems();
  renderHome();
}

function snapshotVocabulary() {
  return Object.fromEntries(profileStorageKeys.map(key => [key, window.localStorage.getItem(key)]));
}

function clearVocabulary() {
  if (!state.vocabularyItems.length) return;
  // Back up progress and prompt identity together before starting a fresh learner profile.
  window.localStorage.setItem(vocabularyBackupKey, JSON.stringify(snapshotVocabulary()));
  profileStorageKeys.forEach(key => window.localStorage.removeItem(key));
  window.location.reload();
}

function restoreVocabulary() {
  const raw = window.localStorage.getItem(vocabularyBackupKey);
  if (!raw) return;
  const previous = JSON.parse(raw);
  const current = snapshotVocabulary();
  // Swap profiles so restoring never destroys vocabulary entered after the reset.
  window.localStorage.setItem(vocabularyBackupKey, JSON.stringify(current));
  profileStorageKeys.forEach(key => {
    if (typeof previous[key] === "string") window.localStorage.setItem(key, previous[key]);
    else window.localStorage.removeItem(key);
  });
  window.location.reload();
}

function renderHome() {
  document.getElementById("clear-vocabulary-button").hidden = !state.vocabularyItems.length;
  document.getElementById("restore-vocabulary-button").hidden = !window.localStorage.getItem(vocabularyBackupKey);
  const items = state.vocabularyItems;
  const isReturning = items.length > 0;

  homeTitle.textContent = "Keep your vocabulary in motion.";
  homeLead.hidden = isReturning;

  practiceButton.hidden = !isReturning;
  practiceButton.disabled = state.loading || !isReturning;

  statusSummary.hidden = items.length === 0;
  newCount.textContent = String(items.filter((item) => item.status === "new").length);
  buildingCount.textContent = String(items.filter((item) => item.status === "building").length);
  comfortableCount.textContent = String(items.filter((item) => item.status === "getting-comfortable").length);
  renderVocabularyList();
}

function renderVocabularyList() {
  vocabularyList.replaceChildren();
  if (state.vocabularyItems.length === 0) {
    const empty = document.createElement("div");
    empty.className = "library-empty";
    const title = document.createElement("strong");
    title.textContent = "Your words will live here.";
    const note = document.createElement("span");
    note.textContent = "Add a few from your latest lesson to begin.";
    empty.append(title, note);
    vocabularyList.appendChild(empty);
    return;
  }

  [...state.vocabularyItems]
    .sort(compareVocabularyItems)
    .forEach((item) => vocabularyList.appendChild(createVocabularyRow(item)));
}

function compareVocabularyItems(first, second) {
  const order = { new: 0, building: 1, "getting-comfortable": 2 };
  const statusDifference = order[first.status] - order[second.status];
  if (statusDifference !== 0) {
    return statusDifference;
  }
  return Date.parse(second.addedAt) - Date.parse(first.addedAt);
}

function createVocabularyRow(item) {
  const row = document.createElement("article");
  row.className = `vocabulary-item status-${item.status}`;

  const main = document.createElement("div");
  main.className = "vocabulary-item-main";
  const input = document.createElement("input");
  input.className = "vocabulary-word-input";
  input.type = "text";
  input.value = item.text;
  input.maxLength = 80;
  input.dataset.itemId = item.id;
  input.setAttribute("aria-label", `Edit ${item.text}`);
  const meta = document.createElement("span");
  meta.className = "vocabulary-meta";
  meta.textContent = itemMeta(item);
  main.append(input, meta);

  const actions = document.createElement("div");
  actions.className = "vocabulary-item-actions";
  const status = document.createElement("span");
  status.className = "status-label";
  status.textContent = statusLabel(item.status);
  const remove = document.createElement("button");
  remove.className = "remove-item-button";
  remove.type = "button";
  remove.dataset.removeItem = item.id;
  remove.setAttribute("aria-label", `Remove ${item.text}`);
  remove.innerHTML = `
    <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
      <path d="M4 7h16"></path>
      <path d="M9 7V4h6v3"></path>
      <path d="m7 7 1 13h8l1-13"></path>
    </svg>
  `;
  actions.append(status, remove);
  row.append(main, actions);
  return row;
}

function itemMeta(item) {
  if (item.attempts === 0) {
    return "Not practiced yet";
  }
  const date = formatPracticeDate(item.lastPracticedAt);
  return date;
}

function formatPracticeDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "practiced recently";
  }
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return "practiced today";
  }
  return `practiced ${new Intl.DateTimeFormat("en", { month: "short", day: "numeric" }).format(date)}`;
}

function statusLabel(status) {
  return {
    new: "New",
    building: "Building",
    "getting-comfortable": "Getting comfortable"
  }[status] || "New";
}

function renderImportControls(message = "") {
  const value = wordInput.value;
  const trimmed = value.trim();
  const isTooLong = value.length > inputCharacterLimit;
  const invalidLanguage = trimmed.length > 0 && !isEnglishInput(trimmed);
  const validationMessage = invalidLanguage ? "English words only for now." : message;
  const parsedCount = parseVocabularyInput(trimmed).length;

  inputStatus.textContent = validationMessage || `${value.length}/${inputCharacterLimit}`;
  inputStatus.classList.toggle("is-error", isTooLong || invalidLanguage);
  addWordButton.textContent = parsedCount === 1 ? "Add word" : "Add words";
  addWordButton.disabled = !canSubmitImport(trimmed);
}

function canSubmitImport(value) {
  return value.length > 0
    && wordInput.value.length <= inputCharacterLimit
    && isEnglishInput(value)
    && parseVocabularyInput(value).length > 0;
}

function isEnglishInput(value) {
  return /^[A-Za-z0-9\s.,;:'"!?()[\]{}&/\\+_*#=%\n\r\t—–-]+$/.test(value);
}

function normalizeVocabularyText(text) {
  return String(text || "").replace(/\s+/g, " ").trim().slice(0, 80);
}

function normalizedKey(value) {
  return normalizeVocabularyText(value).toLocaleLowerCase();
}

function createItemId() {
  return `item-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function setLoading(isLoading) {
  state.loading = isLoading;
  practiceButton.setAttribute("aria-busy", String(isLoading));
  practiceButtonLabel.textContent = isLoading ? "Preparing…" : "Practice now";
  doneForNowButton.disabled = isLoading;
  practiceAgainButton.disabled = isLoading;
  renderHome();
}

function showView(viewName) {
  homeView.classList.toggle("is-active", viewName === "home");
  previewView.classList.toggle("is-active", viewName === "preview");
  trainingView.classList.toggle("is-active", viewName === "training");
  summaryView.classList.toggle("is-active", viewName === "summary");
  howItWorksButton.hidden = viewName !== "home";
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function showHome() {
  state.preparation = null;
  state.running = false;
  state.currentQuestion = null;
  setLoading(false);
  renderReadyButton();
  previewError.hidden = true;
  showView("home");
}

function saveVocabularyItems() {
  window.localStorage.setItem(vocabularyStorageKey, JSON.stringify(state.vocabularyItems));
}

function loadVocabularyItems() {
  try {
    const savedItems = JSON.parse(window.localStorage.getItem(vocabularyStorageKey));
    if (!Array.isArray(savedItems)) {
      return [];
    }
    return savedItems
      .map(migrateVocabularyItem)
      .filter((item) => item.text);
  } catch {
    return [];
  }
}

// Replay assessments: entry and exit use different thresholds, so one difficult
// attempt never removes Getting comfortable. Prompt exposures are not assessments.
function progressStatus(item) {
  const results = (item.practiceHistory || [])
    .filter(attempt => attempt && ["used", "needs-practice"].includes(attempt.result))
    .map(attempt => attempt.result);
  let status = item.attempts > 0 ? "building" : "new";
  const recent = [];
  for (const result of results) {
    recent.push(result);
    if (recent.length > 4) recent.shift();
    if (status === "getting-comfortable") {
      if (recent.slice(-2).length === 2 && recent.slice(-2).every(value => value === "needs-practice")) {
        status = "building";
      }
    } else {
      status = recent.filter(value => value === "used").length >= 3
        ? "getting-comfortable" : "building";
    }
  }
  return status;
}

function migrateVocabularyItem(item) {
  const migrated = {
    id: typeof item.id === "string" ? item.id : createItemId(),
    text: normalizeVocabularyText(item.text),
    attempts: Number.isInteger(item.attempts) && item.attempts >= 0 ? item.attempts : 0,
    successfulUses: Number.isInteger(item.successfulUses) && item.successfulUses >= 0
      ? item.successfulUses
      : 0,
    practiceHistory: Array.isArray(item.practiceHistory) ? item.practiceHistory : [],
    // Preserve the last known legacy assessment for sharing, not as invented history.
    legacyLastResult: item.legacyLastResult || (item.status === "confident" ? "used"
      : item.status === "needs-practice" ? "needs-practice" : null),
    addedAt: typeof item.addedAt === "string" ? item.addedAt : new Date().toISOString(),
    lastPracticedAt: typeof item.lastPracticedAt === "string" ? item.lastPracticedAt : null,
    source: item.source && typeof item.source === "object" ? item.source : { type: "manual" }
  };
  migrated.status = progressStatus(migrated);
  return migrated;
}

function saveLastSession() {
  window.localStorage.setItem(lastSessionStorageKey, JSON.stringify(state.lastSession));
}

function loadLastSession() {
  try {
    const session = JSON.parse(window.localStorage.getItem(lastSessionStorageKey));
    return session && typeof session === "object" ? session : null;
  } catch {
    return null;
  }
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
  sessionError.textContent = "";
  sessionError.hidden = true;
}

function pluralize(count, singular, plural) {
  return count === 1 ? singular : plural;
}

function shuffle(items) {
  for (let index = items.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1));
    [items[index], items[randomIndex]] = [items[randomIndex], items[index]];
  }
  return items;
}

renderImportControls();
renderHome();
