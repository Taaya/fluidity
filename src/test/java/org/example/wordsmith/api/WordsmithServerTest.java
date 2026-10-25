package org.example.wordsmith.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.wordsmith.generation.DemoQuestionGenerator;
import org.example.wordsmith.persistence.JsonQuestionSetRepository;
import org.example.wordsmith.service.TrainingQuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WordsmithServerTest {
    @TempDir Path cache;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void fullSessionFlowRecordsExposureAndKeepsOtherLearnersIndependent() throws Exception {
        try (RunningApp app = new RunningApp()) {
            JsonNode first = json(send(app.port(), "POST", "/api/training-sessions", "alice",
                    "{\"words\":[\"Danube\",\"among\",\"cracked\"],\"level\":\"Intermediate\"}"), 200);
            assertEquals(15, first.path("questions").size());
            JsonNode shown = first.path("questions").get(0);
            String event = mapper.createObjectNode().put("word", shown.path("word").asText())
                    .put("taskId", shown.path("id").asText()).put("level", "Intermediate").toString();
            JsonNode ack = json(send(app.port(), "PATCH", "/api/dictionary-items/tasks", "alice", event), 200);
            assertTrue(ack.path("recorded").asBoolean());
            // Replaying after a refresh is safe, even if the original acknowledgment was lost.
            json(send(app.port(), "PATCH", "/api/dictionary-items/tasks", "alice", event), 200);
            JsonNode next = json(send(app.port(), "POST", "/api/training-sessions", "alice",
                    "{\"words\":[\"Danube\"],\"level\":\"Intermediate\"}"), 200);
            assertEquals(4, next.path("questions").size());
            for (JsonNode task : next.path("questions")) assertNotEquals(shown.path("id"), task.path("id"));
            JsonNode bob = json(send(app.port(), "POST", "/api/training-sessions", "bob",
                    "{\"words\":[\"Danube\"],\"level\":\"Intermediate\"}"), 200);
            assertEquals(5, bob.path("questions").size());
            assertEquals(400, send(app.port(), "PATCH", "/api/dictionary-items/tasks", "bob", event).statusCode());
            assertEquals(400, send(app.port(), "POST", "/api/training-sessions", "../bad",
                    "{\"words\":[\"Danube\"]}").statusCode());
        }
    }

    @Test
    void buildsSessionFromAvailableCandidatesIncludingSteeple() throws Exception {
        try (RunningApp app = new RunningApp()) {
            JsonNode session = json(send(app.port(), "POST", "/api/training-sessions", "alice",
                    "{\"words\":[\"unsupported vocabulary\",\"steeple\",\"among\",\"Danube\"],\"sessionSize\":2}"), 200);
            assertEquals(2, session.path("words").size());
            assertEquals("steeple", session.path("words").get(0).asText());
            assertEquals("among", session.path("words").get(1).asText());
            assertEquals(10, session.path("questions").size());
            assertEquals("unsupported vocabulary", session.path("unavailableWords").get(0).asText());
            for (JsonNode question : session.path("questions")) {
                assertFalse(question.path("text").asText().contains("this expression"));
            }
            assertEquals(400, send(app.port(), "POST", "/api/training-sessions", "alice",
                    "{\"words\":[\"steeple\"],\"sessionSize\":0}").statusCode());
        }
    }

    @Test
    void unsupportedDemoVocabularyReturnsAnActionableErrorAndAssetsAreServed() throws Exception {
        try (RunningApp app = new RunningApp()) {
            JsonNode error = json(send(app.port(), "POST", "/api/training-sessions", "alice",
                    "{\"words\":[\"unsupported vocabulary\"]}"), 503);
            assertTrue(error.path("error").asText().contains("Danube, among, cracked"));
            var asset = send(app.port(), "GET", "/prompt-history.js", "alice", "");
            assertEquals(200, asset.statusCode());
            assertTrue(asset.body().contains("createPromptHistory"));
            assertTrue(send(app.port(), "GET", "/", "alice", "").body().contains("Reveal word"));
        }
    }

    private HttpResponse<String> send(int port, String method, String route, String learner, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                .header("Content-Type", "application/json").header("X-Learner-Id", learner)
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private class RunningApp implements AutoCloseable {
        private final WordsmithServer server;
        RunningApp() throws Exception {
            DemoQuestionGenerator generator = new DemoQuestionGenerator();
            var service = new TrainingQuestionService(new JsonQuestionSetRepository(cache, mapper), generator, "Intermediate");
            server = new WordsmithServer(0, mapper, service, generator);
            server.start();
        }
        int port() { return server.port(); }
        @Override public void close() { server.stop(); }
    }
}
