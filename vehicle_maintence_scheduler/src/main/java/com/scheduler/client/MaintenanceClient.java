package com.scheduler.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.model.DepotResponse;
import com.scheduler.model.VehicleResponse;
import com.scheduler.util.Logger;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MaintenanceClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Properties config = new Properties();
    
    private volatile String accessToken;
    
    private static final String DEPOT_API = "http://4.224.186.213/evaluation-service/depots";
    private static final String VEHICLE_API = "http://4.224.186.213/evaluation-service/vehicles";

    public MaintenanceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        loadConfig();
        this.accessToken = config.getProperty("initial_access_token");
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                // Fallback or handle missing config
                return;
            }
            config.load(input);
        } catch (Exception e) {
            // Handle error
        }
    }

    /**
     * Executes an HTTP request with automatic 401 retry and token refresh.
     */
    private HttpResponse<String> executeWithAuth(HttpRequest.Builder requestBuilder) throws Exception {
        HttpRequest request = requestBuilder.copy()
                .header("Authorization", "Bearer " + accessToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            Logger.log("backend", "warn", "service", "Token expired (401). Attempting refresh...");
            synchronized (this) {
                // Check if another thread already refreshed while we waited for lock
                refreshToken();
            }
            // Retry once with new token
            request = requestBuilder.copy()
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }

    private void refreshToken() throws Exception {
        Map<String, String> authPayload = new HashMap<>();
        authPayload.put("email", config.getProperty("email"));
        authPayload.put("name", config.getProperty("name"));
        authPayload.put("rollNo", config.getProperty("roll_no"));
        authPayload.put("accessCode", config.getProperty("access_code"));
        authPayload.put("clientID", config.getProperty("client_id"));
        authPayload.put("clientSecret", config.getProperty("client_secret"));

        String jsonPayload = objectMapper.writeValueAsString(authPayload);

        HttpRequest authRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getProperty("auth_url")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            JsonNode root = objectMapper.readTree(response.body());
            // Assuming the response JSON has "access_token" field
            this.accessToken = root.has("access_token") ? root.get("access_token").asText() : this.accessToken;
            Logger.log("backend", "info", "service", "Token refreshed successfully.");
        } else {
            Logger.log("backend", "error", "service", "Token refresh failed. Status: " + response.statusCode());
        }
    }

    public DepotResponse fetchDepots() throws Exception {
        Logger.log("backend", "info", "service", "Fetching depots data.");
        
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(DEPOT_API))
                .GET();

        HttpResponse<String> response = executeWithAuth(builder);
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch depots: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), DepotResponse.class);
    }

    public VehicleResponse fetchVehicles() throws Exception {
        Logger.log("backend", "info", "service", "Fetching vehicles data.");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(VEHICLE_API))
                .GET();

        HttpResponse<String> response = executeWithAuth(builder);

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch vehicles: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), VehicleResponse.class);
    }
}
