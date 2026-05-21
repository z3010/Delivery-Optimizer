# DELIVERY SLOT OPTIMIZER
https://dso-dashboard.onrender.com 
Real-time backend engine that dynamically manages delivery slot availability across multiple delivery zones.
# Key Design Decisions
- SSE instead of WebSockets (simpler, HTTP-native, auto-reconnect)
- Pessimistic DB lock on slot row during booking (prevents double-booking)
- Redis atomic `INCR`/`DECR` for sub-millisecond counter updates
- Greedy bin-packing: fills existing windows before opening new ones (reduces deadhead vehicle trips)
# Orders

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/orders` | Create an order and allocate a slot |
| `GET` | `/orders/{id}` | Fetch order by ID |
| `GET` | `/orders/zone/{zoneId}?page=0&size=20` | Orders for a zone (paginated) |
| `POST` | `/orders/simulate/bulk` | Simulate N synthetic orders |



# Slots

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/slots/zone/{zoneId}?date=2025-06-01` | Available slots for a zone on a date |
| `GET` | `/slots/{slotId}` | Single slot state |
| `GET` | `/slots/zone/{zoneId}/all` | Full slot grid for a zone |

# Zones

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/zones` | All zones with current load snapshot |
| `GET` | `/zones/{zoneId}/load` | Real-time load for one zone |

# SSE stream

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/stream/zones` | Open SSE connection for live updates |

**SSE events emitted:**
- `zone-update` — single zone payload, fired immediately after each order placement
- `zone-snapshot` — all zones array, fired every 15 seconds as a heartbeat

---

# Redis key schema

```
slot:available:{slotId}             ← remaining capacity (integer)
slot:booked:{slotId}                ← booked count (integer)
zone:load:{zoneId}                  ← active order count (integer)
zone:available_slots:{zoneId}:{date} ← available slot count for today
```

TTLs: slot keys expire after 24h, zone-load keys after 10 min.  
The scheduler re-syncs from Postgres every 15s to prevent drift.

---

# Slot allocation algorithm

`GreedySlotAllocator` applies three hard constraints then a soft objective:

1. **Capacity** — `bookedCount < capacity`
2. **Overload** — `fillRate < 0.85` (configurable)
3. **Fleet** — `bookedCount < activeVehicles × (windowMin / avgDeliveryMin)`

Among passing slots, it picks the one with the **highest current fill-rate** (bin-packing: pack slots before opening new windows). If all slots exceed the threshold it falls back to the least-loaded slot.

---

# Seed data

On startup, `DataSeeder` creates 6 synthetic zones across Bangalore, Mumbai, and Delhi, each with:
- A `FleetCapacity` row (vehicles, avg delivery time)
- 24 half-hour delivery windows for today (08:00–20:00)
- Redis counters seeded from DB state

The seeder is idempotent - safe to restart without duplicating data.

  
