package com.ofss.dto;
import com.ofss.entity.*; import java.time.OffsetDateTime;
public record ApprovalWorkflowResponse(Long workflowId,Long paymentId,ApprovalStatus status,ApprovalLevel requiredLevel,Long decidedBy,String remark,OffsetDateTime dueAt){}
