package com.ofss.dto;
import jakarta.validation.constraints.NotNull;
public record EscalateWorkflowRequest(@NotNull Long backupApproverId,String remark){}
