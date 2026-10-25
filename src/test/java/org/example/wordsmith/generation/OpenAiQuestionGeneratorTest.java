package org.example.wordsmith.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.example.wordsmith.config.AppConfig.OpenAiConfig;
import org.example.wordsmith.domain.TrainingQuestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiQuestionGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Queue<String> replies = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;
    private OpenAiQuestionGenerator generator;

    @BeforeEach
    void startFakeResponsesEndpoint() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody()));
            String reply = replies.poll();
            int status = reply == null ? 500 : 200;
            byte[] body = (reply == null ? "{}" : reply).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        generator = new OpenAiQuestionGenerator(new OpenAiConfig("test-key", "test-model"), mapper,
                HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"));
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void sendsActualRulesAndHistoryAndOnlyReturnsReviewedTasks() throws Exception {
        respond(candidates("cracked", 0));
        respond(review(true, ""));
        List<TrainingQuestion> history = List.of(new TrainingQuestion("old", "Describe an earlier situation.",
                "cracked", "Previously...", "describe", true, "earlier distinct context"));
        List<TrainingQuestion> tasks = generator.generate(List.of("cracked"), "Intermediate", history);
        assertEquals(5, tasks.size());
        assertEquals(2, requests.size());
        JsonNode generation = requests.get(0);
        assertTrue(generation.path("input").get(0).path("content").asText().contains("The main question must elicit the thought expressed by the target itself"));
        assertFalse(generation.has("prompt")); // No separately managed dashboard prompt is required.
        assertEquals("test-model", generation.path("model").asText());
        assertFalse(generation.path("store").asBoolean());
        assertEquals("json_schema", generation.at("/text/format/type").asText());
        assertTrue(generation.at("/text/format/strict").asBoolean());
        JsonNode input = input(generation);
        assertEquals("Intermediate", input.path("level").asText());
        assertEquals("earlier distinct context", input.path("previous_tasks").get(0).path("context").asText());
        assertTrue(input(requests.get(1)).has("candidates"));
        assertFalse(mapper.writeValueAsString(tasks).contains("sample_answer"));
    }

    @Test
    void semanticRejectionTriggersRevisionWithFeedback() throws Exception {
        respond(candidates("cracked", 0));
        respond(review(false, "The seller request repeats a previous scenario. Choose a different speaking purpose."));
        respond(candidates("cracked", 5));
        respond(review(true, ""));
        List<TrainingQuestion> tasks = generator.generate(List.of("cracked"), "Advanced");
        assertEquals(4, requests.size());
        assertTrue(input(requests.get(2)).path("revision_feedback").asText().contains("different speaking purpose"));
        assertTrue(input(requests.get(2)).has("rejected_candidates"));
        assertEquals("context 5", tasks.get(0).context());
    }

    @Test
    void servesTheRevisedValidBatchWhenTheSecondEditorialReviewStillRejectsIt() throws Exception {
        for (int i = 0; i < 2; i++) {
            respond(candidates("cracked", i * 5));
            respond(review(false, "The word is forced into the sample answer."));
        }
        List<TrainingQuestion> tasks = generator.generate(List.of("cracked"), "Advanced");
        assertEquals(4, requests.size());
        assertEquals(5, tasks.size());
        assertEquals("context 5", tasks.get(0).context());
    }

    @Test
    void exactRepeatIsRejectedBeforeSemanticReviewEvenWithNewIds() throws Exception {
        respond(candidates("cracked", 0));
        respond(candidates("cracked", 5));
        respond(review(true, ""));
        List<TrainingQuestion> history = List.of(new TrainingQuestion("old", TEXTS.get(0), "cracked",
                "Before...", "explain", true, "different label"));
        List<TrainingQuestion> result = generator.generate(List.of("cracked"), "Intermediate", history);
        assertEquals(3, requests.size());
        assertFalse(result.stream().anyMatch(q -> q.text().equals(TEXTS.get(0))));
        assertTrue(input(requests.get(1)).path("revision_feedback").asText().contains("repeat"));
    }

    @Test
    void targetLeakageIsRevisedBeforeReview() throws Exception {
        ObjectNode leaked = candidates("cracked", 0);
        ((ObjectNode) leaked.at("/items/0/tasks/0")).put("text", "Your cracked phone fell onto stone. Explain what happened.");
        respond(leaked);
        respond(candidates("cracked", 5));
        respond(review(true, ""));
        assertEquals(5, generator.generate(List.of("cracked"), "Intermediate").size());
        assertEquals(3, requests.size());
        assertTrue(input(requests.get(1)).path("revision_feedback").asText().contains("reveals the target"));
    }

    @Test
    void refusesIncompleteOrWrongWordResponses() throws Exception {
        replies.add("{\"status\":\"incomplete\",\"output_text\":\"{}\"}");
        assertThrows(java.io.IOException.class, () -> generator.generate(List.of("cracked"), "Intermediate"));
        respond(candidates("unrequested", 0));
        assertThrows(java.io.IOException.class, () -> generator.generate(List.of("cracked"), "Intermediate"));
    }

    @Test
    void doesNotFallBackToDemoOnHttpFailureOrRefusal() throws Exception {
        assertThrows(OpenAiQuestionGenerator.OpenAiException.class,
                () -> generator.generate(List.of("cracked"), "Intermediate"));
        replies.add("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"Declined\"}]}]}");
        assertThrows(java.io.IOException.class, () -> generator.generate(List.of("cracked"), "Intermediate"));
    }

    @Test
    void boundsReviewHistoryWhileRetainingRecentExposures() throws Exception {
        respond(candidates("cracked", 0));
        respond(review(true, ""));
        List<TrainingQuestion> history = new ArrayList<>();
        for (int i = 0; i < 45; i++) history.add(new TrainingQuestion("old-" + i, "Historical task number " + i,
                "cracked", "Before...", "explain", i < 35, "historical context " + i));
        generator.generate(List.of("cracked"), "Intermediate", history);
        JsonNode previous = input(requests.get(0)).path("previous_tasks");
        assertEquals(30, previous.size());
        assertEquals("old-34", previous.get(29).path("id").asText());
    }

    private JsonNode input(JsonNode request) throws Exception {
        return mapper.readTree(request.path("input").get(1).path("content").asText());
    }

    private ObjectNode candidates(String word, int offset) {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode item = payload.putArray("items").addObject();
        item.put("word", word);
        var tasks = item.putArray("tasks");
        for (int i = offset; i < offset + 5; i++) {
            tasks.addObject().put("text", TEXTS.get(i)).put("starter", "The first thing I noticed was...")
                    .put("action", "explain").put("context", "context " + i)
                    .put("sample_answer", "The surface had cracked along one edge. A thin crack extended towards the middle.");
        }
        return payload;
    }

    private ObjectNode review(boolean accepted, String feedback) {
        return mapper.createObjectNode().put("accepted", accepted).put("feedback", feedback);
    }

    private void respond(JsonNode content) throws Exception {
        ObjectNode envelope = mapper.createObjectNode().put("status", "completed");
        envelope.putArray("output").addObject().putArray("content").addObject()
                .put("type", "output_text").put("text", mapper.writeValueAsString(content));
        replies.add(mapper.writeValueAsString(envelope));
    }

    private static final List<String> TEXTS = List.of(
            "Your phone fell onto stone. Describe the damage and decide whether a repair is worthwhile.",
            "A delivered mug has a split. Explain the problem to its seller and request a replacement.",
            "An unexplained line crosses your rented window. Tell the landlord what you discovered and ask for an inspection.",
            "You damaged a bowl borrowed from a friend. Apologise and offer to put things right.",
            "You are packing delicate plates before moving. Describe how you would protect them and check them afterwards.",
            "An outdoor pot has split after a cold week. Suggest a cause and explain how to care for the plant.",
            "An egg breaks into pieces while baking. Explain what happened and how to finish the recipe.",
            "A tile has developed a line across your shared hallway. Coordinate with neighbours to arrange an inspection.",
            "An old family cup leaks but has sentimental value. Explain why you would keep it and propose a new purpose.",
            "A used wooden table has a split down the middle. Ask its owner about the condition before making an offer."
    );
}
