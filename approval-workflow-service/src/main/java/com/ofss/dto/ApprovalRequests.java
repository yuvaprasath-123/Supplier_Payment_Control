package com.ofss.dto;
import com.ofss.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class ApprovalRequests {
    private ApprovalRequests() {}
    public record Route(@NotNull @Positive Long paymentId,@NotNull @Positive Long requesterId,
            @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal paymentAmount,
            @NotNull RiskBand riskBand,@NotNull ApprovalLevel amountApprovalLevel,@Size(max=100) String configurationVersion) {}
    public record Task(@NotNull @Positive Long paymentId,@NotNull @Positive Long assignedApproverId,@Min(1) @Max(525600) Integer slaMinutes) {}
    public record Decision(@NotNull @Positive Long approverId,@Size(max=2000) String remark) {}
    public record Reassign(@Positive Long backupApproverId,@NotBlank @Size(max=2000) String remark) {}
    public record Escalate(@Positive Long assignedApproverId,@NotBlank @Size(max=2000) String remark) {}
}
