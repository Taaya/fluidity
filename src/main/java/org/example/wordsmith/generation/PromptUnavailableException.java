package org.example.wordsmith.generation;

/** A user-facing explanation when suitable fresh prompts cannot be provided. */
public final class PromptUnavailableException extends Exception {
    public PromptUnavailableException(String message) {
        super(message);
    }
}
