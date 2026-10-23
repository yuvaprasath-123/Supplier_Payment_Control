
package com.ofss.service;

import com.ofss.event.PaymentApprovedEvent;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentProcessingAttemptServiceTest {

    private final JdbcTemplate jdbcTemplate =
            mock(JdbcTemplate.class);

    private final PaymentProcessingAttemptService service =
            new PaymentProcessingAttemptService(jdbcTemplate);

    private PaymentApprovedEvent validEvent() {
        return new PaymentApprovedEvent(
                3L,
                3L,
                1L,
                new BigDecimal("50000.00"),
                3L,
                LocalDateTime.of(
                        2026, 10, 18,
                        12, 1, 5, 677766000
                )
        );
    }

    @Test
    void rejectsMissingPaymentId() {

        PaymentApprovedEvent event = validEvent();
        event.setPaymentId(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(event)
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsMissingAmount() {

        PaymentApprovedEvent event = validEvent();
        event.setPaymentAmount(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(event)
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsMissingApprover() {

        PaymentApprovedEvent event = validEvent();
        event.setApprovedBy(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(event)
        );

        verifyNoInteractions(jdbcTemplate);
    }

    /*
     * Simulates all three Oracle queries:
     *
     * 1. Trusted payment data.
     * 2. Verified approval decision.
     * 3. Existing processing-attempt status.
     *
     * No real Oracle database is accessed.
     */
    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private void mockOracleResponses(
            Long verifiedApproverId,
            String existingAttemptStatus
    ) throws Exception {

        ResultSet paymentRs = mock(ResultSet.class);

        when(paymentRs.getLong("PAYMENT_ID"))
                .thenReturn(3L);

        when(paymentRs.getLong("INVOICE_ID"))
                .thenReturn(3L);

        when(paymentRs.getLong("SUPPLIER_ID"))
                .thenReturn(1L);

        when(paymentRs.getBigDecimal("PAYMENT_AMOUNT"))
                .thenReturn(
                        new BigDecimal("50000.00")
                );

        when(paymentRs.getString("STATUS"))
                .thenReturn("PROCESSING");

        when(paymentRs.getTimestamp("APPROVED_AT"))
                .thenReturn(
                        Timestamp.valueOf(
                                LocalDateTime.of(
                                        2026, 10, 18,
                                        12, 1, 5, 677766000
                                )
                        )
                );

        ResultSet approverRs = mock(ResultSet.class);

        if (verifiedApproverId != null) {
            when(approverRs.getLong("APPROVER_ID"))
                    .thenReturn(verifiedApproverId);
        }

        ResultSet attemptRs = mock(ResultSet.class);

        if (existingAttemptStatus != null) {
            when(attemptRs.getString("STATUS"))
                    .thenReturn(existingAttemptStatus);
        }

        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(3L)
        )).thenAnswer(invocation -> {

            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);

            if (sql.contains("FROM PAY_PAYMENT_REQUESTS")) {
                return List.of(
                        mapper.mapRow(paymentRs, 0)
                );
            }

            if (sql.contains("FROM APPROVAL_TASKS")) {

                if (verifiedApproverId == null) {
                    return List.of();
                }

                return List.of(
                        mapper.mapRow(approverRs, 0)
                );
            }

            if (sql.contains("FROM PAY_PROCESSING_ATTEMPTS")) {

                if (existingAttemptStatus == null) {
                    return List.of();
                }

                return List.of(
                        mapper.mapRow(attemptRs, 0)
                );
            }

            throw new IllegalStateException(
                    "Unexpected SQL query in test"
            );
        });
    }

    private void verifyNoProcessingAttemptCreated() {

        verify(jdbcTemplate, never()).update(
                anyString(),
                any(Object[].class)
        );
    }

    @Test
    void rejectsIncorrectPaymentAmountBeforeCreatingAttempt()
            throws Exception {

        mockOracleResponses(3L, null);

        PaymentApprovedEvent event = validEvent();

        event.setPaymentAmount(
                new BigDecimal("60000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.claim(event)
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void rejectsIncorrectInvoiceId()
            throws Exception {

        mockOracleResponses(3L, null);

        PaymentApprovedEvent event = validEvent();
        event.setInvoiceId(99L);

        assertThrows(
                IllegalStateException.class,
                () -> service.claim(event)
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void rejectsIncorrectSupplierId()
            throws Exception {

        mockOracleResponses(3L, null);

        PaymentApprovedEvent event = validEvent();
        event.setSupplierId(99L);

        assertThrows(
                IllegalStateException.class,
                () -> service.claim(event)
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void rejectsIncorrectApprovalTimestamp()
            throws Exception {

        mockOracleResponses(3L, null);

        PaymentApprovedEvent event = validEvent();

        event.setApprovedAt(
                LocalDateTime.of(
                        2026, 10, 18, 15, 30
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.claim(event)
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void rejectsIncorrectApproverId()
            throws Exception {

        // Oracle says the actual approver is user 3.
        mockOracleResponses(3L, null);

        PaymentApprovedEvent event = validEvent();

        // Kafka message incorrectly claims user 99.
        event.setApprovedBy(99L);

        assertThrows(
                IllegalStateException.class,
                () -> service.claim(event)
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void skipsAlreadyProcessingAttempt()
            throws Exception {

        mockOracleResponses(3L, "PROCESSING");

        var result = service.claim(validEvent());

        assertEquals(
                PaymentProcessingAttemptService
                        .ClaimResult.ALREADY_PROCESSING,
                result
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void skipsAlreadyCompletedAttempt()
            throws Exception {

        mockOracleResponses(3L, "COMPLETED");

        var result = service.claim(validEvent());

        assertEquals(
                PaymentProcessingAttemptService
                        .ClaimResult.ALREADY_COMPLETED,
                result
        );

        verifyNoProcessingAttemptCreated();
    }

    @Test
    void requiresReconciliationForUncertainAttempt()
            throws Exception {

        mockOracleResponses(
                3L,
                "REQUIRES_RECONCILIATION"
        );

        var result = service.claim(validEvent());

        assertEquals(
                PaymentProcessingAttemptService
                        .ClaimResult.REQUIRES_RECONCILIATION,
                result
        );

        verifyNoProcessingAttemptCreated();
    }
}
