package org.example.wordsmith.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.wordsmith.config.AppConfig.OpenAiConfig;
import org.example.wordsmith.domain.TrainingQuestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class OpenAiQuestionGenerator implements QuestionGenerator {
    private static final URI RESPONSES_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final String GENERATION_RULES = readRules("generate-speaking-tasks.txt");
    private static final String REVIEW_RULES = readRules("review-speaking-tasks.txt");
    // Rules are versioned by content, so editing the actual prompt invalidates task pools.
    private static final String RULES_VERSION = rulesVersion();
    private static final int RECENT_TASK_LIMIT = 30;

    private final OpenAiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;

    public OpenAiQuestionGenerator(OpenAiConfig config, ObjectMapper objectMapper) {
        this(config, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                RESPONSES_ENDPOINT);
    }

    // Endpoint injection is package-private and is used only by local HTTP contract tests.
    OpenAiQuestionGenerator(OpenAiConfig config, ObjectMapper objectMapper, HttpClient client, URI endpoint) {
        if (!config.isConfigured()) throw new IllegalArgumentException("OpenAI generation requires OPENAI_API_KEY");
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = client;
        this.endpoint = endpoint;
    }

    @Override
    public List<TrainingQuestion> generate(List<String> words, String level) throws Exception {
        return generate(words, level, List.of());
    }

    @Override
    public List<TrainingQuestion> generate(List<String> words, String level,
                                            List<TrainingQuestion> previousTasks) throws Exception {
        // The service prepares one word at a time, keeping history, latency and retries bounded.
        if (words.size() != 1) throw new IllegalArgumentException("Generate one vocabulary item at a time");
        ObjectNode input = objectMapper.createObjectNode();
        input.set("words_json", objectMapper.valueToTree(words));
        input.put("level", level);
        input.set("previous_tasks", objectMapper.valueToTree(recentTasks(previousTasks)));
        String feedback = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            input.put("revision_feedback", feedback);
            JsonNode candidates = request(GENERATION_RULES, input, "vocabulary_speaking_tasks", questionSchema(), 5000);
            List<TrainingQuestion> questions = parseQuestions(candidates, words.get(0));
            List<TrainingQuestion> exclusions = new ArrayList<>(previousTasks);
            boolean duplicate = false;
            boolean revealedTarget = false;
            for (TrainingQuestion question : questions) {
                if (exclusions.stream().anyMatch(old -> PromptSimilarity.repeats(question, old))) duplicate = true;
                if (PromptSimilarity.containsTarget(question.text(), words.get(0))
                        || PromptSimilarity.containsTarget(question.starter(), words.get(0))) revealedTarget = true;
                exclusions.add(question);
            }
            if (revealedTarget) {
                feedback = "The question or starter reveals the target. Supply its natural context without including the answer.";
            } else if (duplicate) {
                feedback = "Candidates repeat earlier tasks or one another. Create substantively different situations and speaking purposes.";
            } else {
                ObjectNode reviewInput = input.deepCopy();
                reviewInput.set("candidates", candidates);
                JsonNode review = request(REVIEW_RULES, reviewInput, "speaking_task_review", reviewSchema(), 1500);
                if (review == null || !review.path("accepted").isBoolean() || !review.path("feedback").isTextual()) {
                    throw new IOException("OpenAI returned an incomplete prompt review");
                }
                if (review.path("accepted").booleanValue()) return questions;
                // The editorial review gets one chance to improve a valid batch. For the MVP,
                // do not make an advisory semantic judgment a permanent practice blocker.
                if (attempt == 1) return questions;
                feedback = review.path("feedback").asText();
            }
            // Let the next attempt see what failed, rather than request the same ideas again.
            input.set("rejected_candidates", candidates);
        }
        throw new PromptUnavailableException("Could not prepare sufficiently different, word-specific prompts for “"
                + words.get(0) + "”. Please try again.");
    }

    private List<TrainingQuestion> recentTasks(List<TrainingQuestion> tasks) {
        // Prefer actually shown tasks; fill the remaining slots with previously prepared tasks.
        List<TrainingQuestion> ordered = new ArrayList<>();
        tasks.stream().filter(q -> !q.used()).forEach(ordered::add);
        tasks.stream().filter(TrainingQuestion::used).forEach(ordered::add);
        return ordered.subList(Math.max(0, ordered.size() - RECENT_TASK_LIMIT), ordered.size());
    }

    private JsonNode request(String rules, ObjectNode input, String schemaName, ObjectNode schema,
                             int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("store", false);
        body.put("max_output_tokens", maxTokens);
        body.put("prompt_cache_key", "wordsmith-" + RULES_VERSION + "-" + schemaName);
        var messages = body.putArray("input");
        messages.addObject().put("role", "system").put("content", rules);
        messages.addObject().put("role", "user").put("content", objectMapper.writeValueAsString(input));
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenAiException(response.statusCode(), "Generation request failed");
        }
        JsonNode envelope = objectMapper.readTree(response.body());
        if (envelope == null || envelope.has("error") && !envelope.path("error").isNull()
                || envelope.has("status") && !"completed".equals(envelope.path("status").asText())) {
            throw new IOException("OpenAI did not complete the generation request");
        }
        return objectMapper.readTree(extractOutputText(envelope));
    }

    private ObjectNode objectSchema(String... fields) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        var required = schema.putArray("required");
        for (String field : fields) {
            properties.putObject(field).put("type", "string");
            required.add(field);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode arraySchema(ObjectNode items, int count) {
        ObjectNode array = objectMapper.createObjectNode();
        array.put("type", "array");
        array.put("minItems", count);
        array.put("maxItems", count);
        array.set("items", items);
        return array;
    }

    private ObjectNode questionSchema() {
        ObjectNode task = objectSchema("text", "starter", "action", "context", "sample_answer");
        ObjectNode item = objectSchema("word", "tasks");
        ((ObjectNode) item.path("properties")).set("tasks", arraySchema(task, 5));
        ObjectNode root = objectSchema("items");
        ((ObjectNode) root.path("properties")).set("items", arraySchema(item, 1));
        return root;
    }

    private ObjectNode reviewSchema() {
        ObjectNode root = objectSchema("accepted", "feedback");
        ((ObjectNode) root.path("properties").path("accepted")).put("type", "boolean");
        return root;
    }

    private List<TrainingQuestion> parseQuestions(JsonNode payload, String requestedWord) throws IOException {
        if (payload == null || !payload.path("items").isArray() || payload.path("items").size() != 1) {
            throw new IOException("OpenAI returned an unexpected vocabulary task set");
        }
        JsonNode item = payload.path("items").get(0);
        if (!requestedWord.equalsIgnoreCase(item.path("word").asText().trim())
                || !item.path("tasks").isArray() || item.path("tasks").size() != 5) {
            throw new IOException("OpenAI must return five tasks for the requested word");
        }
        List<TrainingQuestion> questions = new ArrayList<>();
        for (JsonNode task : item.path("tasks")) {
            for (String field : List.of("text", "starter", "action", "context", "sample_answer")) {
                if (!task.path(field).isTextual() || task.path(field).asText().isBlank()) {
                    throw new IOException("OpenAI returned an incomplete vocabulary task");
                }
            }
            questions.add(new TrainingQuestion(UUID.randomUUID().toString(), task.path("text").asText().trim(),
                    requestedWord, task.path("starter").asText().trim(), task.path("action").asText().trim(),
                    false, task.path("context").asText().trim()));
        }
        return List.copyOf(questions);
    }

    private String extractOutputText(JsonNode response) throws IOException {
        String direct = response.path("output_text").asText("");
        if (!direct.isBlank()) return direct;
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) throw new IOException("OpenAI declined the prompt request");
                if ("output_text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank()) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IOException("OpenAI response did not contain output text");
    }

    private static String readRules(String name) {
        try (InputStream input = OpenAiQuestionGenerator.class.getResourceAsStream("/prompts/" + name)) {
            if (input == null) throw new IllegalStateException("Missing speaking rules: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read speaking rules", e);
        }
    }

    private static String rulesVersion() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((GENERATION_RULES + "\n" + REVIEW_RULES).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String cacheNamespace() {
        return "openai:context-v1:" + config.model() + ":" + RULES_VERSION;
    }

    @Override
    public String source() { return "openai"; }

    public static final class OpenAiException extends IOException {
        private final int statusCode;
        public OpenAiException(int statusCode, String message) {
            super("OpenAI API error (" + statusCode + "): " + message);
            this.statusCode = statusCode;
        }
        public int statusCode() { return statusCode; }
    }
}
