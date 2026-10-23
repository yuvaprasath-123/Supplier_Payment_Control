package com.ofss.service;
import com.ofss.dto.*;
import com.ofss.entity.ApprovalTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Compatibility adapter. New callers should use routes and tasks. */
@Service
public class ApprovalWorkflowService {
    private final ApprovalService service;
    public ApprovalWorkflowService(ApprovalService service) { this.service=service; }
    @Transactional public ApprovalWorkflowResponse create(CreateApprovalWorkflowRequest r,String token) {
        service.route(new ApprovalRequests.Route(r.paymentId(),r.requesterId(),r.paymentAmount(),r.riskBand(),r.amountApprovalLevel(),null));
        return response(service.create(new ApprovalRequests.Task(r.paymentId(),r.assignedApproverId(),r.slaMinutes()),token));
    }
    public ApprovalWorkflowResponse get(Long id) { return response(service.get(id)); }
    public ApprovalWorkflowResponse approve(Long id,DecisionRequest r,String token) { return response(service.decide(id,new ApprovalRequests.Decision(r.approverId(),r.remark()),token,false,false)); }
    public ApprovalWorkflowResponse reject(Long id,DecisionRequest r,String token) { return response(service.decide(id,new ApprovalRequests.Decision(r.approverId(),r.remark()),token,true,false)); }
    public ApprovalWorkflowResponse escalate(Long id,EscalateWorkflowRequest r,String token) { return response(service.escalate(id,new ApprovalRequests.Escalate(r.backupApproverId(),r.remark()),token)); }
    private ApprovalWorkflowResponse response(ApprovalTask t) { return new ApprovalWorkflowResponse(t.id,t.paymentId,t.status,t.requiredLevel,t.decidedBy,t.remark,t.dueAt); }
}
