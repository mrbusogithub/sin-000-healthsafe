# Equipment Alert Service

**Port:** `7034`  
**Main class:** `co.wethinkcode.healthsafe.EquipmentAlertServiceApp`

## 1. Purpose

Equipment Alert Service is the planned consumer for critical medical
equipment-failure alerts.

It is intended to receive equipment-failure messages from the ActiveMQ queue:

`equipment-failure-queue`

## 2. Current implementation status

<mark>Iteration 4 is not implemented yet.</mark>

The current application only exposes:

`GET /health`

The queue configuration contains the broker URL and queue name, but the
application does not yet create a JMS connection or consume messages.

Ward Service also does not yet publish equipment-failure events.

Do not treat the queue workflow as operational until both the producer and
consumer have been implemented and tested.

## 3. Planned message flow

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
    |
    v
Process and record the alert
```

A queue is used for this planned workflow because each failure alert represents
a unit of work that should be delivered to a consumer.

## 4. Project structure

```text
equipment-alert-service/
├── pom.xml
├── README.md
└── src/main/java/co/wethinkcode/healthsafe/
    ├── EquipmentAlertServiceApp.java
    └── mq/
        └── MqConfig.java
```

## 5. REST API

Base URL:

`http://localhost:7034`

### Health check

```http
GET /health
```

PowerShell:

```powershell
Invoke-RestMethod http://localhost:7034/health
```

Expected result:

```text
OK
```

No equipment-alert REST endpoints are implemented yet.

## 6. Build

Open PowerShell in the `equipment-alert-service` directory:

```powershell
mvn clean package
```

## 7. Run

```powershell
java -jar .\target\equipment-alert-service.jar
```

The service listens on:

`http://localhost:7034`

## 8. Test

There are currently no automated tests in this module.

Run Maven's test phase:

```powershell
mvn clean test
```

This currently verifies the Maven test phase, but does not exercise an
equipment-alert consumer because that functionality has not been implemented.

## 9. Dependencies

- Java 17 or later
- Maven
- Javalin 5.6.3
- Apache ActiveMQ Client 5.18.3

## 10. Planned Iteration 4 work

1. Define the equipment-failure message contract.
2. Implement an equipment-failure event producer in Ward Service.
3. Publish messages to `equipment-failure-queue`.
4. Implement the JMS queue consumer in this service.
5. Configure persistent messages and acknowledgement behaviour.
6. Handle malformed messages and processing failures.
7. Add producer, consumer and integration tests.
8. Document how a failure is detected and how an alert is processed.