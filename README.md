# Country Info Service

Spring Boot 3 / Java 21 microservice that accepts a country name over REST, enriches it through the
public **CountryInfoService SOAP API** (ISO code → full country info), persists it to **MySQL**, and exposes CRUD
endpoints. Ships with a Dockerfile, Kubernetes manifests, and deployment/troubleshooting guides.

* Deployment guide: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)
* Troubleshooting guide: [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)

## Flow

```
POST /api/v1/countries {"name":"kenya"}
   │  1. validate, convert to sentence case  → "Kenya"
   │  2. SOAP CountryISOCode(sCountryName)   → "KE"        (cached, retried, circuit-breaker)
   │  3. SOAP FullCountryInfo(sCountryISOCode)             (cached, retried, circuit-breaker)
   │  4. upsert CountryInfo + Language rows in MySQL (keyed by ISO code)
   ▼
201 Created (new) / 200 OK (refreshed, or served from DB if upstream is down)
```

## Architecture (MVC / layering)

| Layer | Class | Responsibility |
|---|---|---|
| Controller | `CountryController` | HTTP mapping, validation, status codes only |
| Service | `CountryService` | Orchestration, upsert, fallback, transactions |
| Client | `CountryInfoSoapClient` | SOAP envelopes, parsing, timeouts, retry, circuit breaker, cache |
| Repository / Model | `CountryInfoRepository`, `CountryInfo`, `Language` | Persistence (JPA) |
| Cross-cutting | `GlobalExceptionHandler`, `CorrelationIdFilter` | RFC 7807 errors, tracing id in logs |

## Design decisions and trade-offs

* **Stateless service**: no session or local state besides a cache, so replicas scale horizontally behind the Service/HPA (2–10 pods on 70% CPU).
* **Hand-built SOAP over `RestClient` instead of WSDL codegen**: only two operations are used, so this avoids generated code and plugins. Trade-off: no compile-time contract, so a parsing unit test guards the response shape. XML parsing disables DTDs (XXE protection).
* **Resilience (Resilience4j)**: 2s connect / 5s read timeouts; 3 attempts with exponential backoff (safe because both SOAP calls are read-only); circuit breaker opens at 50% failures over 10 calls and stays open for 30s. "Country not found" is a business result and neither retried nor counted as a failure.
* **Fallback**: if the upstream is down or the circuit is open and the country already exists in MySQL, the stored copy is returned with header `X-Data-Source: database-fallback`. Otherwise 502 (upstream error) or 503 + `Retry-After` (circuit open).
* **Caching (Caffeine, 24h)**: country reference data is near-static, so repeat lookups never hit the upstream. Trade-off: per-pod cache (not shared) and up to 24h staleness; Redis would be the next step for a shared cache.
* **No network calls inside DB transactions**: a slow upstream can't exhaust the connection pool.
* **Upsert by ISO code** (unique constraint): POST is idempotent, and a concurrent duplicate insert yields 409 instead of corrupt data.
* **Not done on purpose (called out as next steps)**: Flyway migrations (using `ddl-auto=update` here), Redis cache, a queue for async enrichment, distributed tracing (OpenTelemetry), authentication on the API, and MySQL replication/managed DB.

## Observability

* Structured JSON (ECS) logs to stdout in the `prod` profile; every line carries `correlationId` (taken from `X-Correlation-Id` or generated, and echoed back in the response header and in error bodies).
* Actuator: `/actuator/health/liveness`, `/actuator/health/readiness` (includes DB), `/actuator/metrics`, `/actuator/prometheus` (HTTP server metrics, JVM, Hikari, and Resilience4j retry/circuit-breaker metrics).
* Readiness deliberately excludes the SOAP provider so an upstream outage doesn't take every pod out of rotation.

## Error responses

All errors are `application/problem+json`: `400` validation/malformed JSON, `404` unknown country or id, `409` conflict, `502` upstream failure, `503` circuit open (`Retry-After: 30`), `500` unexpected (generic message plus `correlationId`, no stack trace).

## Run and test

**Prerequisites**: JDK 17, Maven 3.9+, Docker (for MySQL).

```bash
# 1. Unit tests
mvn test

# 2a. Everything in Docker
docker compose up --build

# 2b. or MySQL in Docker + app from Maven
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=countrydb mysql:8.4
mvn spring-boot:run
```

```bash
# Create / refresh (note lowercase input → "Kenya")
curl -i -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{"name":"kenya"}'

curl localhost:8080/api/v1/countries                 # list (?page=0&size=20)
curl localhost:8080/api/v1/countries/1               # by id
curl -X PUT localhost:8080/api/v1/countries/1 -H 'Content-Type: application/json' \
     -d '{"name":"Kenya","capitalCity":"Nairobi","phoneCode":"254","continentCode":"AF","currencyIsoCode":"KES"}'
curl -i -X DELETE localhost:8080/api/v1/countries/1  # 204

# Error cases
curl -i -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{"name":""}'        # 400
curl -i -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{"name":"Narnia"}'  # 404
curl localhost:8080/actuator/health
```

Quick resilience check: start with `SOAP_URL=http://localhost:9/x`. A repeat of a stored country returns 200 with
`X-Data-Source: database-fallback`, and an unknown one returns 502 and then 503 once the breaker opens.
