package org.example.wordsmith.config;

import java.nio.file.Path;
import java.util.Locale;

public record AppConfig(
        int port,
        Path cacheDirectory,
        String learnerLevel,
        GeneratorMode generatorMode,
        OpenAiConfig openAi
) {
    public static AppConfig fromEnvironment() {
        int port = Integer.parseInt(read("PORT", "8080"));
        Path cacheDirectory = Path.of(read("WORDSMITH_CACHE_DIR", "data/question-cache"));
        String learnerLevel = read("WORDSMITH_LEARNER_LEVEL", "Upper-Intermediate").trim();
        GeneratorMode mode = GeneratorMode.valueOf(
                read("WORDSMITH_GENERATOR", "auto").trim().toUpperCase(Locale.ROOT)
        );

        OpenAiConfig openAi = new OpenAiConfig(
                read("OPENAI_API_KEY", ""),
                read("OPENAI_MODEL", "gpt-5.4-nano")
        );

        if (mode == GeneratorMode.OPENAI && !openAi.isConfigured()) {
            throw new IllegalStateException(
                    "WORDSMITH_GENERATOR=openai requires OPENAI_API_KEY"
            );
        }

        return new AppConfig(
                port,
                cacheDirectory,
                learnerLevel.isBlank() ? "Upper-Intermediate" : learnerLevel,
                mode,
                openAi
        );
    }

    private static String read(String name, String fallback) {
        String systemValue = System.getProperty(name);
        if (systemValue != null) {
            return systemValue;
        }

        String environmentValue = System.getenv(name);
        return environmentValue == null ? fallback : environmentValue;
    }

    public enum GeneratorMode {
        AUTO,
        DEMO,
        OPENAI
    }

    public record OpenAiConfig(
            String apiKey,
            String model
    ) {
        public boolean isConfigured() {
            return !apiKey.isBlank();
        }
    }
}
