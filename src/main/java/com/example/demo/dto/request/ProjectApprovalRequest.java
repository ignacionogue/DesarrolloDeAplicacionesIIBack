package com.example.demo.dto.request;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ProjectApprovalRequest(@NotNull @DecimalMin(value="0.0", inclusive=false) BigDecimal approvedBudget,
                                     @NotNull @Positive Integer approvedDeadlineDays,
                                     @NotNull LocalDate approvedAt,
                                     String observations) {}
