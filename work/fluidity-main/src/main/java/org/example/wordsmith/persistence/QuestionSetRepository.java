package org.example.wordsmith.persistence;

import org.example.wordsmith.domain.QuestionSet;

import java.io.IOException;
import java.util.Optional;

public interface QuestionSetRepository {
    Optional<QuestionSet> find(String cacheKey) throws IOException;

    void save(QuestionSet questionSet) throws IOException;
}
