package org.example.wordsmith.domain;

public record TrainingSession(
        String sessionId,
        QuestionSet questionSet,
        boolean cached,
        java.util.List<String> unavailableWords
) {
    public TrainingSession(String sessionId, QuestionSet questionSet, boolean cached) {
        this(sessionId, questionSet, cached, java.util.List.of());
    }
}
