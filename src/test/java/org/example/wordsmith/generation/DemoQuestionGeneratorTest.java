package org.example.wordsmith.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.wordsmith.domain.TrainingQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoQuestionGeneratorTest {
    @Test
    void everyCuratedPackHasDistinctUsableTasksAtEveryLevel() throws Exception {
        var packs = new ObjectMapper().readTree(getClass().getResourceAsStream("/prompts/demo-speaking-tasks.json"));
        DemoQuestionGenerator generator = new DemoQuestionGenerator();
        for (String level : List.of("Elementary", "Intermediate", "Upper-Intermediate", "Advanced")) {
            var words = packs.fieldNames();
            while (words.hasNext()) {
                String word = words.next();
                List<TrainingQuestion> previous = new ArrayList<>();
                int total = packs.path(word).size();
                while (previous.size() < total) {
                    List<TrainingQuestion> batch = generator.generate(List.of(word), level, previous);
                    for (TrainingQuestion q : batch) {
                        assertFalse(PromptSimilarity.containsTarget(q.text(), word), word + ": " + q.text());
                        assertFalse(PromptSimilarity.containsTarget(q.starter(), word));
                        assertFalse(previous.stream().anyMatch(old -> PromptSimilarity.repeats(q, old)), word + ": " + q.text());
                        assertFalse(q.starter().isBlank());
                        assertFalse(q.context().isBlank());
                        previous.add(q.asSeen());
                    }
                }
                assertEquals(total, previous.size());
                assertThrows(PromptUnavailableException.class, () -> generator.generate(List.of(word), level, previous));
            }
        }
    }

    @Test
    void unsupportedWordsNeverReceiveGenericVocabularyAdvice() {
        assertThrows(PromptUnavailableException.class,
                () -> new DemoQuestionGenerator().generate(List.of("unlisted word"), "Intermediate"));
    }

    @Test
    void supportsInflectedCrackForms() throws Exception {
        for (String word : List.of("crack", "cracks", "cracked", "cracking")) {
            List<TrainingQuestion> tasks = new DemoQuestionGenerator().generate(List.of(word), "Intermediate");
            assertEquals(5, tasks.size());
            assertTrue(tasks.stream().allMatch(q -> word.equals(q.word())));
        }
    }
}
