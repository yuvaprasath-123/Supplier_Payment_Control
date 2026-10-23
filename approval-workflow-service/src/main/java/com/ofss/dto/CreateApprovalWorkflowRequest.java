package com.ofss.dto;
import com.ofss.entity.*; import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record CreateApprovalWorkflowRequest(@NotNull @Positive Long paymentId,@NotNull @Positive Long requesterId,
    @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal paymentAmount,@NotNull RiskBand riskBand,
    @NotNull ApprovalLevel amountApprovalLevel,@NotNull @Positive Long assignedApproverId,@Min(1) @Max(525600) Integer slaMinutes){}
