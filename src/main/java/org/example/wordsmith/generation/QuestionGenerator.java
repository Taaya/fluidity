package org.example.wordsmith.generation;

import org.example.wordsmith.domain.TrainingQuestion;

import java.util.List;

public interface QuestionGenerator {
    List<TrainingQuestion> generate(List<String> topics, List<String> words) throws Exception;

    String cacheNamespace();

    String source();
}
