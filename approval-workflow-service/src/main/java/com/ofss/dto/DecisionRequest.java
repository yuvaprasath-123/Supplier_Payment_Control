package com.ofss.dto;
import jakarta.validation.constraints.NotNull;
public record DecisionRequest(@NotNull Long approverId,String remark){}
