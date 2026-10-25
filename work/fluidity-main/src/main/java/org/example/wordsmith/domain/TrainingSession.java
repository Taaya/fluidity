package org.example.wordsmith.domain;

public record TrainingSession(
        String sessionId,
        QuestionSet questionSet,
        boolean cached
) {
}
