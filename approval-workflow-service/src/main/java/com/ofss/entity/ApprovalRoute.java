package com.ofss.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
@Entity @Table(name="APPROVAL_ROUTES", uniqueConstraints=@UniqueConstraint(columnNames="PAYMENT_ID"))
public class ApprovalRoute {
    @Id @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="route_seq")
    @SequenceGenerator(name="route_seq", sequenceName="APPROVAL_ROUTES_SEQ", allocationSize=1) public Long id;
    @Column(name="PAYMENT_ID", nullable=false) public Long paymentId;
    @Column(nullable=false) public Long requesterId;
    @Column(nullable=false, precision=19, scale=2) public BigDecimal paymentAmount;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public RiskBand riskBand;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public ApprovalLevel amountApprovalLevel;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public ApprovalLevel riskApprovalLevel;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public ApprovalLevel requiredLevel;
    public String configurationVersion;
    @Column(nullable=false, length=1000) public String explanation;
    @Column(nullable=false) public OffsetDateTime createdAt;
}
