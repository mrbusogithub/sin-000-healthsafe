# StaffingServiceApp

## Overview

Provides on-call schedules for doctors based on ward and status.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `staffing-events-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.

REST: calls `ward-service` (`../ward-service`) to validate the ward and
`alert-level-service` (`../alert-level-service`) to read the current Emergency
Status before computing a schedule — see [Integration contracts](../README.md#integration-contracts)
in the root README for the endpoint shapes.

## Project structure

```
staffing-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── StaffingServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/staffing-service.jar
```

Listens on port `7033`.

## REST endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Service health |
| GET | `/schedule/{wardId}` | Preview a schedule without publishing |
| POST | `/schedule/{wardId}` | Generate a schedule and publish a staffing event |

## Messaging

Publishes JSON staffing events to `staffing-events-topic` through
`co.wethinkcode.healthsafe.mq.MqConfig`.

The broker must be available before a schedule-generation request can
complete successfully.
