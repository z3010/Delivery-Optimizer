package com.deliveryoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeliverySlotOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliverySlotOptimizerApplication.class, args);
    }
}
