# Campus Notifications Microservice: System Design Specification

## Stage 1: Core Platform Actions & REST API Design

### 1.1 Core Platform Actions
The foundational backend actions required to support the student-facing notification feed include:
*   **Fetch Notifications**: Retrieve a paginated list of notifications for the authenticated student.
*   **Filter by Category**: Narrow down notifications based on specific types (Placements, Events, Results).
*   **Update Read Status**: Mark a specific notification as 'read' or 'unread'.
*   **Mark All as Read**: Bulk update all unread notifications to 'read' for a student.
*   **Clear Notification History**: Delete or archive notification records for a student's view.

### 1.2 REST API Contract & Design
The system uses a clean, resource-oriented REST API. All endpoints require a valid Student Session Token.

#### A. Fetch Notifications
*   **Method**: `GET`
*   **Path**: `/api/v1/notifications`
*   **Headers**: 
    - `Authorization: Bearer <JWT_TOKEN>`
    - `Accept: application/json`
*   **Query Parameters**:
    - `category`: (Optional) `Placement`, `Event`, `Result`.
    - `page`: (Optional) Default 0.
    - `limit`: (Optional) Default 20.
*   **Success Response (200 OK)**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "id": "uuid-123",
          "type": "Placement",
          "message": "New drive from Google scheduled for tomorrow.",
          "isRead": false,
          "createdAt": "2024-05-16T10:00:00Z"
        }
      ],
      "pagination": { "current": 0, "total": 5 }
    }
    ```

#### B. Update Read Status
*   **Method**: `PATCH`
*   **Path**: `/api/v1/notifications/{id}`
*   **Headers**: 
    - `Authorization: Bearer <JWT_TOKEN>`
    - `Content-Type: application/json`
*   **Request Body**:
    ```json
    { "isRead": true }
    ```
*   **Error Response (404 Not Found)**:
    ```json
    {
      "status": "error",
      "code": "NOT_FOUND",
      "message": "Notification ID not found for this user."
    }
    ```

### 1.3 Real-Time Delivery Mechanism
To ensure immediate delivery, the platform utilizes **WebSockets (WS)** for bidirectional communication over a single TCP connection.

*   **Handshake**: The client initiates a standard HTTP GET with `Upgrade: websocket` headers to `ws://notifications.university.edu/ws/connect`.
*   **Authentication**: The JWT token is passed as a query parameter or via the `Sec-WebSocket-Protocol` header during the handshake.
*   **Inbound Payload Structure**: When a new notification is generated, the server pushes the following JSON event to the active connection:
    ```json
    {
      "event": "NEW_NOTIFICATION",
      "payload": {
        "id": "uuid-456",
        "type": "Result",
        "message": "Semester results for CS101 are now available.",
        "timestamp": "2024-05-16T10:05:00Z"
      }
    }
    ```
