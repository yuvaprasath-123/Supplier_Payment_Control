package com.ofss.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="APPROVAL_TASKS", uniqueConstraints=@UniqueConstraint(columnNames="PAYMENT_ID"))
public class ApprovalTask {
    @Id @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="task_seq")
    @SequenceGenerator(name="task_seq", sequenceName="APPROVAL_TASKS_SEQ", allocationSize=1) public Long id;
    @Column(name="PAYMENT_ID", nullable=false) public Long paymentId;
    @Column(nullable=false) public Long routeId;
    @Column(nullable=false) public Long requesterId;
    @Column(nullable=false) public Long assignedApproverId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public ApprovalLevel requiredLevel;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public ApprovalStatus status;
    @Column(nullable=false) public int slaMinutes;
    @Column(nullable=false) public OffsetDateTime dueAt;
    @Column(nullable=false) public OffsetDateTime createdAt;
    public Long decidedBy;
    @Column(length=2000) public String remark;
    @Version public Long version;
}
