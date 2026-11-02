# Ward Service

**Port:** `7031`  
**Main class:** `co.wethinkcode.healthsafe.WardServiceApp`

## 1. Purpose

Ward Service exposes the hospital ward catalogue through REST endpoints.

It loads its initial catalogue from Ingestion Service and subscribes to the
ActiveMQ topic `staffing-events-topic` to receive staffing schedule updates.

## 2. Responsibilities

| Component | Responsibility |
|---|---|
| `WardRoutes` | Defines the REST endpoints |
| `WardCatalogService` | Maintains and refreshes the ward catalogue |
| `IngestionWardClient` | Retrieves cleaned ward records from Ingestion Service |
| `StaffingUpdateService` | Validates events and maintains the latest staffing update per ward |
| `JmsStaffingEventConsumer` | Receives staffing events from ActiveMQ |

Ward catalogue data and staffing updates are maintained in memory. The
durable topic subscription allows the broker to retain eligible messages
while the subscriber is disconnected, but the application's in-memory
catalogue and accepted staffing state are not a database.

## 3. Startup dependencies

Start these components before Ward Service:

1. ActiveMQ broker.
2. Ingestion Service.

Ward Service attempts to load the catalogue from Ingestion Service up to five
times, with a two-second delay between attempts.

If the catalogue cannot be loaded, the current implementation logs a warning
and starts with an empty catalogue. You can retry the load through
`POST /admin/refresh`.

The ActiveMQ broker must also be available for the JMS subscriber.

## 4. Project structure

```text
ward-service/
├── pom.xml
├── README.md
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── WardServiceApp.java
    │   ├── api/
    │   │   └── WardRoutes.java
    │   ├── application/
    │   │   ├── StaffingUpdateService.java
    │   │   └── WardCatalogService.java
    │   ├── domain/
    │   │   └── Ward.java
    │   ├── infrastructure/
    │   │   ├── http/
    │   │   │   └── IngestionWardClient.java
    │   │   └── messaging/
    │   │       └── JmsStaffingEventConsumer.java
    │   ├── messaging/
    │   │   └── StaffingEvent.java
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── application/
        │   └── StaffingUpdateServiceTest.java
        └── infrastructure/messaging/
            └── JmsStaffingEventConsumerTest.java
```

## 5. REST API

Base URL:

`http://localhost:7031`

### Health check

```http
GET /health
```

```powershell
Invoke-RestMethod http://localhost:7031/health
```

### List all wards

```http
GET /wards
```

```powershell
Invoke-RestMethod http://localhost:7031/wards |
    ConvertTo-Json -Depth 5
```

### Find a ward

```http
GET /wards/{id}
```

Example:

```powershell
Invoke-RestMethod http://localhost:7031/wards/W-01
```

An unknown ward returns HTTP `404`.

### Refresh the ward catalogue

```http
POST /admin/refresh
```

This asks Ingestion Service for the latest cleaned ward catalogue.

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7031/admin/refresh `
    -Method Post
```

A successful response reports the number of refreshed records. An upstream
failure is returned as HTTP `503`.

### List staffing updates

```http
GET /staffing-updates
```

```powershell
Invoke-RestMethod http://localhost:7031/staffing-updates |
    ConvertTo-Json -Depth 5
```

### Retrieve one ward's latest staffing update

```http
GET /staffing-updates/{wardId}
```

Example:

```powershell
Invoke-RestMethod `
    http://localhost:7031/staffing-updates/W-01 |
    ConvertTo-Json -Depth 5
```

An unknown staffing update returns HTTP `404`.

## 6. ActiveMQ subscription

Ward Service subscribes to:

`staffing-events-topic`

It processes `STAFFING_SCHEDULE_UPDATED` events published by Staffing Service.

The application validates the event contract and keeps the latest accepted
event for each ward. Duplicate or older updates are ignored.

See [common/README.md](../common/README.md) for the broker setup and message
contract.

## 7. Build

Open PowerShell in the `ward-service` directory:

```powershell
mvn clean package
```

## 8. Run

```powershell
java -jar .\target\ward-service.jar
```

The service listens on:

`http://localhost:7031`

## 9. Test

Run the complete test suite:

```powershell
mvn clean test
```

Run the application-service tests:

```powershell
mvn -Dtest=StaffingUpdateServiceTest test
```

Run the JMS consumer tests:

```powershell
mvn -Dtest=JmsStaffingEventConsumerTest test
```

The tests cover event validation, state updates, duplicate or stale events,
and message-consumer behaviour.

## 10. Configuration

The Ingestion Service URL can be overridden with:

`INGESTION_SERVICE_URL`

Default:

`http://localhost:7030/wards`

PowerShell example:

```powershell
$env:INGESTION_SERVICE_URL = "http://localhost:7030/wards"
```

## 11. Dependencies

- Java 21
- Maven
- Javalin 5.6.3
- Jackson Databind
- Apache ActiveMQ Client 5.18.3
- JUnit 5.10.2
- Maven Surefire 3.2.5