package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.wordsmith.api.WordsmithServer;
import org.example.wordsmith.config.AppConfig;
import org.example.wordsmith.generation.DemoQuestionGenerator;
import org.example.wordsmith.generation.OpenAiQuestionGenerator;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.persistence.JsonQuestionSetRepository;
import org.example.wordsmith.service.TrainingQuestionService;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        QuestionGenerator generator = createGenerator(config, objectMapper);
        JsonQuestionSetRepository repository =
                new JsonQuestionSetRepository(config.cacheDirectory(), objectMapper);
        TrainingQuestionService service =
                new TrainingQuestionService(repository, generator, config.learnerLevel());
        WordsmithServer server = new WordsmithServer(config.port(), objectMapper, service, generator);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();

        System.out.printf(
                "Wordswith is running at http://localhost:%d (%s generator)%n",
                config.port(),
                generator.source()
        );
    }

    private static QuestionGenerator createGenerator(AppConfig config, ObjectMapper objectMapper) {
        return switch (config.generatorMode()) {
            case DEMO -> new DemoQuestionGenerator();
            case OPENAI -> new OpenAiQuestionGenerator(config.openAi(), objectMapper);
            case AUTO -> config.openAi().isConfigured()
                    ? new OpenAiQuestionGenerator(config.openAi(), objectMapper)
                    : new DemoQuestionGenerator();
        };
    }
}
