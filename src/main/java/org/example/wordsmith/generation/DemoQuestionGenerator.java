package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DemoQuestionGenerator implements QuestionGenerator {
    private static final Map<String, List<DemoTask>> REVIEWED_TASKS = loadReviewedTasks();

    private static Map<String, List<DemoTask>> loadReviewedTasks() {
        try (InputStream input = DemoQuestionGenerator.class.getResourceAsStream("/prompts/demo-speaking-tasks.json")) {
            if (input == null) throw new IllegalStateException("Missing demo speaking tasks");
            return new ObjectMapper().readValue(input, new TypeReference<Map<String, List<DemoTask>>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load demo speaking tasks", exception);
        }
    }

    @Override
    public List<TrainingQuestion> generate(List<String> words, String level) throws PromptUnavailableException {
        return generate(words, level, List.of());
    }

    @Override
    public List<TrainingQuestion> generate(List<String> words, String level,
                                            List<TrainingQuestion> previousTasks) throws PromptUnavailableException {
        List<TrainingQuestion> questions = new ArrayList<>();
        for (String word : words) {
            String key = word.toLowerCase(Locale.ROOT);
            if (List.of("crack", "cracks", "cracking").contains(key)) key = "cracked";
            if (key.equals("danube")) key = "Danube";
            List<DemoTask> tasks = REVIEWED_TASKS.get(key);
            if (tasks == null) {
                throw new PromptUnavailableException("Personalized prompts for “" + word
                        + "” are not connected yet. This preview includes Danube, among, cracked and the sample phrases.");
            }
            int count = 0;
            for (DemoTask task : tasks) {
                DemoTask adjusted = adjustForLevel(task, level);
                TrainingQuestion candidate = new TrainingQuestion(UUID.randomUUID().toString(), adjusted.text(),
                        word, adjusted.starter(), adjusted.action(), false, task.context());
                if (previousTasks.stream().anyMatch(old -> PromptSimilarity.repeats(candidate, old))) continue;
                questions.add(candidate);
                if (++count == 5) break;
            }
            if (count == 0) throw new PromptUnavailableException("You've seen all preview prompts for “" + word
                    + "”. Connect personalized generation to get new situations.");
        }
        return List.copyOf(questions);
    }

    @Override
    public String cacheNamespace() {
        return "demo-focused-v5";
    }

    @Override
    public String source() {
        return "demo";
    }

    private static DemoTask adjustForLevel(DemoTask task, String level) {
        String normalizedLevel = level == null ? "" : level.toLowerCase(Locale.ROOT);
        return switch (normalizedLevel) {
            case "elementary" -> new DemoTask(
                    task.text().replace("Explain", "Say")
                            .replace("Compare", "Say how these are different")
                            .replace("Respond carefully", "Answer kindly")
                            .replace("Reflect on", "Think about")
                            .replace("persuade", "help them say yes")
                            .replace("Persuade", "Help them say yes"),
                    simpleStarter(task.starter()),
                    task.action(), task.context()
            );
            case "intermediate" -> new DemoTask(task.text(), simpleStarter(task.starter()), task.action(), task.context());
            default -> task;
        };
    }

    private static String simpleStarter(String starter) {
        if (starter.contains("On one hand")) {
            return "One good thing is..., but...";
        }
        if (starter.contains("The difference is subtle")) {
            return "The small difference is...";
        }
        if (starter.contains("The important distinction")) {
            return "The important difference is...";
        }
        if (starter.contains("I would not assume")) {
            return "I would not think that right away because...";
        }
        if (starter.contains("I would first ask whether")) {
            return "First, I would ask if...";
        }
        return starter;
    }

    private record DemoTask(String text, String starter, String action, String context) {
    }
}
