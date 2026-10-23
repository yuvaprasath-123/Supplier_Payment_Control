package com.ofss.controller;
import com.ofss.dto.*;
import com.ofss.service.ApprovalWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/approval-workflows")
public class ApprovalWorkflowController {
    private final ApprovalWorkflowService service;
    public ApprovalWorkflowController(ApprovalWorkflowService service) { this.service=service; }
    @PostMapping public ResponseEntity<ApprovalWorkflowResponse> create(@Valid @RequestBody CreateApprovalWorkflowRequest r,@RequestHeader(value="Authorization",required=false) String token) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r,token)); }
    @GetMapping("/{workflowId}") public ApprovalWorkflowResponse get(@PathVariable Long workflowId) { return service.get(workflowId); }
    @PostMapping("/{workflowId}/approve") public ApprovalWorkflowResponse approve(@PathVariable Long workflowId,@Valid @RequestBody DecisionRequest r,@RequestHeader(value="Authorization",required=false) String token) { return service.approve(workflowId,r,token); }
    @PostMapping("/{workflowId}/reject") public ApprovalWorkflowResponse reject(@PathVariable Long workflowId,@Valid @RequestBody DecisionRequest r,@RequestHeader(value="Authorization",required=false) String token) { return service.reject(workflowId,r,token); }
    @PostMapping("/{workflowId}/escalate") public ApprovalWorkflowResponse escalate(@PathVariable Long workflowId,@Valid @RequestBody EscalateWorkflowRequest r,@RequestHeader(value="Authorization",required=false) String token) { return service.escalate(workflowId,r,token); }
}
