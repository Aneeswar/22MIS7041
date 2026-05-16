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

## Stage 2: Storage Strategy, Scaling, and Queries

### 2.1 Storage Strategy
The system utilizes **PostgreSQL (RDBMS)** as the primary persistent store.

**Justification:**
*   **ACID Compliance:** Ensures consistent notification delivery and 'read' state across student devices.
*   **Relational Integrity:** Facilitates rigid foreign key relationships between Students and Notifications.
*   **Performance:** Advanced indexing capabilities (GIN, Partial indexes) and JSONB support for future metadata flexibility.

#### Database Schema Definition (DDL)
```sql
CREATE TYPE notification_category AS ENUM ('Placement', 'Event', 'Result');

CREATE TABLE students (
    student_id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(100)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id INT NOT NULL REFERENCES students(student_id),
    category notification_category NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 2.2 High-Volume Scale Bottlenecks
With 50,000 students and potentially millions of notifications per term, performance risks include:
*   **Index Bloat:** Frequent updates to `is_read` create "dead tuples" (MVCC), causing index fragmentation and slower lookups.
*   **Buffer Pool pressure:** Large indexes exceed RAM, leading to disk-thrashing for common reads.

**Engineering Mitigations:**
*   **Declarative Partitioning:** Partition the `notifications` table by `created_at` (Range) or `student_id` (Hash) to localize data scanning.
*   **Index Boundaries:** Utilize **Partial Indexes** (e.g., `CREATE INDEX idx_unread ON notifications (student_id) WHERE is_read = false`) to significantly reduce index size and scan time for active feeds.

### 2.3 Database Queries
These queries back the REST API endpoints established in Stage 1.

#### A. Fetch Recent Notifications (Paginated)
```sql
SELECT id, category, message, is_read, created_at 
FROM notifications 
WHERE student_id = :student_id 
ORDER BY created_at DESC 
LIMIT :limit OFFSET :offset;
```

#### B. Filter Unread Notifications by Category
```sql
SELECT id, message, created_at 
FROM notifications 
WHERE student_id = :student_id 
  AND category = :category 
  AND is_read = FALSE
ORDER BY created_at DESC;
```

#### C. Mark Record as Read
```sql
UPDATE notifications 
SET is_read = TRUE 
WHERE id = :notification_id 
  AND student_id = :student_id;
```

## Stage 3: Production Query Analysis & Indexing Strategy

### 3.1 Production Query Analysis
**Target Query:** `SELECT * FROM notifications WHERE studentID = 1042 AND isRead = false ORDER BY createdAt DESC;`

*   **Accuracy:** The query is functionally accurate for retrieving unread notifications for a specific student.
*   **Performance Diagnostics:**
    *   **Full Row Scanning:** Without a composite index, the database engine likely performs an index scan on `studentID`, then a manual filter for `isRead`, and finally a memory-intensive `Sort` for `createdAt`.
    *   **I/O Latency:** Under a volume of 5 million notifications, the engine must pull non-contiguous data pages from disk into memory, causing a "Random I/O" bottleneck.
    *   **Scale Behavior:** As the `notifications` table grows, the "cost" of the sort operation increases logarithmically, leading to noticeable UI lag for students with high notification counts.

### 3.2 Indexing Strategy Critique
**Addressing the "Index Every Column" Suggestion:**
While well-intentioned, indexing every column is detrimental in production environments due to:
*   **Write-Amplification:** Each `INSERT` or `UPDATE` (like marking a message as read) incurs the overhead of updating every single index, doubling or tripling ingestion latency.
*   **Storage Overhead:** In a 5M row dataset, indexes can easily exceed the size of the raw table, leading to increased infrastructure costs.
*   **Optimizer Overhead:** A surplus of indexes can confuse the Query Optimizer, potentially leading it to choose a suboptimal execution plan for complex queries.

### 3.3 Remediation & Time-Bounded Query

#### Optimized Composite Index
To eliminate the Sort operation and filter efficiently, we apply a composite index following the `Equality -> Sort` pattern:
```sql
CREATE INDEX idx_student_unread_recent 
ON notifications (student_id, is_read, created_at DESC);
```

#### Optimized Time-Bounded Query
Finding students who received a "Placement" notification within the trailing 7 days:
```sql
SELECT student_id, message, created_at 
FROM notifications 
WHERE category = 'Placement' 
  AND created_at >= (CURRENT_DATE - INTERVAL '7 days')
ORDER BY created_at DESC;
```

## Stage 4: Performance Enhancement & Architectural Trade-offs

### 4.1 Mitigation of Database Overwhelm
Aggressive database hits on every page load can be mitigated by introducing a **Caching Layer** and **Read-Optimized Access Patterns**. Instead of querying the multi-million row `notifications` table for basic status checks (like unread counts), we transition to a data-on-demand model using memory-resident stores.

### 4.2 Architectural Trade-offs: Side-by-Side Analysis

| Feature | Pattern A: Distributed Read-Through Caching (Redis) | Pattern B: Stateful Gateway (WS Session Cache) |
| :--- | :--- | :--- |
| **Concept** | Store the most recent 50 notifications per student in an in-memory Redis Cluster. | Maintain unread notification state in the local memory of the WebSocket server for active sessions. |
| **Performance Gain** | **Extreme**: Sub-millisecond latency; reduces DB read load by >90%. | **Optimal for Real-time**: Zero-latency retrieval once the connection is established. |
| **Cache Invalidation** | **Complex**: Requires strict "Write-Through" or "Eviction" logic on every status change. | **Direct**: State is naturally invalidated as soon as the student disconnects or marks as read. |
| **State Sync Complexity** | **Moderate**: Cache remains consistent across all app nodes via Redis protocol. | **High**: Requires "Sticky Sessions" or a backplane to sync if a student has multiple tabs open. |
| **Persistence Risk** | **Low**: Redis provides snapshotting; cache can be rebuilt from the DB. | **Critical**: If the gateway node resets, active unread session info is lost (must re-sync from DB). |

**Architectural Recommendation:** Use **Pattern A (Redis)** for overall system resilience and consistent performance across both web and mobile platforms.
    ```
