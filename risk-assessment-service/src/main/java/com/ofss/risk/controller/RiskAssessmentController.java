package com.ofss.risk.controller;

import com.ofss.risk.dto.RiskAssessmentRequest;
import com.ofss.risk.dto.RiskAssessmentResponse;
import com.ofss.risk.dto.RiskFactorResponse;
import com.ofss.risk.repository.RiskAssessmentRepository;
import com.ofss.risk.service.RiskAssessmentService;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk-assessments")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;
    private final RiskAssessmentRepository riskAssessmentRepository;

    public RiskAssessmentController(
            RiskAssessmentService riskAssessmentService,
            RiskAssessmentRepository riskAssessmentRepository
    ) {
        this.riskAssessmentService = riskAssessmentService;
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    @PostMapping
    public ResponseEntity<RiskAssessmentResponse> assessPayment(
            @Valid @RequestBody RiskAssessmentRequest request
    ) {

        RiskAssessmentResponse response =
                riskAssessmentService.assess(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{assessmentId}")
    public RiskAssessmentResponse getAssessment(
            @PathVariable long assessmentId
    ) {

        return riskAssessmentService
                .getAssessment(assessmentId);
    }

    @GetMapping("/payment/{paymentId}")
    public RiskAssessmentResponse getAssessmentByPayment(
            @PathVariable long paymentId
    ) {

        return riskAssessmentService
                .getAssessmentByPayment(paymentId);
    }

    @GetMapping("/{assessmentId}/factors")
    public List<RiskFactorResponse> getFactors(
            @PathVariable long assessmentId
    ) {

        return riskAssessmentRepository
                .findFactors(assessmentId);
    }
}