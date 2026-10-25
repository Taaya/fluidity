package org.example.wordsmith.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.generation.PromptUnavailableException;
import org.example.wordsmith.service.TrainingQuestionService;
import org.example.wordsmith.service.TrainingQuestionService.ValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class WordsmithServer {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final TrainingQuestionService service;
    private final QuestionGenerator generator;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WordsmithServer(
            int port,
            ObjectMapper objectMapper,
            TrainingQuestionService service,
            QuestionGenerator generator
    ) throws IOException {
        this.objectMapper = objectMapper;
        this.service = service;
        this.generator = generator;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/api/health", this::handleHealth);
        this.server.createContext("/api/training-sessions", this::handleTrainingSession);
        this.server.createContext("/api/dictionary-items/tasks", this::handleDictionaryTasks);
        this.server.createContext("/", this::handleStaticFile);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
        executor.shutdownNow();
    }

    public int port() { return server.getAddress().getPort(); }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        sendJson(exchange, 200, Map.of(
                "status", "ok",
                "generator", generator.source()
        ));
    }

    private void handleTrainingSession(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            TrainingSessionRequest request =
                    objectMapper.readValue(readRequestBody(exchange), TrainingSessionRequest.class);
            TrainingSession session = service.createSession(request.words(), request.level(), learnerId(exchange), request.sessionSize());
            sendJson(exchange, 200, toResponse(session));
        } catch (ValidationException | JsonProcessingException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (PromptUnavailableException exception) {
            sendError(exchange, 503, exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 502, "Could not prepare this training session");
        }
    }

    private TrainingSessionResponse toResponse(TrainingSession session) {
        QuestionSet questionSet = session.questionSet();
        return new TrainingSessionResponse(
                session.sessionId(),
                questionSet.words(),
                questionSet.questions(),
                session.cached(),
                questionSet.source(),
                questionSet.generatedAt().toString(),
                session.unavailableWords()
        );
    }

    private void handleDictionaryTasks(HttpExchange exchange) throws IOException {
        try {
            if ("POST".equals(exchange.getRequestMethod())) {
                DictionaryTaskRequest request =
                        objectMapper.readValue(readRequestBody(exchange), DictionaryTaskRequest.class);
                QuestionSet questionSet = service.prepareDictionaryItem(request.word(), request.level(), learnerId(exchange));
                sendJson(exchange, 200, toDictionaryTaskResponse(questionSet));
                return;
            }

            if ("PATCH".equals(exchange.getRequestMethod())) {
                DictionaryTaskUsedRequest request =
                        objectMapper.readValue(readRequestBody(exchange), DictionaryTaskUsedRequest.class);
                service.markTaskUsed(request.word(), request.taskId(), request.level(), learnerId(exchange));
                sendJson(exchange, 200, Map.of("recorded", true));
                return;
            }

            sendMethodNotAllowed(exchange, "POST, PATCH");
        } catch (ValidationException | JsonProcessingException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (PromptUnavailableException exception) {
            sendError(exchange, 503, exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 502, "Could not prepare vocabulary tasks");
        }
    }

    private DictionaryTaskResponse toDictionaryTaskResponse(QuestionSet questionSet) {
        return new DictionaryTaskResponse(
                questionSet.words().get(0),
                questionSet.questions(),
                questionSet.source(),
                questionSet.generatedAt().toString()
        );
    }

    private String learnerId(HttpExchange exchange) {
        // Anonymous browser profile, not authentication. Do not store personal data under this ID.
        return exchange.getRequestHeaders().getFirst("X-Learner-Id");
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        String resourcePath = switch (requestPath) {
            case "/", "/index.html" -> "/public/index.html";
            case "/add-words", "/add-words.html" -> "/public/index.html";
            case "/styles.css" -> "/public/styles.css";
            case "/script.js" -> "/public/script.js";
            case "/prompt-history.js" -> "/public/prompt-history.js";
            default -> requestPath.startsWith("/assets/")
                    ? "/public" + requestPath
                    : null;
        };

        if (resourcePath == null || resourcePath.contains("..")) {
            sendError(exchange, 404, "Not found");
            return;
        }

        try (InputStream input = WordsmithServer.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                sendError(exchange, 404, "Not found");
                return;
            }

            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(resourcePath));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private byte[] readRequestBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ValidationException("Request body is too large");
        }
        return body;
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message == null ? "Unexpected error" : message));
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        sendError(exchange, 405, "Method not allowed");
    }

    private String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/html; charset=utf-8";
    }

    private record TrainingSessionRequest(List<String> words, String level, Integer sessionSize) {
    }

    private record TrainingSessionResponse(
            String sessionId,
            List<String> words,
            List<?> questions,
            boolean cached,
            String source,
            String generatedAt,
            List<String> unavailableWords
    ) {
    }

    private record DictionaryTaskRequest(String word, String level) {
    }

    private record DictionaryTaskUsedRequest(String word, String taskId, String level) {
    }

    private record DictionaryTaskResponse(
            String word,
            List<?> tasks,
            String source,
            String generatedAt
    ) {
    }
}
