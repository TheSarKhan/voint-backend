package com.starsoft.voint.billing.dto;

import com.starsoft.voint.billing.InvoiceStatus;
import jakarta.validation.constraints.NotNull;
public record InvoiceStatusRequest(@NotNull InvoiceStatus status) { }
