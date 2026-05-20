package com.deliveryoptimizer.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateOrderRequest(

    @NotBlank @Size(max = 64)
    String externalRef,

    @NotNull
    Long zoneId,

    LocalDate preferredDate,    // defaults to today if null

    @NotBlank @Size(max = 150)
    String customerName,

    @Size(max = 20)
    String customerPhone,

    @NotBlank
    String deliveryAddress
) {}
