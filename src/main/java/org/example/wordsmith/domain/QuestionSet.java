package org.example.wordsmith.domain;

import java.time.Instant;
import java.util.List;

public record QuestionSet(
        String cacheKey,
        List<String> topics,
        List<String> words,
        List<TrainingQuestion> questions,
        String source,
        Instant generatedAt
) {
}
