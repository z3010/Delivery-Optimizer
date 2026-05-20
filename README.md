# Hyperlocal Delivery Slot Optimizer

Real-time backend engine for a quick-commerce platform that dynamically manages delivery slot availability across multiple delivery zones. Built with Spring Boot, PostgreSQL, Redis, and SSE.

---

## Architecture at a glance

```
Clients (REST / SSE dashboard)
        │
        ▼
  Spring Boot monolith (port 8080)
  ├── Controller layer        ← thin HTTP handlers
  ├── Service layer           ← business logic
  ├── GreedySlotAllocator     ← constraint-based bin-packing
  ├── RedisSlotCache          ← fast atomic counters
  └── SseBroadcaster          ← live push to dashboard
        │                  │
        ▼                  ▼
   PostgreSQL           Redis
  (source of truth)   (fast cache)
```

**Key design decisions:**
- Monolithic, modular — no microservices, no Kafka
- SSE instead of WebSockets (simpler, HTTP-native, auto-reconnect)
- Pessimistic DB lock on slot row during booking (prevents double-booking)
- Redis atomic `INCR`/`DECR` for sub-millisecond counter updates
- Greedy bin-packing: fills existing windows before opening new ones (reduces deadhead vehicle trips)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Docker | 24+ |
| Docker Compose | v2.x |
| Java | 21 (only needed for local dev without Docker) |
| Maven | 3.9+ (only for local dev) |

---

## Quick start (Docker — recommended)

```bash
# 1. Clone and enter the project
git clone <repo-url>
cd delivery-slot-optimizer

# 2. Copy env config (defaults work out of the box)
cp .env.example .env

# 3. Start the full stack
docker compose up --build

# 4. Wait ~30s for Spring Boot to finish seeding, then open:
#    Dashboard  →  http://localhost:3000
#    API docs   →  http://localhost:8080/api/actuator/health
```

To run in the background:
```bash
docker compose up --build -d
docker compose logs -f app   # tail logs
```

To stop and clean up:
```bash
docker compose down -v       # -v removes volumes (resets DB + Redis)
```

---

## Local development (without Docker)

You still need Postgres and Redis running locally:

```bash
# Start dependencies only
docker compose up postgres redis -d

# Run the Spring Boot app
./mvnw spring-boot:run

# Dashboard: open dashboard/index.html in a browser
# (configure your browser to allow file:// XHR, or serve via any static server)
npx serve dashboard -p 3000
```

Environment variables for local run (or set in `application.yml`):
```
DB_HOST=localhost  DB_PORT=5432  DB_NAME=delivery_optimizer
DB_USER=postgres   DB_PASS=postgres
REDIS_HOST=localhost  REDIS_PORT=6379
```

---

## API Reference

Base URL: `http://localhost:8080/api`  
No authentication required.

### Orders

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/orders` | Create an order and allocate a slot |
| `GET` | `/orders/{id}` | Fetch order by ID |
| `GET` | `/orders/zone/{zoneId}?page=0&size=20` | Orders for a zone (paginated) |
| `POST` | `/orders/simulate/bulk` | Simulate N synthetic orders |

**POST /orders — request body:**
```json
{
  "externalRef": "ORD-001",
  "zoneId": 1,
  "preferredDate": "2025-06-01",
  "customerName": "Priya Iyer",
  "customerPhone": "9876543210",
  "deliveryAddress": "42 MG Road, Bangalore"
}
```

**POST /orders/simulate/bulk — request body:**
```json
{
  "orderCount": 50,
  "zoneId": null,
  "randomZones": true,
  "delayMs": 0
}
```

### Slots

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/slots/zone/{zoneId}?date=2025-06-01` | Available slots for a zone on a date |
| `GET` | `/slots/{slotId}` | Single slot state |
| `GET` | `/slots/zone/{zoneId}/all` | Full slot grid for a zone |

### Zones

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/zones` | All zones with current load snapshot |
| `GET` | `/zones/{zoneId}/load` | Real-time load for one zone |

### SSE stream

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/stream/zones` | Open SSE connection for live updates |

**SSE events emitted:**
- `zone-update` — single zone payload, fired immediately after each order placement
- `zone-snapshot` — all zones array, fired every 15 seconds as a heartbeat

---

## Redis key schema

```
slot:available:{slotId}             ← remaining capacity (integer)
slot:booked:{slotId}                ← booked count (integer)
zone:load:{zoneId}                  ← active order count (integer)
zone:available_slots:{zoneId}:{date} ← available slot count for today
```

TTLs: slot keys expire after 24h, zone-load keys after 10 min.  
The scheduler re-syncs from Postgres every 15s to prevent drift.

---

## Slot allocation algorithm

`GreedySlotAllocator` applies three hard constraints then a soft objective:

1. **Capacity** — `bookedCount < capacity`
2. **Overload** — `fillRate < 0.85` (configurable)
3. **Fleet** — `bookedCount < activeVehicles × (windowMin / avgDeliveryMin)`

Among passing slots, it picks the one with the **highest current fill-rate** (bin-packing: pack slots before opening new windows). If all slots exceed the threshold it falls back to the least-loaded slot.

---

## Seed data

On startup, `DataSeeder` creates 6 synthetic zones across Bangalore, Mumbai, and Delhi, each with:
- A `FleetCapacity` row (vehicles, avg delivery time)
- 24 half-hour delivery windows for today (08:00–20:00)
- Redis counters seeded from DB state

The seeder is idempotent — safe to restart without duplicating data.

---

## Configuration

All values are in `src/main/resources/application.yml` and overrideable via environment variables.

| Key | Default | Description |
|-----|---------|-------------|
| `app.slot.default-capacity` | 20 | Slots per window per zone |
| `app.slot.window-duration-minutes` | 30 | Length of each slot window |
| `app.slot.windows-per-day` | 24 | Number of windows seeded per day |
| `app.slot.overload-threshold` | 0.85 | Fill-rate above which a slot is "busy" |
| `app.sse.heartbeat-interval-ms` | 15000 | SSE heartbeat frequency |
| `app.seed.zones` | 6 | Number of zones to seed |
| `app.seed.run-on-startup` | true | Enable/disable seeder |

---

## Project structure

```
delivery-slot-optimizer/
├── Dockerfile
├── docker-compose.yml
├── docker/
│   └── nginx.conf              ← SSE-aware reverse proxy config
├── dashboard/
│   └── index.html              ← live dashboard (no build step)
├── .env.example
└── src/main/java/com/deliveryoptimizer/
    ├── controller/             ← REST + SSE endpoints
    ├── service/                ← business logic
    ├── algorithm/              ← pluggable SlotAllocator interface
    ├── cache/                  ← RedisSlotCache
    ├── sse/                    ← SseEmitterRegistry + SseBroadcaster
    ├── repository/             ← JPA repos
    ├── model/
    │   ├── entity/             ← JPA entities
    │   ├── dto/                ← request/response records
    │   └── enums/
    ├── config/                 ← Redis, Web, AppProperties
    ├── seed/                   ← DataSeeder (startup)
    └── exception/              ← GlobalExceptionHandler
```

---

## Running tests

```bash
./mvnw test
```

Integration tests require a running Postgres and Redis (use `docker compose up postgres redis -d` first).

---

## Common issues

**App fails to connect to Postgres on first boot**  
The `depends_on: condition: service_healthy` in docker-compose waits for Postgres to be ready. If it still fails, increase `start_period` in the app healthcheck.

**SSE events not reaching the dashboard**  
Nginx is configured with `proxy_buffering off` for `/api/stream/*`. If you're behind another proxy, ensure it also disables buffering for SSE routes.

**Redis keys missing after restart**  
Redis is configured with `save 60 1` (persistence enabled). If you see cache misses on startup, the `DataSeeder` re-seeds Redis from Postgres automatically.

**Slots not appearing for today**  
The seeder only seeds today's date. If the container started just before midnight, restart it after midnight or call `POST /orders/simulate/bulk` to trigger slot creation via the allocation path.
