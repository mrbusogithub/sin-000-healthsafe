# Ingestion Service

**Port:** `7030`  
**Main class:** `co.wethinkcode.healthsafe.IngestionServiceApp`

## 1. Purpose

Ingestion Service loads the legacy ward CSV file, cleans inconsistent values,
validates records, resolves duplicate ward IDs and exposes the cleaned records
through a REST API.

It is the source of the ward catalogue used by Ward Service and Staffing Service.

## 2. Input dataset

The source file is:

`src/main/resources/wards-outdated.csv`

Expected CSV columns:

| Column | Description |
|---|---|
| `ward_id` | Ward identifier |
| `Wing` | Ward wing |
| `department` | Specialist department |
| `beds_available` | Number of available beds |

The loader requires all four columns. Header order can change because fields
are located by their names rather than fixed positions.

Date and boolean normalisation are not applicable to the current four-column
dataset.

## 3. Cleaning rules

The cleaning implementation is in:

`src/main/java/co/wethinkcode/healthsafe/cleaning/WardCleaningUtils.java`

It handles:

- Leading and trailing whitespace.
- Repeated internal whitespace.
- Ward ID casing, for example `w-05` becomes `W-05`.
- Wing and department name casing.
- Department aliases such as `Pediatrics` and `Paediatrics`.
- Recognised medical acronyms such as `ICU`.
- Placeholder values such as `N/A`, `TBD`, `unknown` and `-`.
- Invalid, negative or unrealistic bed counts.
- The special bed-count value `full`, interpreted as zero.

Invalid bed counts are represented as `null` and accompanied by a note.

## 4. Duplicate handling

Duplicate ward records are grouped using the normalised ward ID.

The record with the highest data-quality score is selected:

| Field | Score |
|---|---:|
| Valid bed count | 10 |
| Populated wing | 1 |
| Populated department | 1 |

Tied scores are resolved using a secondary ordering of normalised field values.
The decision therefore does not depend on CSV row order.

Distinct cleaning notes from duplicate records are retained in the merged record.

## 5. Failure handling

The service does not start if:

- The required CSV resource is missing.
- The CSV header is empty.
- A required header is missing.
- The CSV contains no usable ward records.

Individual malformed data rows are skipped and reported. One malformed row does
not automatically invalidate the rest of the dataset.

## 6. Project structure

```text
ingestion-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/co/wethinkcode/healthsafe/
    │   │   ├── IngestionServiceApp.java
    │   │   ├── cleaning/
    │   │   │   └── WardCleaningUtils.java
    │   │   ├── domain/
    │   │   │   └── WardRecord.java
    │   │   └── infrastructure/csv/
    │   │       └── WardCsvLoader.java
    │   └── resources/
    │       └── wards-outdated.csv
    └── test/java/co/wethinkcode/healthsafe/
        ├── cleaning/
        │   └── WardCleaningUtilsTest.java
        └── infrastructure/csv/
            └── WardCsvLoaderTest.java
```

## 7. REST API

### Health check

```http
GET /health
```

Example:

```powershell
Invoke-RestMethod http://localhost:7030/health
```

Expected result:

```text
OK
```

### Retrieve cleaned ward records

```http
GET /wards
```

Example:

```powershell
Invoke-RestMethod http://localhost:7030/wards |
    ConvertTo-Json -Depth 5
```

Each record exposes:

```json
{
  "wardId": "W-01",
  "wing": "East Wing",
  "department": "Cardiology",
  "bedsAvailable": 3,
  "notes": null
}
```

## 8. Build

Open PowerShell in the `ingestion-service` directory:

```powershell
mvn clean package
```

## 9. Run

```powershell
java -jar .\target\ingestion-service.jar
```

The service listens on:

`http://localhost:7030`

## 10. Test

Run the complete test suite:

```powershell
mvn clean test
```

Run only the cleaning tests:

```powershell
mvn -Dtest=WardCleaningUtilsTest test
```

Run only the CSV loader tests:

```powershell
mvn -Dtest=WardCsvLoaderTest test
```

The tests cover normalisation, invalid values, missing headers, malformed rows,
empty resources and duplicate resolution.

## 11. Dependencies

- Java 21
- Maven
- Javalin 5.6.3
- OpenCSV 5.9
- JUnit 5.10.2