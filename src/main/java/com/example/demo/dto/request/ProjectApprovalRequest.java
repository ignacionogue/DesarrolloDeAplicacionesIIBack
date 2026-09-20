package com.example.demo.dto.request;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ProjectApprovalRequest(@NotNull @DecimalMin(value="0.0", inclusive=false) @Digits(integer=13, fraction=2) BigDecimal approvedBudget,
                                     @NotNull @Positive Integer approvedDeadlineDays,
                                     @NotNull LocalDate approvedAt,
                                     @Size(max=1000) String observations) {}
