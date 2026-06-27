package org.example.wordsmith.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.wordsmith.domain.QuestionSet;
import org.example.wordsmith.domain.TrainingSession;
import org.example.wordsmith.generation.QuestionGenerator;
import org.example.wordsmith.service.TrainingQuestionService;
import org.example.wordsmith.service.TrainingQuestionService.ValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WordsmithServer {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final TrainingQuestionService service;
    private final QuestionGenerator generator;
    private final HttpServer server;

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
        this.server.createContext("/", this::handleStaticFile);
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

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
            TrainingSession session = service.createSession(request.topics(), request.words());
            sendJson(exchange, 200, toResponse(session));
        } catch (ValidationException | JsonProcessingException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 502, "Could not prepare this training session");
        }
    }

    private TrainingSessionResponse toResponse(TrainingSession session) {
        QuestionSet questionSet = session.questionSet();
        return new TrainingSessionResponse(
                session.sessionId(),
                questionSet.topics(),
                questionSet.words(),
                questionSet.questions(),
                session.cached(),
                questionSet.source(),
                questionSet.generatedAt().toString()
        );
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        String resourcePath = switch (requestPath) {
            case "/", "/index.html" -> "/public/index.html";
            case "/styles.css" -> "/public/styles.css";
            case "/script.js" -> "/public/script.js";
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

    private record TrainingSessionRequest(List<String> topics, List<String> words) {
    }

    private record TrainingSessionResponse(
            String sessionId,
            List<String> topics,
            List<String> words,
            List<?> questions,
            boolean cached,
            String source,
            String generatedAt
    ) {
    }
}
