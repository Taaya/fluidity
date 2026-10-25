package org.example.wordsmith.service;

import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingQuestion;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.generation.PromptSimilarity;
import org.example.wordsmith.generation.PromptUnavailableException;
import org.example.wordsmith.persistence.QuestionSetRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TrainingQuestionService {
    public static final int TASKS_PER_WORD = 5;

    private static final int MAX_WORDS = 100;
    private static final int MAX_VALUE_LENGTH = 160;

    private final QuestionSetRepository repository;
    private final QuestionGenerator generator;
    private final String defaultLearnerLevel;
    // Stable stripes serialize generation and exposure writes for the same learner/word.
    private final Object[] historyLocks = new Object[64];

    public TrainingQuestionService(
            QuestionSetRepository repository,
            QuestionGenerator generator,
            String learnerLevel
    ) {
        Arrays.setAll(historyLocks, ignored -> new Object());
        this.repository = repository;
        this.generator = generator;
        this.defaultLearnerLevel = normalizeLevel(learnerLevel, "Upper-Intermediate");
    }

    public TrainingSession createSession(List<String> rawWords, String rawLevel) throws Exception {
        return createSession(rawWords, rawLevel, "local");
    }

    public TrainingSession createSession(List<String> rawWords, String rawLevel, String learnerId) throws Exception {
        return createSession(rawWords, rawLevel, learnerId, null);
    }

    public TrainingSession createSession(List<String> rawWords, String rawLevel, String learnerId, Integer requestedSize) throws Exception {
        learnerId = validateLearnerId(learnerId);
        List<String> words = normalize(rawWords, "words");
        String learnerLevel = normalizeLevel(rawLevel, defaultLearnerLevel);
        validateWords(words);
        int size = requestedSize == null ? words.size() : requestedSize;
        if (size < 1 || size > MAX_WORDS) throw new ValidationException("Session size must be between 1 and 100");

        List<QuestionSet> questionSets = new ArrayList<>();
        List<String> preparedWords = new ArrayList<>();
        List<String> unavailableWords = new ArrayList<>();
        String unavailableReason = "";
        boolean fullyCached = true;
        for (String word : words) {
            try {
                PreparedQuestionSet prepared = prepareQuestionSet(word, learnerLevel, learnerId);
                questionSets.add(prepared.questionSet());
                preparedWords.add(word);
                fullyCached = fullyCached && prepared.cached();
                if (preparedWords.size() == size) break;
            } catch (PromptUnavailableException exception) {
                unavailableWords.add(word);
                unavailableReason = exception.getMessage();
            }
        }

        if (preparedWords.isEmpty()) throw new PromptUnavailableException(unavailableReason);
        QuestionSet sessionQuestionSet = mergeQuestionSets(preparedWords, questionSets, learnerLevel);
        return new TrainingSession(UUID.randomUUID().toString(), sessionQuestionSet, fullyCached, List.copyOf(unavailableWords));
    }

    public QuestionSet prepareDictionaryItem(String rawWord, String rawLevel) throws Exception {
        return prepareDictionaryItem(rawWord, rawLevel, "local");
    }

    public QuestionSet prepareDictionaryItem(String rawWord, String rawLevel, String learnerId) throws Exception {
        String word = singleWord(rawWord);
        String level = normalizeLevel(rawLevel, defaultLearnerLevel);
        return prepareQuestionSet(word, level, validateLearnerId(learnerId)).questionSet();
    }

    // "Used" is retained for API compatibility; this records exposure, not learning success.
    public QuestionSet markTaskUsed(String rawWord, String taskId, String rawLevel) throws Exception {
        return markTaskUsed(rawWord, taskId, rawLevel, "local");
    }

    public QuestionSet markTaskUsed(String rawWord, String taskId, String rawLevel, String learnerId) throws Exception {
        String word = singleWord(rawWord);
        String historyKey = historyKey(word, validateLearnerId(learnerId));
        if (taskId == null || taskId.isBlank()) throw new ValidationException("Task ID is required");
        synchronized (lockFor(historyKey)) {
            QuestionSet history = repository.find(historyKey)
                    .orElseThrow(() -> new ValidationException("Dictionary item has no prepared prompts"));
            TrainingQuestion seen = history.questions().stream().filter(q -> q.id().equals(taskId))
                    .findFirst().orElseThrow(() -> new ValidationException("Prompt was not found for this learner and word"));
            if (seen.used()) return history; // Idempotent replay from the browser's durable outbox.
            List<TrainingQuestion> updated = new ArrayList<>(history.questions());
            updated.removeIf(q -> q.id().equals(taskId));
            updated.add(seen.asSeen());
            QuestionSet saved = new QuestionSet(historyKey, List.of(word), List.copyOf(updated),
                    "history", Instant.now());
            repository.save(saved);
            return saved;
        }
    }

    private PreparedQuestionSet prepareQuestionSet(String word, String learnerLevel, String learnerId) throws Exception {
        String historyKey = historyKey(word, learnerId);
        String cacheKey = createWordCacheKey(word, generator.cacheNamespace() + ":learner:" + learnerId, learnerLevel);
        synchronized (lockFor(historyKey)) {
            QuestionSet history = repository.find(historyKey).orElse(null);
            List<TrainingQuestion> previous = history == null ? List.of() : history.questions();
            List<TrainingQuestion> seenTasks = previous.stream().filter(TrainingQuestion::used).toList();
            Set<String> seenIds = new HashSet<>();
            seenTasks.forEach(q -> seenIds.add(q.id()));
            QuestionSet cached = repository.find(cacheKey).orElse(null);
            if (cached != null) {
                List<TrainingQuestion> available = cached.questions().stream()
                        .filter(q -> !q.used() && !seenIds.contains(q.id()))
                        .filter(q -> seenTasks.stream().noneMatch(seen -> PromptSimilarity.repeats(q, seen)))
                        .toList();
                if (!available.isEmpty()) {
                    return new PreparedQuestionSet(new QuestionSet(cacheKey, List.of(word), available,
                            cached.source(), cached.generatedAt()), true);
                }
            }

            List<TrainingQuestion> questions = validateGeneratedQuestions(
                    generator.generate(List.of(word), learnerLevel, seenTasks), word);
            List<TrainingQuestion> exclusions = new ArrayList<>(seenTasks);
            for (TrainingQuestion question : questions) {
                if (exclusions.stream().anyMatch(old -> PromptSimilarity.repeats(question, old))) {
                    throw new PromptUnavailableException("Could not find sufficiently different prompts for “"
                            + word + "”. Please try again.");
                }
                exclusions.add(question);
            }

            // History is independent of level, model and prompt revision and retains every issued ID.
            // This also lets a delayed exposure arrive after a pool refresh or a settings change.
            List<TrainingQuestion> issued = new ArrayList<>(previous);
            issued.addAll(questions);
            repository.save(new QuestionSet(historyKey, List.of(word), List.copyOf(issued),
                    "history", Instant.now()));
            QuestionSet prepared = new QuestionSet(cacheKey, List.of(word), questions, generator.source(), Instant.now());
            repository.save(prepared);
            return new PreparedQuestionSet(prepared, false);
        }
    }

    private Object lockFor(String historyKey) {
        return historyLocks[Math.floorMod(historyKey.hashCode(), historyLocks.length)];
    }

    static String historyKey(String word, String learnerId) {
        return sha256("prompt-history-v1\nlearner=" + learnerId + "\nword=" + normalizedValue(word));
    }

    private static String singleWord(String rawWord) {
        if (rawWord == null) throw new ValidationException("Dictionary item is required");
        List<String> words = normalize(List.of(rawWord), "word");
        if (words.isEmpty()) throw new ValidationException("Dictionary item is required");
        return words.get(0);
    }

    private static String validateLearnerId(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) return "local";
        if (!learnerId.matches("[A-Za-z0-9_-]{1,80}")) throw new ValidationException("Invalid learner ID");
        return learnerId;
    }

    private QuestionSet mergeQuestionSets(
            List<String> words,
            List<QuestionSet> questionSets,
            String learnerLevel
    ) {
        List<TrainingQuestion> questions = questionSets.stream()
                .flatMap(questionSet -> questionSet.questions().stream())
                .filter(question -> !question.used())
                .toList();

        return new QuestionSet(
                createSessionCacheKey(words, generator.cacheNamespace(), learnerLevel),
                List.copyOf(words),
                questions,
                generator.source(),
                Instant.now()
        );
    }

    static String createWordCacheKey(String word, String generatorNamespace, String learnerLevel) {
        String material = generatorNamespace
                + "\nlevel=" + learnerLevel.trim().toUpperCase(Locale.ROOT)
                + "\nword=" + normalizedValue(word);
        return sha256(material);
    }

    static String createSessionCacheKey(List<String> words, String generatorNamespace, String learnerLevel) {
        List<String> sortedWords = normalizedForKey(words);
        String material = generatorNamespace
                + "\nlevel=" + learnerLevel.trim().toUpperCase(Locale.ROOT)
                + "\nwords=" + String.join("\u001F", sortedWords);
        return sha256(material);
    }

    private static String sha256(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<String> normalizedForKey(List<String> values) {
        return values.stream()
                .map(TrainingQuestionService::normalizedValue)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String normalizeLevel(String rawLevel, String fallback) {
        String level = rawLevel == null || rawLevel.isBlank() ? fallback : rawLevel.trim();
        return switch (level.toLowerCase(Locale.ROOT)) {
            case "elementary" -> "Elementary";
            case "intermediate" -> "Intermediate";
            case "upper-intermediate", "upper intermediate" -> "Upper-Intermediate";
            case "advanced" -> "Advanced";
            default -> fallback;
        };
    }

    private static List<String> normalize(List<String> values, String fieldName) {
        if (values == null) {
            throw new ValidationException(fieldName + " is required");
        }

        Map<String, String> uniqueValues = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }

            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_VALUE_LENGTH) {
                throw new ValidationException(
                        fieldName + " values must be at most " + MAX_VALUE_LENGTH + " characters"
                );
            }

            uniqueValues.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }

        return new ArrayList<>(uniqueValues.values());
    }

    private static void validateWords(List<String> words) {
        if (words.isEmpty()) {
            throw new ValidationException("At least one dictionary word is required");
        }
        if (words.size() > MAX_WORDS) {
            throw new ValidationException("A session can contain at most " + MAX_WORDS + " words");
        }
    }

    private static List<TrainingQuestion> validateGeneratedQuestions(
            List<TrainingQuestion> questions,
            String word
    ) {
        if (questions == null || questions.isEmpty() || questions.size() > TASKS_PER_WORD) {
            throw new IllegalStateException("Question generator must return one to five fresh tasks per word");
        }

        List<TrainingQuestion> validated = new ArrayList<>();
        for (TrainingQuestion question : questions) {
            if (question == null || question.text() == null || question.starter() == null) {
                throw new IllegalStateException("Question generator returned an incomplete task");
            }

            String text = question.text().trim();
            String starter = question.starter().trim();
            String action = question.action() == null || question.action().isBlank()
                    ? "reflect"
                    : question.action().trim();
            String canonicalWord = normalizedValue(question.word()).equals(normalizedValue(word))
                    ? word
                    : null;

            if (text.isEmpty() || starter.isEmpty() || canonicalWord == null) {
                throw new IllegalStateException("Question generator returned a task outside the request");
            }
            if (containsTargetItem(text, word) || containsTargetItem(starter, word)) {
                throw new IllegalStateException("Question generator included the target item in task text");
            }

            // IDs are issued here, never trusted from the model or a fixed demo pack.
            String id = UUID.randomUUID().toString();
            validated.add(new TrainingQuestion(id, text, canonicalWord, starter, action, false, question.context()));
        }

        return List.copyOf(validated);
    }

    private static boolean containsTargetItem(String value, String word) {
        return PromptSimilarity.containsTarget(value, word);
    }

    private static String normalizedValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record PreparedQuestionSet(QuestionSet questionSet, boolean cached) {
    }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
