# Alert Level Service

**Port:** `7032`  
**Main class:** `co.wethinkcode.healthsafe.AlertLevelServiceApp`

## 1. Purpose

Alert Level Service stores the hospital's current emergency level.

The valid range is:

- `0`: Normal operating level.
- `8`: Highest emergency level.

Values below `0` or above `8` are rejected.

## 2. Responsibilities

The service separates three responsibilities:

| Package | Responsibility |
|---|---|
| `domain` | Represents a valid alert level and enforces the 0–8 rule |
| `application` | Reads and updates the current alert level |
| `api` | Defines HTTP endpoints, parses requests and returns responses |

The current value is stored in thread-safe in-memory state. It returns to `0`
when the process restarts; it is not persisted in a database.

## 3. Project structure

```text
alert-level-service/
├── pom.xml
├── README.md
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── AlertLevelServiceApp.java
    │   ├── api/
    │   │   └── AlertLevelRoutes.java
    │   ├── application/
    │   │   └── AlertLevelService.java
    │   └── domain/
    │       └── AlertLevel.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── api/
        │   └── AlertLevelRoutesTest.java
        └── application/
            └── AlertLevelServiceTest.java
```

## 4. REST API

Base URL:

`http://localhost:7032`

### Health check

```http
GET /health
```

PowerShell:

```powershell
Invoke-RestMethod http://localhost:7032/health
```

### Read current alert level

```http
GET /alert-level
```

Example:

```powershell
Invoke-RestMethod http://localhost:7032/alert-level
```

Example response:

```json
{
  "level": 0
}
```

### Update the alert level

Both POST and PUT are supported.

```http
POST /alert-level
Content-Type: application/json
```

Request:

```json
{
  "level": 5
}
```

PowerShell:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7032/alert-level `
    -Method Post `
    -ContentType "application/json" `
    -Body '{"level":5}'
```

The same request can be sent using PUT:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7032/alert-level `
    -Method Put `
    -ContentType "application/json" `
    -Body '{"level":5}'
```

### Validation responses

| Input | Expected status |
|---|---:|
| `{"level":0}` | 200 |
| `{"level":8}` | 200 |
| `{"level":-1}` | 400 |
| `{"level":9}` | 400 |
| `{}` | 400 |
| Empty body | 400 |
| Malformed JSON | 400 |

## 5. Build

Open PowerShell in the `alert-level-service` directory:

```powershell
mvn clean package
```

## 6. Run

```powershell
java -jar .\target\alert-level-service.jar
```

The service listens on:

`http://localhost:7032`

## 7. Test

Run the complete test suite:

```powershell
mvn clean test
```

Run only the application-service tests:

```powershell
mvn -Dtest=AlertLevelServiceTest test
```

Run only the HTTP route tests:

```powershell
mvn -Dtest=AlertLevelRoutesTest test
```

The application tests cover initial state, valid boundary values, invalid
values and repeated updates.

The route tests start a local HTTP server and verify actual HTTP responses
for valid requests, missing values, malformed JSON and out-of-range values.

## 8. Dependencies

- Java 21
- Maven
- Javalin 5.6.3
- Jackson Databind
- JUnit 5.10.2
- Maven Surefire 3.2.5