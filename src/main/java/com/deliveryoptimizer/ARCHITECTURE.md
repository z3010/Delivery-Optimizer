# Package Structure

com.deliveryoptimizer
├── DeliverySlotOptimizerApplication.java   ← entry point
│
├── controller/        ← REST + SSE endpoints (thin, no business logic)
│   ├── OrderController.java
│   ├── SlotController.java
│   ├── ZoneController.java
│   └── SseController.java
│
├── service/           ← business logic, orchestrates algo + cache + db
│   ├── OrderService.java
│   ├── SlotAllocationService.java
│   ├── ZoneLoadService.java
│   └── SimulatorService.java
│
├── algorithm/         ← pluggable slot-allocation strategy
│   ├── SlotAllocator.java          (interface)
│   └── GreedySlotAllocator.java    (greedy constraint impl)
│
├── cache/             ← Redis read/write abstraction
│   └── RedisSlotCache.java
│
├── sse/               ← SSE emitter registry + broadcaster
│   ├── SseEmitterRegistry.java
│   └── SseBroadcaster.java
│
├── repository/        ← Spring Data JPA repos
│   ├── DeliveryZoneRepository.java
│   ├── DeliverySlotRepository.java
│   ├── OrderRepository.java
│   └── FleetCapacityRepository.java
│
├── model/
│   ├── entity/        ← JPA entities (mapped to PostgreSQL tables)
│   │   ├── DeliveryZone.java
│   │   ├── DeliverySlot.java
│   │   ├── Order.java
│   │   └── FleetCapacity.java
│   ├── dto/           ← request/response payloads (no entity leakage)
│   │   ├── CreateOrderRequest.java
│   │   ├── OrderResponse.java
│   │   ├── SlotAvailabilityResponse.java
│   │   ├── ZoneLoadResponse.java
│   │   └── BulkSimulateRequest.java
│   └── enums/
│       ├── OrderStatus.java
│       └── ZoneStatus.java
│
├── config/            ← Spring beans configuration
│   ├── RedisConfig.java
│   ├── WebConfig.java             (CORS for dashboard)
│   └── AppProperties.java        (typed config from application.yml)
│
├── seed/              ← synthetic data generator (runs on startup)
│   └── DataSeeder.java
│
└── exception/         ← global error handling
    ├── GlobalExceptionHandler.java
    ├── SlotUnavailableException.java
    └── ZoneNotFoundException.java
