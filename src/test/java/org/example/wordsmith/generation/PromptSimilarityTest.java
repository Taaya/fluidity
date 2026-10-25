package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromptSimilarityTest {
    @Test
    void changingANameAndObjectDoesNotMakeNearIdenticalWordingNew() {
        TrainingQuestion first = task("Alice drops a phone onto a stone floor. Describe the damage and explain whether you would pay to repair it.");
        TrainingQuestion second = task("Bob drops a tablet onto a stone floor. Describe the damage and explain whether you would pay to repair it.");
        assertTrue(PromptSimilarity.repeats(first, second));
        assertFalse(PromptSimilarity.repeats(first,
                task("A mug arrives with a split near the rim. Explain the problem to the seller and request a replacement.")));
    }

    private TrainingQuestion task(String text) {
        return new TrainingQuestion("id", text, "cracked", "When I noticed...", "explain", false);
    }
}
