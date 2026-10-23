package com.ofss.entity;
import jakarta.persistence.*;
/** Optional administrator-maintained mapping; reassignment can supply a backup explicitly. */
@Entity @Table(name="BACKUP_APPROVER_MAPPINGS", uniqueConstraints=@UniqueConstraint(columnNames={"APPROVER_ID","APPROVAL_LEVEL"}))
public class BackupApproverMapping {
    @Id @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="backup_seq")
    @SequenceGenerator(name="backup_seq", sequenceName="BACKUP_APPROVER_MAPPINGS_SEQ", allocationSize=1) public Long id;
    @Column(name="APPROVER_ID", nullable=false) public Long approverId;
    @Column(nullable=false) public Long backupApproverId;
    @Enumerated(EnumType.STRING) @Column(name="APPROVAL_LEVEL", nullable=false) public ApprovalLevel approvalLevel;
}
