package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DemoQuestionGenerator implements QuestionGenerator {
    private static final List<String> TEMPLATES = List.of(
            "Tell a story about %s where you could naturally use \"%s\".",
            "Describe a situation involving %s and include \"%s\" in your answer.",
            "Imagine a conversation about %s. How would you use \"%s\"?"
    );

    @Override
    public List<TrainingQuestion> generate(List<String> topics, List<String> words) {
        List<TrainingQuestion> questions = new ArrayList<>();

        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            String topic = topics.get(index % topics.size());
            String template = TEMPLATES.get(index % TEMPLATES.size());

            questions.add(new TrainingQuestion(
                    UUID.randomUUID().toString(),
                    template.formatted(topic, word),
                    word,
                    topic
            ));
        }

        return questions;
    }

    @Override
    public String cacheNamespace() {
        return "demo-v1";
    }

    @Override
    public String source() {
        return "demo";
    }
}
