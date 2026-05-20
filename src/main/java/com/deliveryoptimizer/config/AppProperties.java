package com.deliveryoptimizer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {

    private Slot slot = new Slot();
    private Sse sse = new Sse();
    private Seed seed = new Seed();

    @Getter @Setter
    public static class Slot {
        private int defaultCapacity = 20;
        private int windowDurationMinutes = 30;
        private int windowsPerDay = 24;
        private double overloadThreshold = 0.85;
    }

    @Getter @Setter
    public static class Sse {
        private long heartbeatIntervalMs = 15000;
    }

    @Getter @Setter
    public static class Seed {
        private int zones = 6;
        private boolean runOnStartup = true;
    }
}
