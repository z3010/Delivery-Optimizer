package com.deliveryoptimizer.seed;

import com.deliveryoptimizer.cache.RedisSlotCache;
import com.deliveryoptimizer.config.AppProperties;
import com.deliveryoptimizer.model.entity.DeliverySlot;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.model.entity.FleetCapacity;
import com.deliveryoptimizer.model.enums.ZoneStatus;
import com.deliveryoptimizer.repository.DeliverySlotRepository;
import com.deliveryoptimizer.repository.DeliveryZoneRepository;
import com.deliveryoptimizer.repository.FleetCapacityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final DeliveryZoneRepository zoneRepository;
    private final FleetCapacityRepository fleetRepository;
    private final DeliverySlotRepository slotRepository;
    private final RedisSlotCache redisCache;
    private final AppProperties props;

    // Synthetic zones modelled on Indian metro areas
    private static final List<ZoneSeed> ZONES = List.of(
        new ZoneSeed("Zone Alpha",  "Bangalore",  12.9716, 77.5946, 25, 8,  22, 20),
        new ZoneSeed("Zone Beta",   "Bangalore",  12.9352, 77.6245, 20, 6,  28, 18),
        new ZoneSeed("Zone Gamma",  "Mumbai",     19.0760, 72.8777, 30, 10, 20, 22),
        new ZoneSeed("Zone Delta",  "Mumbai",     19.1136, 72.8697, 15, 5,  35, 15),
        new ZoneSeed("Zone Epsilon","Delhi",      28.7041, 77.1025, 25, 8,  25, 20),
        new ZoneSeed("Zone Zeta",   "Delhi",      28.6139, 77.2090, 20, 7,  30, 18)
    );

    record ZoneSeed(String name, String city, double lat, double lng,
                    int totalVehicles, int activeVehicles,
                    int avgDeliveryMin, int maxOrdersPerSlot) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isRunOnStartup()) return;
        log.info("Running data seeder...");
        seedZonesAndFleet();
        seedTodaySlots();
        seedRedis();
        log.info("Data seeder complete — {} zones, {} slots seeded",
            zoneRepository.count(), slotRepository.count());
    }

    private void seedZonesAndFleet() {
        for (ZoneSeed zs : ZONES) {
            if (zoneRepository.existsByNameAndCity(zs.name(), zs.city())) continue;

            DeliveryZone zone = DeliveryZone.builder()
                .name(zs.name())
                .city(zs.city())
                .latitude(BigDecimal.valueOf(zs.lat()))
                .longitude(BigDecimal.valueOf(zs.lng()))
                .status(ZoneStatus.ACTIVE)
                .maxOrdersPerSlot(zs.maxOrdersPerSlot())
                .build();
            zone = zoneRepository.save(zone);

            FleetCapacity fleet = FleetCapacity.builder()
                .zone(zone)
                .totalVehicles(zs.totalVehicles())
                .activeVehicles(zs.activeVehicles())
                .avgDeliveryMinutes(zs.avgDeliveryMin())
                .build();
            fleetRepository.save(fleet);
        }
    }

    private void seedTodaySlots() {
        LocalDate today = LocalDate.now();
        List<DeliveryZone> zones = zoneRepository.findAll();
        int windowMin = props.getSlot().getWindowDurationMinutes();
        int windows   = props.getSlot().getWindowsPerDay();

        // Slot windows start at 08:00
        LocalTime startTime = LocalTime.of(8, 0);

        for (DeliveryZone zone : zones) {
            for (int w = 0; w < windows; w++) {
                LocalTime wStart = startTime.plusMinutes((long) w * windowMin);
                LocalTime wEnd   = wStart.plusMinutes(windowMin);

                if (slotRepository.existsByZoneIdAndSlotDateAndWindowStart(
                        zone.getId(), today, wStart)) {
                    continue; // idempotent
                }

                DeliverySlot slot = DeliverySlot.builder()
                    .zone(zone)
                    .slotDate(today)
                    .windowStart(wStart)
                    .windowEnd(wEnd)
                    .capacity(zone.getMaxOrdersPerSlot())
                    .bookedCount(0)
                    .isAvailable(true)
                    .build();
                slotRepository.save(slot);
            }
        }
    }

    private void seedRedis() {
        // Warm up Redis with current DB state
        slotRepository.findBySlotDate(LocalDate.now()).forEach(slot ->
            redisCache.initSlot(slot.getId(), slot.getCapacity(), slot.getBookedCount())
        );
        zoneRepository.findAll().forEach(zone ->
            redisCache.setZoneLoad(zone.getId(), 0L)
        );
    }
}
