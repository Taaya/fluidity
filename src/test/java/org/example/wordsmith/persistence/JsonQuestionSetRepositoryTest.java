package org.example.wordsmith.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonQuestionSetRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndReloadsQuestionSet() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonQuestionSetRepository repository =
                new JsonQuestionSetRepository(temporaryDirectory, objectMapper);
        QuestionSet questionSet = new QuestionSet(
                "cache-key",
                List.of("cat"),
                List.of(new TrainingQuestion(
                        "question-1",
                        "Describe a small everyday moment where this might happen.",
                        "cat",
                        "I would probably say that...",
                        "describe",
                        false
                )),
                "test",
                Instant.parse("2026-06-21T12:00:00Z")
        );

        repository.save(questionSet);

        assertTrue(repository.find("cache-key").isPresent());
        assertEquals(questionSet, repository.find("cache-key").orElseThrow());
    }
}
