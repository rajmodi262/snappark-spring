# SnapPark: parking-bay booking service on Spring Boot

A driver scans a QR code at a parking bay, holds it for five minutes, checks in, and pays by the
half hour at a surge-adjusted rate when they leave. The service guarantees that **two drivers can
never get the same bay**, even when dozens of them try at the same moment.

This is a rewrite of the booking engine from [SnapPark](https://github.com/rajmodi262/SnapPark-Smart-Parking),
my earlier plain-Java desktop kiosk, as a stateless Spring Boot service. The rewrite also fixes a
concurrency bug the original had, described below.

**Stack:** Java 21 · Spring Boot 4 (Web MVC, Data JPA, Security, Validation, Actuator) · PostgreSQL 16 ·
Flyway · Micrometer/Prometheus · JUnit 5 + Testcontainers · JaCoCo · SpotBugs + FindSecBugs · Docker ·
Jenkins · GitHub Actions

## Architecture

```
            ┌──────────────────────────── snappark (stateless, N instances) ───────────────────────────┐
 driver ──► │ SlotController ─► SlotService ─┐                                                          │
  (QR)      │ SessionController ─► SessionService ─► PricingService   (surge bands, BigDecimal money)   │
 operator ─►│ AdminController (HTTP Basic, ROLE_ADMIN)│                                                │
            │                                  SlotRepository / SessionRepository (Spring Data JPA)     │
            └───────────────────────────────────────────┼──────────────────────────────────────────────┘
                                                        ▼
                                PostgreSQL: conditional UPDATEs decide every race
```

Layered `controller -> service -> repository`, one package per feature (`slot`, `session`, `pricing`,
`admin`). The schema belongs to Flyway, and Hibernate only validates it (`ddl-auto=validate`).

## The bug in the original, and the fix

The original kept holds in an in-memory `ConcurrentHashMap`. Taking over an expired hold looked like this:

```java
SlotLock existing = locks.putIfAbsent(slotId, newLock);
if (existing != null && existing.isExpired()) {
    locks.put(slotId, newLock);   // check-then-act: every waiting thread can reach this line
    return true;
}
```

When a hold expires, every waiting driver sees it as expired at once, and each one calls `put` and is
told it won. Keeping the locks in memory also means two server instances would each have their own
lock table.

Here, each state change is one conditional `UPDATE`. PostgreSQL's row lock picks the winner, and the
`WHERE` clause is checked again after any wait:

```sql
update parking_slot set status = 'HELD', held_by = ?, hold_expires_at = ?
 where id = ? and (status = 'AVAILABLE' or (status = 'HELD' and hold_expires_at < now))
```

A result of 0 rows means you lost the race, and the API answers `409 Conflict`. No lock state lives in
the JVM, so the service scales horizontally.

**How I know it works.** `BookingConcurrencyTest` starts 32 threads on real PostgreSQL, releases them
at the same instant, and requires exactly one winner, repeated 5 times per scenario. As a check on the
test itself, I temporarily put the original check-then-act logic back: **10 of the 32 drivers won the
same bay, in every run**, and the test failed. With the conditional `UPDATE`, it passes every time.

Checkout is guarded the same way, so a double-tapped "pay" button bills the driver only once.

## API

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/api/v1/slots?type=&status=` | anyone | List bays |
| POST | `/api/v1/slots/{id}/hold` | driver | Hold a bay for 5 min (`409` if taken) |
| DELETE | `/api/v1/slots/{id}/hold` | driver | Release your own hold |
| POST | `/api/v1/sessions` | driver | Check in on a bay you hold; locks in the rate |
| GET | `/api/v1/sessions/{id}` | driver | Session details |
| POST | `/api/v1/sessions/{id}/checkout` | driver | Pay and leave; returns a receipt |
| GET | `/api/v1/pricing/quote?type=` | anyone | The rate you would get right now |
| POST | `/api/v1/admin/slots` | operator | Add a bay |
| GET | `/api/v1/admin/stats` | operator | Occupancy and revenue |
| GET | `/actuator/health/{liveness,readiness}` | anyone | Orchestrator probes |
| GET | `/actuator/prometheus` | operator | Metrics (`snappark_holds_total{outcome}`, check-ins, check-outs, HTTP latency) |

Errors are RFC 9457 problem documents, with one message per invalid field.

**Pricing** (ported from the original): base rate per hour (bike 20, car 50, SUV 80) × a time-of-day
band (late night 0.85, early bird 1.00, morning peak 1.20, standard 1.00, rush hour 1.30, night 0.90,
in `Asia/Kolkata` time) × an occupancy band (below 50% 1.00, 50% 1.05, 75% 1.15, 90% 1.25). Stays are
billed in half-hour steps with a one-hour minimum, and stays of 5 minutes or less are free. The rate is
fixed at check-in, so a driver who arrives just before rush hour does not pay the rush-hour rate.
Money is `BigDecimal`, rounded half-up to the paisa (the original used `double`).

## Security

- **Least privilege by default:** drivers are anonymous, and the operator API and metrics need HTTP
  Basic with `ROLE_ADMIN`. Any path not explicitly allowed is denied.
- **No default password:** the admin account exists only when a BCrypt hash is supplied in
  `ADMIN_PASSWORD_HASH`. Without one, the admin API is locked.
- **Input validation:** Bean Validation on every request body, with an allow-list pattern for IDs and
  number plates. All queries are parameterised through JPA.
- **No leaked internals:** unexpected exceptions are logged on the server, and the client gets a
  generic 500 with no stack trace or SQL.
- **Security headers:** `Content-Security-Policy: default-src 'none'`, `X-Frame-Options: DENY`,
  `nosniff`.
- CSRF protection is off on purpose. The API is stateless and never issues a session cookie, so CSRF
  has nothing to exploit.
- **Scanning:** SpotBugs with the FindSecBugs rules fails the build on any Medium+ finding. Dependabot
  watches Maven, Docker and Actions dependencies. The container runs as a non-root user.

**Known limit:** the driver ID comes from the client, which matches the original's anonymous QR flow.
A production version would take it from a signed token.

## Quality gates

- `./mvnw verify` runs 66 tests: unit tests on pricing boundaries, plus HTTP and concurrency
  integration tests against **PostgreSQL 16 in Testcontainers**. The build fails if line coverage drops
  below 80% (currently about 95%).
- `./mvnw spotbugs:check` runs the static security analysis.

## Run it

```bash
docker compose up --build            # PostgreSQL + the service on :8080 (needs Docker)
./mvnw verify                        # tests + coverage gate (needs Docker for Testcontainers)
```

To enable the operator API, generate a BCrypt hash (for example with `htpasswd -bnBC 10 "" 'your-password' | tr -d ':\n'`)
and export it as `ADMIN_PASSWORD_HASH` before running `docker compose up`.

The image uses a multi-stage build and a JRE-only runtime. It runs as a non-root user, keeps
dependencies in a separate cacheable layer, and has a container health check on the readiness probe.
In the `prod` profile (the image default), logs are one JSON object per line in Elastic Common Schema,
ready for Splunk, ELK or CloudWatch.

## CI/CD

- **Jenkins:** the [`Jenkinsfile`](Jenkinsfile) stages are Build & Test (JUnit results and the JaCoCo
  report are archived), Security Scan, Docker Image, and Push to Amazon ECR (runs only when
  `ECR_REGISTRY` is set). [`ci/jenkins/`](ci/jenkins) starts a local Jenkins with configuration as code
  that already has this pipeline set up:
  `JENKINS_ADMIN_PASSWORD=... docker compose -f ci/jenkins/docker-compose.yml up -d --build`, then
  open http://localhost:8090.
- **GitHub Actions:** [`ci.yml`](.github/workflows/ci.yml) runs the same checks on every push and pull
  request.
- **AWS:** see [`deploy/aws/README.md`](deploy/aws/README.md).
