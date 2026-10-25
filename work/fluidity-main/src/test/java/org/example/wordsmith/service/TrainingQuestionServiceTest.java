package org.example.wordsmith.service;

import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingQuestion;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.persistence.QuestionSetRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingQuestionServiceTest {
    @Test
    void reusesQuestionSetWhenWordsAndTopicsOnlyChangeOrder() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        CountingGenerator generator = new CountingGenerator();
        TrainingQuestionService service = new TrainingQuestionService(repository, generator);

        TrainingSession first = service.createSession(
                List.of("pets", "travelling & places"),
                List.of("cat", "serenity", "wisdom")
        );
        TrainingSession second = service.createSession(
                List.of("travelling & places", "pets"),
                List.of("wisdom", "cat", "serenity")
        );

        assertFalse(first.cached());
        assertTrue(second.cached());
        assertEquals(1, generator.calls.get());
        assertEquals(first.questionSet().cacheKey(), second.questionSet().cacheKey());
        assertNotEquals(first.sessionId(), second.sessionId());
    }

    @Test
    void rejectsMoreThanTwoTopics() {
        TrainingQuestionService service =
                new TrainingQuestionService(new InMemoryRepository(), new CountingGenerator());

        assertThrows(
                TrainingQuestionService.ValidationException.class,
                () -> service.createSession(
                        List.of("pets", "travel", "food"),
                        List.of("cat")
                )
        );
    }

    @Test
    void rejectsGeneratedQuestionsOutsideRequestedVocabulary() {
        QuestionGenerator invalidGenerator = new CountingGenerator() {
            @Override
            public List<TrainingQuestion> generate(List<String> topics, List<String> words) {
                return List.of(new TrainingQuestion(
                        "question-1",
                        "Use an unrelated word.",
                        "unrelated",
                        topics.get(0)
                ));
            }
        };
        TrainingQuestionService service =
                new TrainingQuestionService(new InMemoryRepository(), invalidGenerator);

        assertThrows(
                IllegalStateException.class,
                () -> service.createSession(List.of("pets"), List.of("cat"))
        );
    }

    private static final class InMemoryRepository implements QuestionSetRepository {
        private final Map<String, QuestionSet> entries = new HashMap<>();

        @Override
        public Optional<QuestionSet> find(String cacheKey) {
            return Optional.ofNullable(entries.get(cacheKey));
        }

        @Override
        public void save(QuestionSet questionSet) {
            entries.put(questionSet.cacheKey(), questionSet);
        }
    }

    private static class CountingGenerator implements QuestionGenerator {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<TrainingQuestion> generate(List<String> topics, List<String> words) {
            calls.incrementAndGet();
            return List.of(new TrainingQuestion(
                    "question-1",
                    "Tell a story about travelling with your cat.",
                    words.get(0),
                    topics.get(0)
            ));
        }

        @Override
        public String cacheNamespace() {
            return "test-generator-v1";
        }

        @Override
        public String source() {
            return "test";
        }
    }
}
