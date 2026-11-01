# Common — ActiveMQ Infrastructure

## Overview

HealthSafe uses Apache ActiveMQ Classic 5.18.3 for asynchronous communication.

- Broker: `tcp://localhost:61616`
- Web console: `http://localhost:8161`
- Web console credentials: `admin` / `admin`
- Topic: `staffing-events-topic`

## Publisher and subscriber

- Publisher: `staffing-service`
- Subscriber: `ward-service`

Staffing Service publishes a `STAFFING_SCHEDULE_UPDATED` event after a
schedule is generated through `POST /schedule/{wardId}`.

Ward Service subscribes to the topic and updates its latest staffing
information for the affected ward. It does not need to poll Staffing
Service to receive these updates.

## Event contract

Every message is JSON with the following fields:

| Field | Type | Description |
|---|---|---|
| eventId | String | Unique event identifier |
| eventType | String | `STAFFING_SCHEDULE_UPDATED` |
| schemaVersion | Integer | Contract version, currently 1 |
| wardId | String | Ward affected |
| department | String | Ward department |
| alertLevel | Integer | Emergency status from 0 to 8 |
| doctorsOnCall | Integer | Computed staffing requirement |
| scheduleFor | String | Schedule period |
| generatedAt | String | ISO-8601 timestamp |

Messages are published with persistent delivery. Ward Service uses a
durable topic subscription with a stable client ID and subscription name.

## Run

From this directory:

```bash
docker compose up -d
docker compose ps
docker compose logs activemq
docker compose down