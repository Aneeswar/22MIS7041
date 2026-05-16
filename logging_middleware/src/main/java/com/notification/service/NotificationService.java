package com.notification.service;

import com.notification.repository.NotificationRepository;
import com.notification.utils.Logger;

public class NotificationService {
    private final NotificationRepository repository = new NotificationRepository();

    public void sendNotification(String userId, String message) {
        Logger.log("backend", "info", "service", "Processing notification for user: " + userId);
        
        if (userId == null) {
            Logger.log("backend", "warn", "service", "User ID is null, skipping notification processing.");
            return;
        }

        try {
            repository.save(message);
            Logger.log("backend", "info", "service", "Notification processed successfully.");
        } catch (Exception e) {
            Logger.log("backend", "error", "service", "Failed to process notification: " + e.getMessage());
        }
    }
}
