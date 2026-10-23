package com.ofss.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="APPROVAL_ESCALATIONS")
public class ApprovalEscalation {
    @Id @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="escalation_seq")
    @SequenceGenerator(name="escalation_seq", sequenceName="APPROVAL_ESCALATIONS_SEQ", allocationSize=1) public Long id;
    public Long taskId;
    public Long paymentId;
    public Long previousApproverId;
    public Long assignedApproverId;
    @Enumerated(EnumType.STRING) public ApprovalLevel previousLevel;
    @Enumerated(EnumType.STRING) public ApprovalLevel requiredLevel;
    public String action;
    @Column(length=2000) public String remark;
    public OffsetDateTime occurredAt;
}
