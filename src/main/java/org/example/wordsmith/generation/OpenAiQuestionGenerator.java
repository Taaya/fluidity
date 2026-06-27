package org.example.wordsmith.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.wordsmith.config.AppConfig.OpenAiConfig;
import org.example.wordsmith.domain.TrainingQuestion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OpenAiQuestionGenerator implements QuestionGenerator {
    private static final URI RESPONSES_ENDPOINT = URI.create("https://api.openai.com/v1/responses");

    private final OpenAiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiQuestionGenerator(OpenAiConfig config, ObjectMapper objectMapper) {
        if (!config.isConfigured()) {
            throw new IllegalArgumentException(
                    "OpenAI generation requires OPENAI_API_KEY and OPENAI_PROMPT_ID"
            );
        }

        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<TrainingQuestion> generate(List<String> topics, List<String> words) throws Exception {
        String requestBody = objectMapper.writeValueAsString(buildRequest(topics, words));
        HttpRequest request = HttpRequest.newBuilder(RESPONSES_ENDPOINT)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenAiException(response.statusCode(), extractError(response.body()));
        }

        return parseQuestions(response.body());
    }

    private ObjectNode buildRequest(List<String> topics, List<String> words) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", config.model());
        request.put("store", false);
        request.put("max_output_tokens", 3000);
        request.put("prompt_cache_key", promptCacheKey());

        ObjectNode prompt = request.putObject("prompt");
        prompt.put("id", config.promptId());
        if (!config.promptVersion().isBlank()) {
            prompt.put("version", config.promptVersion());
        }
        ObjectNode variables = prompt.putObject("variables");
        variables.put("topics_json", objectMapper.writeValueAsString(topics));
        variables.put("words_json", objectMapper.writeValueAsString(words));

        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "training_questions");
        format.put("strict", true);
        format.set("schema", questionSchema());

        return request;
    }

    private ObjectNode questionSchema() {
        ObjectNode questionProperties = objectMapper.createObjectNode();
        questionProperties.putObject("text").put("type", "string");
        questionProperties.putObject("word").put("type", "string");
        questionProperties.putObject("topic").put("type", "string");

        ObjectNode question = objectMapper.createObjectNode();
        question.put("type", "object");
        question.set("properties", questionProperties);
        question.putArray("required").add("text").add("word").add("topic");
        question.put("additionalProperties", false);

        ObjectNode questions = objectMapper.createObjectNode();
        questions.put("type", "array");
        questions.set("items", question);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.putObject("properties").set("questions", questions);
        root.putArray("required").add("questions");
        root.put("additionalProperties", false);
        return root;
    }

    private List<TrainingQuestion> parseQuestions(String responseBody) throws IOException {
        JsonNode response = objectMapper.readTree(responseBody);
        String outputText = extractOutputText(response);
        JsonNode payload = objectMapper.readTree(outputText);
        JsonNode questionsNode = payload.path("questions");

        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new IOException("OpenAI returned no training questions");
        }

        List<TrainingQuestion> questions = new ArrayList<>();
        for (JsonNode questionNode : questionsNode) {
            String text = questionNode.path("text").asText("").trim();
            String word = questionNode.path("word").asText("").trim();
            String topic = questionNode.path("topic").asText("").trim();

            if (text.isBlank() || word.isBlank() || topic.isBlank()) {
                throw new IOException("OpenAI returned an incomplete training question");
            }

            questions.add(new TrainingQuestion(
                    UUID.randomUUID().toString(),
                    text,
                    word,
                    topic
            ));
        }

        return List.copyOf(questions);
    }

    private String extractOutputText(JsonNode response) throws IOException {
        String directOutput = response.path("output_text").asText("");
        if (!directOutput.isBlank()) {
            return directOutput;
        }

        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        throw new IOException("OpenAI response did not contain output text");
    }

    private String extractError(String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText("");
            return message.isBlank() ? body : message;
        } catch (IOException ignored) {
            return body;
        }
    }

    private String promptCacheKey() {
        String version = config.promptVersion().isBlank() ? "latest" : config.promptVersion();
        return "wordsmith-questions-" + config.promptId() + "-" + version;
    }

    @Override
    public String cacheNamespace() {
        String version = config.promptVersion().isBlank() ? "latest" : config.promptVersion();
        return "openai:" + config.model() + ":" + config.promptId() + ":" + version;
    }

    @Override
    public String source() {
        return "openai";
    }

    public static final class OpenAiException extends IOException {
        private final int statusCode;

        public OpenAiException(int statusCode, String message) {
            super("OpenAI API error (" + statusCode + "): " + message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
