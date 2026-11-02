# HealthSafe

HealthSafe is a Java-based hospital ward and staffing integration system.

The project demonstrates how independently deployable services can exchange
data through REST APIs and asynchronous messaging.

## 1. Project overview

HealthSafe contains five independent Maven services and one shared ActiveMQ
infrastructure directory.

| Component | Directory | Port | Responsibility |
|---|---|---:|---|
| Ingestion Service | `ingestion-service/` | 7030 | Loads, cleans and exposes ward records from a legacy CSV file |
| Ward Service | `ward-service/` | 7031 | Provides ward lookup APIs and receives staffing events |
| Alert Level Service | `alert-level-service/` | 7032 | Stores and exposes the hospital emergency level (0–8) |
| Staffing Service | `staffing-service/` | 7033 | Calculates staffing schedules and publishes schedule events |
| Equipment Alert Service | `equipment-alert-service/` | 7034 | Reserved for equipment-failure alert handling; currently exposes health only |
| ActiveMQ | `common/` | 61616, 8161 | Provides the message broker used by the asynchronous services |

Each service is an independent Maven project. There is no root parent POM.
Build, test and package each service from its own directory.

## 2. Current implementation status

| Iteration | Functionality | Status |
|---|---|---|
| Iteration 1 | CSV ingestion, data cleaning, validation and deduplication | Implemented |
| Iteration 2 | Ward, Alert Level and Staffing REST APIs | Implemented |
| Iteration 3 | Staffing event publishing and Ward event consumption through ActiveMQ | Implemented |
| Iteration 4 | Equipment failure queue producer and consumer | Not implemented yet |

The Equipment Alert Service is currently a placeholder. Its `/health` endpoint
works, but equipment-failure messages are not yet being published or consumed.

## 3. Architecture

### 3.1 Ward ingestion

The Ingestion Service reads:

`ingestion-service/src/main/resources/wards-outdated.csv`

It normalises ward IDs, wing names and department names, validates bed counts,
and merges duplicate ward records.

The cleaned records are exposed through:

`GET http://localhost:7030/wards`

Ward Service retrieves this catalogue when it starts and can refresh it through
its administrative endpoint.

Staffing Service also loads its own ward catalogue from Ingestion Service at
startup. This avoids requiring Staffing Service to call Ward Service just to
validate a ward during schedule calculation.

### 3.2 REST integration

The Alert Level Service stores the current emergency level.

Staffing Service reads this level when calculating a schedule.

The current alert level must be between `0` and `8`, inclusive.

### 3.3 Asynchronous messaging

Staffing Service publishes a `STAFFING_SCHEDULE_UPDATED` event after a
schedule is generated through:

`POST /schedule/{wardId}`

The event is sent to the ActiveMQ topic:

`staffing-events-topic`

Ward Service subscribes to the topic and updates its in-memory staffing state.

This means Ward Service receives schedule updates asynchronously rather than
polling Staffing Service.

See [common/README.md](common/README.md) for the broker setup and event contract.

### 3.4 Equipment alerting

The planned Iteration 4 flow is:

```text
Ward Service
    |
    | Equipment failure event
    v
ActiveMQ Queue
equipment-failure-queue
    |
    v
Equipment Alert Service
```

The queue configuration exists, but the producer and consumer workflow has
not been implemented yet.

## 4. Technology stack

| Technology | Purpose |
|---|---|
| Java 21 | Application language and runtime |
| Maven | Independent service builds and dependency management |
| Javalin 5.6.3 | REST APIs |
| Jackson | JSON serialisation and deserialisation |
| OpenCSV 5.9 | Legacy CSV parsing |
| Apache ActiveMQ Classic 5.18.3 | Asynchronous messaging |
| JUnit 5 | Automated tests |
| Docker Compose | Local ActiveMQ broker |

## 5. Requirements

Install the following before running the project:

- JDK 21
- Maven 3.8 or later
- Docker Desktop with Docker Compose
- Git

Verify Java and Maven:

```powershell
java -version
mvn -version
docker --version
docker compose version
```

## 6. Build and test

Run the following from the repository root in PowerShell to build and test
every independent Maven module:

```powershell
$modules = @(
    "ingestion-service",
    "ward-service",
    "alert-level-service",
    "staffing-service",
    "equipment-alert-service"
)

foreach ($module in $modules) {
    Write-Host "Building and testing $module"

    Push-Location $module

    mvn clean verify
    $exitCode = $LASTEXITCODE

    Pop-Location

    if ($exitCode -ne 0) {
        throw "Build or tests failed in $module"
    }
}
```

To build and test one service only, enter its directory and run:

```powershell
mvn clean test
```

To package an executable JAR:

```powershell
mvn clean package
```

There is no root Maven project. Running `mvn clean install` from the repository
root will not build all services.

## 7. Start the application

Each service runs in its own terminal.

### Step 1 — Start ActiveMQ

From the repository root:

```powershell
docker compose -f .\common\docker-compose.yml up -d
```

Check that the broker is running:

```powershell
docker compose -f .\common\docker-compose.yml ps
```

View broker logs:

```powershell
docker compose -f .\common\docker-compose.yml logs -f activemq
```

The broker must be available before starting Ward Service and Staffing Service.

### Step 2 — Start Ingestion Service

Open a new PowerShell terminal:

```powershell
cd .\ingestion-service

mvn clean package

java -jar .\target\ingestion-service.jar
```

### Step 3 — Start Alert Level Service

Open another terminal:

```powershell
cd .\alert-level-service

mvn clean package

java -jar .\target\alert-level-service.jar
```

### Step 4 — Start Ward Service

Open another terminal after Ingestion Service and ActiveMQ are running:

```powershell
cd .\ward-service

mvn clean package

java -jar .\target\ward-service.jar
```

Ward Service loads the ward catalogue from Ingestion Service and connects
to the ActiveMQ topic subscription.

### Step 5 — Start Staffing Service

Open another terminal after Ingestion Service and ActiveMQ are running:

```powershell
cd .\staffing-service

mvn clean package

java -jar .\target\staffing-service.jar
```

Staffing Service loads its ward catalogue during startup and connects to
ActiveMQ for publishing schedule events.

Alert Level Service must also be running when a schedule is requested.

### Step 6 — Start Equipment Alert Service

Open another terminal:

```powershell
cd .\equipment-alert-service

mvn clean package

java -jar .\target\equipment-alert-service.jar
```

This service currently exposes `/health` only. Its queue consumer is
planned work.

## 8. Service endpoints

| Service | Method | Endpoint | Purpose |
|---|---|---|---|
| Ingestion | GET | `/health` | Health check |
| Ingestion | GET | `/wards` | Return cleaned ward records |
| Ward | GET | `/health` | Health check |
| Ward | GET | `/wards` | Return ward catalogue |
| Ward | GET | `/wards/{id}` | Find a ward |
| Ward | POST | `/admin/refresh` | Reload wards from Ingestion Service |
| Ward | GET | `/staffing-updates` | List latest staffing events |
| Ward | GET | `/staffing-updates/{wardId}` | Get the latest event for a ward |
| Alert Level | GET | `/health` | Health check |
| Alert Level | GET | `/alert-level` | Read the current emergency level |
| Alert Level | POST | `/alert-level` | Update the emergency level |
| Alert Level | PUT | `/alert-level` | Update the emergency level |
| Staffing | GET | `/health` | Health check |
| Staffing | GET | `/schedule/{wardId}` | Preview a schedule without publishing |
| Staffing | POST | `/schedule/{wardId}` | Generate a schedule and publish an event |
| Equipment Alert | GET | `/health` | Health check |

All service URLs use `http://localhost` with the ports listed in the overview.

## 9. Verify the running services

Run these commands in PowerShell after starting the services.

### Check health endpoints

```powershell
Invoke-RestMethod http://localhost:7030/health
Invoke-RestMethod http://localhost:7031/health
Invoke-RestMethod http://localhost:7032/health
Invoke-RestMethod http://localhost:7033/health
Invoke-RestMethod http://localhost:7034/health
```

### Read cleaned ward records

```powershell
Invoke-RestMethod http://localhost:7030/wards |
    ConvertTo-Json -Depth 5
```

### Read the Ward Service catalogue

```powershell
Invoke-RestMethod http://localhost:7031/wards |
    ConvertTo-Json -Depth 5
```

### Read the current alert level

```powershell
Invoke-RestMethod http://localhost:7032/alert-level
```

### Update the alert level

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7032/alert-level `
    -Method Post `
    -ContentType "application/json" `
    -Body '{"level":5}'
```

### Preview a staffing schedule

```powershell
Invoke-RestMethod http://localhost:7033/schedule/W-01 |
    ConvertTo-Json -Depth 5
```

### Generate a schedule and publish an event

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7033/schedule/W-01 `
    -Method Post |
    ConvertTo-Json -Depth 5
```

### Check whether Ward Service received the event

```powershell
Invoke-RestMethod `
    http://localhost:7031/staffing-updates/W-01 |
    ConvertTo-Json -Depth 5
```

The staffing update is delivered asynchronously, so it may not be visible
at the exact instant the schedule request returns.

### Refresh the ward catalogue

```powershell
Invoke-RestMethod `
    -Uri http://localhost:7031/admin/refresh `
    -Method Post
```

## 10. Configuration

The following environment variables can override default service URLs:

| Variable | Default |
|---|---|
| `INGESTION_SERVICE_URL` | `http://localhost:7030/wards` |
| `ALERT_LEVEL_SERVICE_URL` | `http://localhost:7032/alert-level` |

Example PowerShell configuration:

```powershell
$env:INGESTION_SERVICE_URL = "http://localhost:7030/wards"
$env:ALERT_LEVEL_SERVICE_URL = "http://localhost:7032/alert-level"
```

Set the variables in the terminal used to start the relevant service.

## 11. Stop the application

Stop each Java service with `Ctrl+C` in its terminal.

Stop ActiveMQ from the repository root:

```powershell
docker compose -f .\common\docker-compose.yml down
```

The named Docker volume is retained.

To remove the broker volume and its stored data as well:

```powershell
docker compose -f .\common\docker-compose.yml down -v
```

Use the second command only when you intentionally want to delete the
persisted broker data.

## 12. Module documentation

- [Ingestion Service](ingestion-service/README.md)
- [Ward Service](ward-service/README.md)
- [Alert Level Service](alert-level-service/README.md)
- [Staffing Service](staffing-service/README.md)
- [ActiveMQ Infrastructure](common/README.md)
- [Equipment Alert Service](equipment-alert-service/README.md)

## 13. Design notes

- Each service is independently buildable and deployable.
- Service boundaries use HTTP contracts or messaging contracts, not shared
  Java implementation classes.
- Ward and Staffing services maintain their own ward data.
- Staffing schedule events are published through a topic because subscribers
  need to receive broadcast updates.
- The planned equipment-failure workflow uses a queue because it represents
  work that should be handled by a consumer.
- In-memory application state is not a replacement for a production database.