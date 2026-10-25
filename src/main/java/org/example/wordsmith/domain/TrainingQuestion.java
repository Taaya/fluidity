package org.example.wordsmith.domain;

public record TrainingQuestion(
        String id,
        String text,
        String word,
        String starter,
        String action,
        boolean used,
        String context
) {
    public TrainingQuestion(String id, String text, String word, String starter, String action, boolean used) {
        this(id, text, word, starter, action, used, "");
    }

    public TrainingQuestion {
        context = context == null ? "" : context;
    }

    public TrainingQuestion asSeen() {
        return new TrainingQuestion(id, text, word, starter, action, true, context);
    }
}
