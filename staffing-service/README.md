# Staffing Service

**Port:** `7033`  
**Main class:** `co.wethinkcode.healthsafe.StaffingServiceApp`

## 1. Purpose

Staffing Service calculates doctor on-call schedules using:

- The ward catalogue retrieved from Ingestion Service.
- The current emergency level retrieved from Alert Level Service.

When a schedule is generated, the service publishes a staffing event to
ActiveMQ so Ward Service can update its staffing state asynchronously.

## 2. Responsibilities

| Component | Responsibility |
|---|---|
| `StaffingRoutes` | Defines the REST endpoints |
| `StaffingScheduleService` | Calculates schedules and coordinates publishing |
| `StaffingDataClient` | Defines the external data required by scheduling |
| `HttpStaffingDataClient` | Loads wards from Ingestion Service and reads Alert Level Service |
| `StaffingEventPublisher` | Defines the event-publishing boundary |
| `JmsStaffingEventPublisher` | Publishes JSON events to ActiveMQ |
| `StaffingEvent` | Defines the event data contract |

Staffing Service loads its own ward catalogue from Ingestion Service during
startup. It does not need to call Ward Service to look up a ward when
calculating a schedule.

The current alert level is read from Alert Level Service when schedule
calculations are performed.

## 3. Startup dependencies

Start these components before Staffing Service:

1. ActiveMQ broker.
2. Ingestion Service.

Alert Level Service must be running before a schedule is requested.

The application loads its ward catalogue and establishes its ActiveMQ
publisher during startup. If a required startup dependency is unavailable,
the service will not complete startup successfully.

## 4. Project structure

```text
staffing-service/
├── pom.xml
├── README.md
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── StaffingServiceApp.java
    │   ├── api/
    │   │   └── StaffingRoutes.java
    │   ├── application/
    │   │   ├── StaffingDataClient.java
    │   │   ├── StaffingEventPublisher.java
    │   │   ├── StaffingScheduleService.java
    │   │   └── StaffingServiceException.java
    │   ├── domain/
    │   │   ├── StaffingSchedule.java
    │   │   └── WardSummary.java
    │   ├── infrastructure/
    │   │   ├── http/
    │   │   │   └── HttpStaffingDataClient.java
    │   │   └── messaging/
    │   │       └── JmsStaffingEventPublisher.java
    │   ├── messaging/
    │   │   └── StaffingEvent.java
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── application/
        │   └── StaffingScheduleServiceTest.java
        └── infrastructure/messaging/
            └── JmsStaffingEventPublisherTest.java
```

## 5. REST API

Base URL:

`http://localhost:7033`

### Health check

```http
GET /health
```

```powershell
Invoke-RestMethod http://localhost:7033/health
```

### Preview a schedule

```http
GET /schedule/{wardId}
```

This calculates and returns a schedule without publishing a staffing event.

Example:

```powershell
Invoke-RestMethod `
    http://localhost:7033/schedule/W-01 |
    ConvertTo-Json -Depth 5
```

### Generate a schedule

```http
POST /schedule/{wardId}
```

This calculates the schedule and publishes a `STAFFING_SCHEDULE_UPDATED`
event to ActiveMQ.

Example:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7033/schedule/W-01 `
    -Method Post |
    ConvertTo-Json -Depth 5
```

A successful schedule-generation request returns HTTP `201`.

## 6. Event publishing

Staffing Service publishes to:

`staffing-events-topic`

The event type is:

`STAFFING_SCHEDULE_UPDATED`

The event includes:

- Unique event ID.
- Event type and schema version.
- Ward ID and department.
- Alert level.
- Number of doctors on call.
- Schedule period.
- Event generation timestamp.

The message is published using persistent JMS delivery.

See [common/README.md](../common/README.md) for the complete event contract.

## 7. Build

Open PowerShell in the `staffing-service` directory:

```powershell
mvn clean package
```

## 8. Run

```powershell
java -jar .\target\staffing-service.jar
```

The service listens on:

`http://localhost:7033`

## 9. Test

Run the complete test suite:

```powershell
mvn clean test
```

Run the scheduling application tests:

```powershell
mvn -Dtest=StaffingScheduleServiceTest test
```

Run the JMS publisher tests:

```powershell
mvn -Dtest=JmsStaffingEventPublisherTest test
```

The application tests exercise schedule calculation and application behaviour.
The publisher tests exercise message publication using the test broker setup.

## 10. Configuration

The following URLs can be configured using environment variables.

| Variable | Default |
|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7030/wards` |
| `ALERT_LEVEL_SERVICE_URL` | `http://localhost:7032/alert-level` |

PowerShell example:

```powershell
$env:INGESTION_SERVICE_URL = "http://localhost:7030/wards"
$env:ALERT_LEVEL_SERVICE_URL = "http://localhost:7032/alert-level"
```

Set these variables in the terminal used to launch Staffing Service.

## 11. Dependencies

- Java 21
- Maven
- Javalin 5.6.3
- Jackson Databind
- Apache ActiveMQ Client 5.18.3
- JUnit 5.10.2
- Maven Surefire 3.2.5