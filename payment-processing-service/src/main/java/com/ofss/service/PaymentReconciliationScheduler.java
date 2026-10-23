
package com.ofss.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService reconciliationService;
    private final boolean enabled;

    public PaymentReconciliationScheduler(
            PaymentReconciliationService reconciliationService,
            @Value("${payment.reconciliation.enabled:false}")
            boolean enabled
    ) {
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.reconciliation.interval-ms:60000}"
    )
    public void reconcile() {

        if (!enabled) {
            return;
        }

        reconciliationService.inspectCandidates();
    }
}
