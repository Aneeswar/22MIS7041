package com.notification.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Logger {

    private static final String API_ENDPOINT = "http://4.224.186.213/evaluation-service/logs";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Allowed values for validation
    private static final Set<String> ALLOWED_STACKS = Set.of("backend", "frontend");
    private static final Set<String> ALLOWED_LEVELS = Set.of("debug", "info", "warn", "error", "fatal");
    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "cache", "controller", "cron_job", "db", "domain", "handler", "repository", "route", "service",
            "component", "hook", "page", "state", "style",
            "auth", "config", "middleware", "utils"
    );

    /**
     * Logs a message to the centralized logging service.
     *
     * @param stack   The stack (backend/frontend)
     * @param level   The severity level
     * @param pkg     The package name
     * @param message The log message
     */
    public static void log(String stack, String level, String pkg, String message) {
        // 1. Validate inputs (lowercase conversion as per requirement)
        String s = stack.toLowerCase();
        String l = level.toLowerCase();
        String p = pkg.toLowerCase();

        if (!ALLOWED_STACKS.contains(s) || !ALLOWED_LEVELS.contains(l) || !ALLOWED_PACKAGES.contains(p)) {
            System.err.println("Invalid log parameters: stack=" + s + ", level=" + l + ", package=" + p);
            return;
        }

        // 2. Prepare payload
        Map<String, String> payload = new HashMap<>();
        payload.put("stack", s);
        payload.put("level", l);
        payload.put("package", p);
        payload.put("message", message);
        payload.put("timestamp", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // 3. Send Async POST Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .thenAccept(statusCode -> {
                        if (statusCode >= 300) {
                            System.err.println("Logging failed with status code: " + statusCode);
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("Failed to send log to API: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            // Ensure logging failures do not crash the application
            System.err.println("Logger internal failure: " + e.getMessage());
        }
    }
}
