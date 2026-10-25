# Personal Expense Tracker

A production-grade Personal Expense Tracker built with **Java 17** and **Spring Boot 3**. Designed using clean architecture principles, the application provides both an **interactive console interface** and a comprehensive **RESTful HTTP API** backed by a pluggable storage layer supporting both local **RFC 4180 CSV persistence** and relational **PostgreSQL persistence**.

---

## Architecture Overview

```
                         +-----------------------------------+
                         |      HTTP Clients / Frontend     |
                         +-----------------+-----------------+
                                           |
                                           v
                         +-----------------------------------+
                         |         ExpenseController         |
                         |   (REST API / DTOs / Validation)  |
                         +-----------------+-----------------+
                                           |
+--------------------+                     |
|  Interactive CLI   |                     |
|    (Main.java)     |                     |
+---------+----------+                     |
          |                                |
          +---------------->+<-------------+
                            |
                            v
              +---------------------------+
              |      ExpenseService       |
              |  (Business Logic & DSA)   |
              +-------------+-------------+
                            |
                            v
              +---------------------------+
              |     ExpenseRepository     |
              |     (Storage Abstraction) |
              +-------------+-------------+
                            |
            +---------------+---------------+
            |                               |
            v                               v
+-----------------------+       +-----------------------+
|  CsvExpenseRepository |       | PostgresExpenseRepo   |
| (Atomic File Storage) |       | (HikariCP / SQL JDBC) |
+-----------------------+       +-----------------------+
```

### Key Engineering Features
- **Pluggable Storage Abstraction**: Decoupled repository interface (`ExpenseRepository`) allowing runtime switching between local CSV files and PostgreSQL without changing business logic.
- **Transactional Rollback Safety**: In-memory state and sequential counters are safely rolled back if persistence write operations fail, preventing data corruption or desynchronization.
- **Efficient Data Structures & Algorithms**:
  - **Bounded Min-Heap (`PriorityQueue`)**: Efficient Top-\(K\) expense extraction in \(O(N \log K)\) time complexity.
  - **Single-Pass Aggregations**: Linear \(O(N)\) filtering and category-wise spending summaries with precise percentage calculations.
  - **Defensive Deep Copying**: All queries return detached domain copies within unmodifiable collections to guarantee internal state encapsulation.
- **Zero Floating-Point Drift**: Strict usage of `BigDecimal` for currency and `LocalDate` (ISO-8601) for dates throughout the domain model and REST endpoints.
- **Centralized Exception Handling**: Uniform error responses (`ErrorResponse`) with HTTP status mapping (400 Bad Request, 404 Not Found, 500 Internal Server Error) via `@RestControllerAdvice`.

---

## REST API Specification

Base URL: `/api/expenses`

### Endpoints Summary

| Method | Endpoint | Description | Success Status |
|---|---|---|---|
| `GET` | `/api/expenses` | Retrieve all expenses (supports filtering & sorting) | `200 OK` |
| `GET` | `/api/expenses/{id}` | Retrieve a specific expense by ID | `200 OK` |
| `POST` | `/api/expenses` | Create and persist a new expense | `201 Created` |
| `PUT` | `/api/expenses/{id}` | Update an existing expense by ID | `200 OK` |
| `DELETE` | `/api/expenses/{id}` | Delete an expense by ID | `204 No Content` |

---

### 1. List Expenses
`GET /api/expenses`

Supports optional query parameters for filtering and sorting:
- `category` *(optional, string)*: Filter expenses by category (case-insensitive).
- `startDate` *(optional, ISO date `YYYY-MM-DD`)*: Inclusive start date boundary.
- `endDate` *(optional, ISO date `YYYY-MM-DD`)*: Inclusive end date boundary.
- `topK` *(optional, integer > 0)*: Retrieve the top \(K\) highest monetary expenses.
- `sortBy` *(optional, `"amount"` or `"date"`)*: Sort results by amount or date.
- `ascending` *(optional, boolean, default: `true`)*: Sort direction.

#### Example Request
```bash
curl -X GET "http://localhost:8080/api/expenses"
```

#### Example Response (`200 OK`)
```json
[
  {
    "id": 1,
    "description": "Team lunch",
    "category": "Food",
    "amount": 32.50,
    "date": "2026-10-20"
  },
  {
    "id": 2,
    "description": "Metro card reload",
    "category": "Transport",
    "amount": 15.00,
    "date": "2026-10-21"
  }
]
```

---

### 2. Retrieve Expense by ID
`GET /api/expenses/{id}`

#### Example Request
```bash
curl -X GET "http://localhost:8080/api/expenses/1"
```

#### Example Response (`200 OK`)
```json
{
  "id": 1,
  "description": "Team lunch",
  "category": "Food",
  "amount": 32.50,
  "date": "2026-10-20"
}
```

---

### 3. Create Expense
`POST /api/expenses`

#### Request Headers
- `Content-Type: application/json`

#### Validation Rules
- `description`: Required, non-blank string.
- `category`: Required, non-blank string.
- `amount`: Required positive number strictly greater than 0.
- `date`: Required ISO-8601 date (`YYYY-MM-DD`).

#### Example Request
```bash
curl -X POST "http://localhost:8080/api/expenses" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Grocery shopping",
    "category": "Food",
    "amount": 54.20,
    "date": "2026-10-25"
  }'
```

#### Example Response (`201 Created`)
**Headers:**
- `Location: http://localhost:8080/api/expenses/3`

**Body:**
```json
{
  "id": 3,
  "description": "Grocery shopping",
  "category": "Food",
  "amount": 54.20,
  "date": "2026-10-25"
}
```

---

### 4. Update Expense
`PUT /api/expenses/{id}`

#### Example Request
```bash
curl -X PUT "http://localhost:8080/api/expenses/3" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Weekly organic groceries",
    "category": "Food",
    "amount": 62.80,
    "date": "2026-10-25"
  }'
```

#### Example Response (`200 OK`)
```json
{
  "id": 3,
  "description": "Weekly organic groceries",
  "category": "Food",
  "amount": 62.80,
  "date": "2026-10-25"
}
```

---

### 5. Delete Expense
`DELETE /api/expenses/{id}`

#### Example Request
```bash
curl -X DELETE "http://localhost:8080/api/expenses/3"
```

#### Example Response (`204 No Content`)
*(Empty response body)*

---

### 6. Error Responses
All client and server errors return a standard JSON structure:

```json
{
  "timestamp": "2026-10-25T22:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for request object.",
  "path": "/api/expenses",
  "details": [
    "amount: Amount must be strictly positive (greater than 0).",
    "description: Description cannot be null or blank."
  ]
}
```

#### Common Error Status Codes:
- `400 Bad Request`: Field validation failure, invalid argument type, non-positive ID, or malformed JSON body.
- `404 Not Found`: Requested expense ID does not exist.
- `500 Internal Server Error`: Persistence failure or unexpected server error.

---

## Configuration & Storage Modes

The active repository implementation is configured using the `STORAGE_TYPE` environment variable or Java system property `storage.type`:

### 1. CSV Persistence (Default)
When `STORAGE_TYPE` is unset or set to `csv`:
- Data is saved to `data/expenses.csv`.
- Writes use temporary files and atomic file replacement (`ATOMIC_MOVE`) to eliminate corruption risks.
- Supports commas, quotes, and multiline descriptions compliant with RFC 4180.

### 2. PostgreSQL Persistence
To enable PostgreSQL storage, set `STORAGE_TYPE=postgres` along with database connection details:

```bash
export STORAGE_TYPE=postgres
export DB_URL=jdbc:postgresql://localhost:5432/expensedb
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
```

Optional connection pool settings:
- `DB_MAX_POOL_SIZE`: Maximum HikariCP pool connections (default: `10`).
- `DB_MIN_IDLE`: Minimum idle connections (default: `2`).

---

## Building and Running

### Prerequisites
- **Java 17** or higher
- Included Maven Wrapper (`mvnw` / `mvnw.cmd`)

### Run All Tests
```bash
.\mvnw.cmd clean test
```
All unit, MVC, and integration tests execute in-memory with zero Docker or live database dependencies.

### Run the Spring Boot REST API
```bash
.\mvnw.cmd spring-boot:run
```
The server will start on `http://localhost:8080`.

### Run the Interactive Console Application
```bash
.\mvnw.cmd compile
java -cp "target/classes;target/dependency/*" com.expense.Main
```
or run `Main.java` directly from your IDE.
