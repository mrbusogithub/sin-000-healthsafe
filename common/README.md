# Common — ActiveMQ Infrastructure

## 1. Purpose

This directory contains the Docker Compose configuration for the HealthSafe
ActiveMQ broker.

It is infrastructure shared by the independent service modules, not a Java
Maven service.

## 2. Broker configuration

| Setting | Value |
|---|---|
| Broker | Apache ActiveMQ Classic |
| Version | 5.18.3 |
| OpenWire address | `tcp://localhost:61616` |
| Web console | `http://localhost:8161` |
| Local console credentials | `admin` / `admin` |
| Staffing topic | `staffing-events-topic` |
| Planned equipment queue | `equipment-failure-queue` |

The Docker Compose configuration publishes ports `61616` and `8161` and
uses a named volume called `activemq-data`.

The volume keeps broker data outside the container's writable layer.

## 3. Topic publisher and subscriber

The current topic-based flow is:

```text
Staffing Service
       |
       | STAFFING_SCHEDULE_UPDATED
       v
ActiveMQ Topic
staffing-events-topic
       |
       v
Ward Service
       |
       v
Latest staffing update per ward
```

- Publisher: `staffing-service`
- Subscriber: `ward-service`

Staffing Service publishes an event after a schedule is generated through
`POST /schedule/{wardId}`.

Ward Service subscribes to the topic using a durable subscription and updates
its staffing state when an accepted event is received.

## 4. Event contract

The topic message is JSON.

| Field | Type | Description |
|---|---|---|
| `eventId` | String | Unique event identifier |
| `eventType` | String | `STAFFING_SCHEDULE_UPDATED` |
| `schemaVersion` | Integer | Contract version, currently `1` |
| `wardId` | String | Ward affected by the schedule |
| `department` | String | Ward department |
| `alertLevel` | Integer | Emergency status between 0 and 8 |
| `doctorsOnCall` | Integer | Computed staffing requirement |
| `scheduleFor` | String | Schedule period |
| `generatedAt` | String | ISO-8601 timestamp |

The producer and consumer each maintain their own Java representation of the
event contract because the services are independently built Maven projects.

## 5. Persistent delivery

Staffing Service publishes with persistent JMS delivery.

Ward Service uses a durable topic subscription with a stable client ID and
subscription name.

This allows the broker to retain eligible messages for the durable subscriber
while it is disconnected, subject to broker storage and subscription
configuration.

The application state maintained by Ward Service is still in memory. Durable
message delivery does not make that application state a persistent database.

## 6. Start ActiveMQ

Run these commands from the HealthSafe repository root in PowerShell:

```powershell
docker compose -f .\common\docker-compose.yml up -d
```

Check the container:

```powershell
docker compose -f .\common\docker-compose.yml ps
```

Follow the broker logs:

```powershell
docker compose -f .\common\docker-compose.yml logs -f activemq
```

Open the local web console:

`http://localhost:8161`

Local development credentials:

- Username: `admin`
- Password: `admin`

Do not use these default local credentials for a publicly exposed broker.

## 7. Stop ActiveMQ

Stop the broker:

```powershell
docker compose -f .\common\docker-compose.yml down
```

The named volume is retained.

To remove the container and delete the stored volume data:

```powershell
docker compose -f .\common\docker-compose.yml down -v
```

The `-v` option deletes the persisted broker data. Use it only when that
data is no longer needed.

## 8. Equipment failure queue

The planned Iteration 4 destination is:

`equipment-failure-queue`

The queue name and broker URL are present in the Equipment Alert Service
configuration. The producer and consumer workflow is not implemented yet.

A queue is appropriate for this future workflow because each failure alert
should be processed as work by a consumer, rather than broadcast to every
subscriber as a topic event.

## 9. Dependencies

- Docker
- Docker Compose
- Apache ActiveMQ Classic 5.18.3

This directory does not require Maven.