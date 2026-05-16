package com.notification.repository;

import com.notification.utils.Logger;

public class NotificationRepository {
    public void save(String message) {
        Logger.log("backend", "debug", "repository", "Attempting to save notification: " + message);
        try {
            // Simulated DB save
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("Notification message cannot be empty");
            }
            Logger.log("backend", "info", "db", "Notification saved successfully to database.");
        } catch (Exception e) {
            Logger.log("backend", "error", "db", "Database save error: " + e.getMessage());
            throw e;
        }
    }
}
