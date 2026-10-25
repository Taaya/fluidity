package org.example.wordsmith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingQuestion;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.DemoQuestionGenerator;
import org.example.wordsmith.generation.PromptUnavailableException;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.persistence.JsonQuestionSetRepository;
import org.example.wordsmith.persistence.QuestionSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TrainingQuestionServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void reusesUnseenTasksInsteadOfRegeneratingAfterEachExposure() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        CountingGenerator generator = new CountingGenerator();
        TrainingQuestionService service = service(repository, generator);
        QuestionSet pool = service.prepareDictionaryItem("cracked", "Intermediate");
        String seenId = pool.questions().get(0).id();
        service.markTaskUsed("cracked", seenId, "Intermediate");
        TrainingSession next = service.createSession(List.of("cracked"), "Intermediate");
        assertTrue(next.cached());
        assertEquals(1, generator.calls.get());
        assertEquals(4, next.questionSet().questions().size());
        assertFalse(ids(next.questionSet()).contains(seenId));
    }

    @Test
    void replenishesExhaustedPoolAndPassesPreviousTasksToGenerator() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        CountingGenerator generator = new CountingGenerator();
        TrainingQuestionService service = service(repository, generator);
        QuestionSet first = service.prepareDictionaryItem("cracked", "Advanced");
        markAll(service, first, "Advanced", "local");
        TrainingSession next = service.createSession(List.of("cracked"), "Advanced");
        assertFalse(next.cached());
        assertEquals(2, generator.calls.get());
        assertEquals(5, generator.lastHistory.size());
        assertTrue(generator.lastHistory.stream().allMatch(TrainingQuestion::used));
        assertEquals(5, next.questionSet().questions().size());
        assertTrue(java.util.Collections.disjoint(texts(first), texts(next.questionSet())));
    }

    @Test
    void retainsExposureAfterRestartAndAcrossLevelsAndGeneratorVersions() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TrainingQuestionService firstService = service(new JsonQuestionSetRepository(temporaryDirectory, mapper), new CountingGenerator());
        QuestionSet first = firstService.prepareDictionaryItem("cracked", "Intermediate", "alice");
        firstService.markTaskUsed("cracked", first.questions().get(0).id(), "Intermediate", "alice");
        CountingGenerator upgraded = new CountingGenerator() {
            @Override public String cacheNamespace() { return "new-prompt-version"; }
        };
        TrainingQuestionService restarted = service(new JsonQuestionSetRepository(temporaryDirectory, mapper), upgraded);
        QuestionSet second = restarted.prepareDictionaryItem("CRACKED", "Elementary", "alice");
        assertEquals(1, upgraded.lastHistory.size());
        assertEquals(1, upgraded.lastHistory.stream().filter(TrainingQuestion::used).count());
        assertTrue(java.util.Collections.disjoint(texts(first), texts(second)));
        // A delayed exposure from the old pool still resolves, even though settings changed.
        restarted.markTaskUsed("cracked", first.questions().get(1).id(), "Intermediate", "alice");
        restarted.markTaskUsed("cracked", first.questions().get(1).id(), "Advanced", "alice");
        QuestionSet history = new JsonQuestionSetRepository(temporaryDirectory, mapper)
                .find(TrainingQuestionService.historyKey("cracked", "alice")).orElseThrow();
        assertEquals(2, history.questions().stream().filter(TrainingQuestion::used).count());
    }

    @Test
    void isolatesLearnerHistories() throws Exception {
        CountingGenerator generator = new CountingGenerator();
        TrainingQuestionService service = service(new InMemoryRepository(), generator);
        QuestionSet alice = service.prepareDictionaryItem("cracked", "Intermediate", "alice");
        service.markTaskUsed("cracked", alice.questions().get(0).id(), "Intermediate", "alice");
        QuestionSet bob = service.prepareDictionaryItem("cracked", "Intermediate", "bob");
        assertTrue(generator.lastHistory.isEmpty());
        assertEquals(texts(alice), texts(bob));
        assertTrue(java.util.Collections.disjoint(ids(alice), ids(bob)));
        assertThrows(TrainingQuestionService.ValidationException.class,
                () -> service.markTaskUsed("cracked", alice.questions().get(0).id(), "Intermediate", "bob"));
    }

    @Test
    void concurrentExposureWritesDoNotLoseUpdates() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        TrainingQuestionService service = service(repository, new CountingGenerator());
        QuestionSet pool = service.prepareDictionaryItem("cracked", "Intermediate");
        List<CompletableFuture<Void>> writes = pool.questions().stream().map(q -> CompletableFuture.runAsync(() -> {
            try { service.markTaskUsed("cracked", q.id(), "Intermediate"); }
            catch (Exception e) { throw new RuntimeException(e); }
        })).toList();
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        assertEquals(5, repository.find(TrainingQuestionService.historyKey("cracked", "local"))
                .orElseThrow().questions().stream().filter(TrainingQuestion::used).count());
    }

    @Test
    void newIdsCannotDisguiseOldPrompts() throws Exception {
        CountingGenerator repeating = new CountingGenerator() {
            @Override public List<TrainingQuestion> generate(List<String> words, String level,
                                                              List<TrainingQuestion> history) {
                return super.generate(words, level, List.of());
            }
        };
        TrainingQuestionService service = service(new InMemoryRepository(), repeating);
        QuestionSet first = service.prepareDictionaryItem("cracked", "Advanced");
        markAll(service, first, "Advanced", "local");
        assertThrows(PromptUnavailableException.class, () -> service.prepareDictionaryItem("cracked", "Advanced"));
    }

    @Test
    void rejectsRepeatedContextsEvenWithDifferentText() throws Exception {
        CountingGenerator repeatedContext = new CountingGenerator() {
            @Override public List<TrainingQuestion> generate(List<String> words, String level, List<TrainingQuestion> history) {
                return super.generate(words, level, history).stream().map(q -> new TrainingQuestion(q.id(), q.text(),
                        q.word(), q.starter(), q.action(), false, "same scenario and purpose")).toList();
            }
        };
        assertThrows(PromptUnavailableException.class,
                () -> service(new InMemoryRepository(), repeatedContext).prepareDictionaryItem("cracked", "Advanced"));
    }

    @Test
    void rejectsTargetInPromptButAllowsTargetInsideAnUnrelatedWord() throws Exception {
        QuestionGenerator allowed = singleTask("Describe your vacation and explain which place you would revisit.", "cat");
        assertEquals(1, service(new InMemoryRepository(), allowed).prepareDictionaryItem("cat", "Elementary").questions().size());
        QuestionGenerator invalid = singleTask("Describe a CAT you saw and explain why you noticed it.", "cat");
        assertThrows(IllegalStateException.class,
                () -> service(new InMemoryRepository(), invalid).prepareDictionaryItem("cat", "Elementary"));
    }

    @Test
    void unknownTasksAndInvalidInputAreRejected() throws Exception {
        TrainingQuestionService service = service(new InMemoryRepository(), new CountingGenerator());
        service.prepareDictionaryItem("cracked", "Intermediate");
        assertThrows(TrainingQuestionService.ValidationException.class,
                () -> service.markTaskUsed("cracked", "missing", "Intermediate"));
        assertThrows(TrainingQuestionService.ValidationException.class,
                () -> service.prepareDictionaryItem(null, "Intermediate"));
        assertThrows(TrainingQuestionService.ValidationException.class,
                () -> service.prepareDictionaryItem(" ", "Intermediate"));
        assertThrows(TrainingQuestionService.ValidationException.class,
                () -> service.prepareDictionaryItem("cracked", "Intermediate", "../other"));
    }

    @Test
    void revisionCanReuseUnseenContextsButCannotReshowSeenOnesThroughAnOlderPool() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        TrainingQuestionService older = service(repository, demoRevision("old-editorial-version"));
        String word = "to bridge the gap";
        QuestionSet oldPool = older.prepareDictionaryItem(word, "Intermediate");
        TrainingQuestion seenBeforeRevision = oldPool.questions().get(0);
        older.markTaskUsed(word, seenBeforeRevision.id(), "Intermediate");

        TrainingQuestionService revised = service(repository, demoRevision("focused-editorial-version"));
        QuestionSet revisedPool = revised.prepareDictionaryItem(word, "Intermediate");
        assertEquals(4, revisedPool.questions().size());
        assertTrue(revisedPool.questions().stream().noneMatch(q -> q.context().equals(seenBeforeRevision.context())));
        TrainingQuestion newlySeen = revisedPool.questions().get(0);
        revised.markTaskUsed(word, newlySeen.id(), "Intermediate");

        // The old pool contains a different issued ID for the same now-seen situation.
        QuestionSet backOnOldVersion = older.prepareDictionaryItem(word, "Intermediate");
        assertEquals(3, backOnOldVersion.questions().size());
        assertTrue(backOnOldVersion.questions().stream().noneMatch(q -> q.context().equals(newlySeen.context())));
    }

    private static QuestionGenerator demoRevision(String revision) {
        return new QuestionGenerator() {
            private final DemoQuestionGenerator demo = new DemoQuestionGenerator();
            @Override public List<TrainingQuestion> generate(List<String> words, String level) throws Exception {
                return demo.generate(words, level);
            }
            @Override public List<TrainingQuestion> generate(List<String> words, String level,
                                                              List<TrainingQuestion> previous) throws Exception {
                return demo.generate(words, level, previous);
            }
            @Override public String cacheNamespace() { return revision; }
            @Override public String source() { return "demo"; }
        };
    }

    @Test
    void demoOffersTenDifferentEncountersThenReportsExhaustion() throws Exception {
        TrainingQuestionService service = service(new InMemoryRepository(), new DemoQuestionGenerator());
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            QuestionSet pool = service.prepareDictionaryItem("cracked", "Intermediate");
            TrainingQuestion question = pool.questions().get(0);
            assertTrue(seen.add(question.text()));
            service.markTaskUsed("cracked", question.id(), "Intermediate");
        }
        assertThrows(PromptUnavailableException.class, () -> service.prepareDictionaryItem("cracked", "Intermediate"));
    }

    private static QuestionGenerator singleTask(String text, String word) {
        return new CountingGenerator() {
            @Override public List<TrainingQuestion> generate(List<String> words, String level, List<TrainingQuestion> history) {
                return List.of(new TrainingQuestion("supplied", text, word, "I remember...", "describe", false));
            }
        };
    }

    private static void markAll(TrainingQuestionService service, QuestionSet pool, String level, String learner) throws Exception {
        for (TrainingQuestion q : pool.questions()) service.markTaskUsed(q.word(), q.id(), level, learner);
    }

    private static TrainingQuestionService service(QuestionSetRepository repository, QuestionGenerator generator) {
        return new TrainingQuestionService(repository, generator, "Upper-Intermediate");
    }

    private static Set<String> ids(QuestionSet pool) {
        return pool.questions().stream().map(TrainingQuestion::id).collect(Collectors.toSet());
    }

    private static Set<String> texts(QuestionSet pool) {
        return pool.questions().stream().map(TrainingQuestion::text).collect(Collectors.toSet());
    }

    private static final class InMemoryRepository implements QuestionSetRepository {
        private final Map<String, QuestionSet> entries = new HashMap<>();
        @Override public Optional<QuestionSet> find(String key) { return Optional.ofNullable(entries.get(key)); }
        @Override public void save(QuestionSet set) { entries.put(set.cacheKey(), set); }
    }

    private static class CountingGenerator implements QuestionGenerator {
        private final AtomicInteger calls = new AtomicInteger();
        private List<TrainingQuestion> lastHistory = List.of();
        private static final List<String> SCENARIOS = List.of(
                "A phone fell onto a stone floor. Explain the visible damage and decide whether a repair is worthwhile.",
                "Your mug arrived with a split in its side. Contact the seller to explain the problem and request a replacement.",
                "There is a line across your rented flat's window. Tell the landlord what you know and ask for an inspection.",
                "You damaged a borrowed bowl against the sink. Apologise to your friend and offer a way to put things right.",
                "You are packing delicate crockery before moving. Describe how to protect it and how to check it on arrival.",
                "An outdoor plant pot has a split after cold weather. Describe the discovery and decide what to do with the plant.",
                "An egg breaks into pieces during baking. Explain the mistake and how to help your friend finish the recipe.",
                "A hallway tile has developed a long line. Coordinate with neighbours to arrange an inspection of the shared space.",
                "An old family teacup leaks but has sentimental value. Explain why you would keep it and suggest a new purpose.",
                "A second-hand table has a long split through the top. Ask the seller about its condition before deciding to buy."
        );
        @Override public List<TrainingQuestion> generate(List<String> words, String level) {
            return generate(words, level, List.of());
        }
        @Override public List<TrainingQuestion> generate(List<String> words, String level, List<TrainingQuestion> history) {
            calls.incrementAndGet();
            lastHistory = List.copyOf(history);
            int offset = history.isEmpty() ? 0 : 5;
            return IntStream.range(offset, offset + 5).mapToObj(i -> new TrainingQuestion("model-" + i,
                    SCENARIOS.get(i), words.get(0), "The first thing I noticed was...", "explain", false, "context " + i)).toList();
        }
        @Override public String cacheNamespace() { return "test-context-v1"; }
        @Override public String source() { return "test"; }
    }
}
