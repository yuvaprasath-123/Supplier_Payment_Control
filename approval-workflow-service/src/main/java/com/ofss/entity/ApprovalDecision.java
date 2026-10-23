package com.ofss.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="APPROVAL_DECISIONS")
public class ApprovalDecision {
    @Id @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="decision_seq")
    @SequenceGenerator(name="decision_seq", sequenceName="APPROVAL_DECISIONS_SEQ", allocationSize=1) public Long id;
    @Column(nullable=false) public Long taskId;
    @Column(nullable=false) public Long paymentId;
    @Column(nullable=false) public Long approverId;
    @Column(nullable=false) public String decision;
    @Column(length=2000) public String remark;
    @Column(nullable=false) public OffsetDateTime decidedAt;
}
