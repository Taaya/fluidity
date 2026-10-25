package org.example.wordsmith.service;

import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingQuestion;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.QuestionGenerator;
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
import java.util.concurrent.ConcurrentHashMap;

public final class TrainingQuestionService {
    private static final int MAX_WORDS = 100;
    private static final int MAX_VALUE_LENGTH = 160;

    private final QuestionSetRepository repository;
    private final QuestionGenerator generator;
    private final Map<String, Object> generationLocks = new ConcurrentHashMap<>();

    public TrainingQuestionService(
            QuestionSetRepository repository,
            QuestionGenerator generator
    ) {
        this.repository = repository;
        this.generator = generator;
    }

    public TrainingSession createSession(List<String> rawTopics, List<String> rawWords) throws Exception {
        List<String> topics = normalize(rawTopics, "topics");
        List<String> words = normalize(rawWords, "words");
        validate(topics, words);

        String cacheKey = createCacheKey(topics, words, generator.cacheNamespace());
        QuestionSet cached = repository.find(cacheKey).orElse(null);
        if (cached != null) {
            return new TrainingSession(UUID.randomUUID().toString(), cached, true);
        }

        Object lock = generationLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = repository.find(cacheKey).orElse(null);
                if (cached != null) {
                    return new TrainingSession(UUID.randomUUID().toString(), cached, true);
                }

                List<TrainingQuestion> questions =
                        validateGeneratedQuestions(generator.generate(topics, words), topics, words);

                QuestionSet questionSet = new QuestionSet(
                        cacheKey,
                        List.copyOf(topics),
                        List.copyOf(words),
                        List.copyOf(questions),
                        generator.source(),
                        Instant.now()
                );
                repository.save(questionSet);
                return new TrainingSession(UUID.randomUUID().toString(), questionSet, false);
            }
        } finally {
            generationLocks.remove(cacheKey, lock);
        }
    }

    static String createCacheKey(
            List<String> topics,
            List<String> words,
            String generatorNamespace
    ) {
        List<String> sortedTopics = normalizedForKey(topics);
        List<String> sortedWords = normalizedForKey(words);
        String material = generatorNamespace
                + "\ntopics=" + String.join("\u001F", sortedTopics)
                + "\nwords=" + String.join("\u001F", sortedWords);

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
                .map(value -> value.toLowerCase(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .toList();
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

    private static void validate(List<String> topics, List<String> words) {
        if (topics.isEmpty() || topics.size() > 2) {
            throw new ValidationException("Select one or two topics");
        }
        if (words.isEmpty()) {
            throw new ValidationException("At least one dictionary word is required");
        }
        if (words.size() > MAX_WORDS) {
            throw new ValidationException("A session can contain at most " + MAX_WORDS + " words");
        }
    }

    private static List<TrainingQuestion> validateGeneratedQuestions(
            List<TrainingQuestion> questions,
            List<String> topics,
            List<String> words
    ) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalStateException("Question generator returned an empty set");
        }

        Map<String, String> allowedTopics = indexByNormalizedValue(topics);
        Map<String, String> allowedWords = indexByNormalizedValue(words);
        List<TrainingQuestion> validated = new ArrayList<>();

        for (TrainingQuestion question : questions) {
            if (question == null || question.text() == null) {
                throw new IllegalStateException("Question generator returned an incomplete question");
            }

            String text = question.text().trim();
            String canonicalWord = allowedWords.get(normalizedValue(question.word()));
            String canonicalTopic = allowedTopics.get(normalizedValue(question.topic()));
            if (text.isEmpty() || canonicalWord == null || canonicalTopic == null) {
                throw new IllegalStateException(
                        "Question generator returned a word or topic outside the request"
                );
            }

            String id = question.id() == null || question.id().isBlank()
                    ? UUID.randomUUID().toString()
                    : question.id();
            validated.add(new TrainingQuestion(id, text, canonicalWord, canonicalTopic));
        }

        return List.copyOf(validated);
    }

    private static Map<String, String> indexByNormalizedValue(List<String> values) {
        Map<String, String> index = new LinkedHashMap<>();
        values.forEach(value -> index.put(normalizedValue(value), value));
        return index;
    }

    private static String normalizedValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
