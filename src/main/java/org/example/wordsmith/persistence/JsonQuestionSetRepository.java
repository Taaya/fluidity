package org.example.wordsmith.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.wordsmith.domain.QuestionSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class JsonQuestionSetRepository implements QuestionSetRepository {
    private final Path cacheDirectory;
    private final ObjectMapper objectMapper;

    public JsonQuestionSetRepository(Path cacheDirectory, ObjectMapper objectMapper) {
        this.cacheDirectory = cacheDirectory;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<QuestionSet> find(String cacheKey) throws IOException {
        Path path = cachePath(cacheKey);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        return Optional.of(objectMapper.readValue(path.toFile(), QuestionSet.class));
    }

    @Override
    public void save(QuestionSet questionSet) throws IOException {
        Files.createDirectories(cacheDirectory);
        Path destination = cachePath(questionSet.cacheKey());
        Path temporary = Files.createTempFile(cacheDirectory, questionSet.cacheKey(), ".tmp");

        try {
            objectMapper.writeValue(temporary.toFile(), questionSet);
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private Path cachePath(String cacheKey) {
        return cacheDirectory.resolve(cacheKey + ".json");
    }
}
