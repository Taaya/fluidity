package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;

import java.util.List;

public interface QuestionGenerator {
    List<TrainingQuestion> generate(List<String> words, String level) throws Exception;

    default List<TrainingQuestion> generate(List<String> words, String level,
                                             List<TrainingQuestion> previousTasks) throws Exception {
        return generate(words, level);
    }

    String cacheNamespace();

    String source();
}
