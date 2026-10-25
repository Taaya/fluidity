package org.example.wordsmith.domain;

public record TrainingQuestion(
        String id,
        String text,
        String word,
        String topic
) {
}
