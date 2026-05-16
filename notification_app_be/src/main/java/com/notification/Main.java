package com.notification;

import com.notification.utils.Logger;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting application...");

        // Example Logs
        Logger.log("backend", "info", "utils", "Logger system initialized successfully.");
        
        try {
            // Simulating a process
            performDatabaseOperation();
        } catch (Exception e) {
            Logger.log("backend", "error", "db", "Failed to connect to database: " + e.getMessage());
        }

        Logger.log("backend", "fatal", "db", "Critical database connection failure.");
        
        // Wait a bit for async calls to finish before main thread exits
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        System.out.println("Application finished.");
    }

    private static void performDatabaseOperation() throws Exception {
        throw new Exception("Connection timeout");
    }
}
