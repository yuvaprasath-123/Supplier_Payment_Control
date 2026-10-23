import {
  useEffect,
  useState
} from "react";

import {
  Link,
  useParams
} from "react-router";

import {
  useAuth
} from "../auth/AuthContext";

import {
  getPayment,
  getPaymentTimeline
} from "../api/paymentApi";

import {
  getRiskAssessmentByPaymentId,
  getRiskAssessmentFactors
} from "../api/riskAssessmentApi";

import {
  getApprovalHistory,
  getApprovalRoute,
  getApprovalExplanation
} from "../api/approvalApi";

import {
  getPaymentProcessingStatus
} from "../api/paymentProcessingApi";

import "./FinanceManagerAudit.css";


function normalizeApprovalLevel(value) {
  if (
    value === null ||
    value === undefined
  ) {
    return "—";
  }

  const normalized =
    String(value)
      .trim()
      .toUpperCase();

  if (/^L\d+$/.test(normalized)) {
    return normalized;
  }

  if (/^\d+$/.test(normalized)) {
    return `L${normalized}`;
  }

  return normalized;
}


function displayValue(value) {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return "—";
  }

  return String(value);
}


function formatDate(value) {
  if (!value) {
    return "—";
  }

  const date =
    new Date(value);

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return value;
  }

  return date.toLocaleString();
}


function formatAmount(value) {
  if (
    value === null ||
    value === undefined
  ) {
    return "—";
  }

  return Number(value)
    .toLocaleString(
      undefined,
      {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }
    );
}


function AuditField({
  label,
  value,
  className = ""
}) {
  return (
    <div
      className={
        `audit-field-card ${className}`
      }
    >
      <div className="audit-field-label">
        {label}
      </div>

      <div className="audit-field-value">
        {displayValue(value)}
      </div>
    </div>
  );
}


function StatusBadge({
  value
}) {
  if (!value) {
    return (
      <span className="audit-badge">
        —
      </span>
    );
  }

  const normalized =
    String(value)
      .toUpperCase();

  return (
    <span
      className={
        `audit-badge audit-badge-${normalized.toLowerCase()}`
      }
    >
      {normalized}
    </span>
  );
}


export default function FinanceManagerAuditDetailsPage() {
  const {
    paymentId
  } = useParams();

  const {
    token
  } = useAuth();

  const [
    payment,
    setPayment
  ] = useState(null);

  const [
    timeline,
    setTimeline
  ] = useState([]);

  const [
    risk,
    setRisk
  ] = useState(null);

  const [
    factors,
    setFactors
  ] = useState([]);

  const [
    route,
    setRoute
  ] = useState(null);

  const [
    explanation,
    setExplanation
  ] = useState(null);

  const [
    history,
    setHistory
  ] = useState(null);

  const [
    processing,
    setProcessing
  ] = useState(null);

  const [
    loading,
    setLoading
  ] = useState(true);

  const [
    error,
    setError
  ] = useState("");


  useEffect(
    () => {
      let cancelled = false;

      async function loadAudit() {
        setLoading(true);
        setError("");

        try {
          const paymentData =
            await getPayment(
              token,
              paymentId
            );

          const timelineData =
            await getPaymentTimeline(
              token,
              paymentId
            );

          const riskData =
            await getRiskAssessmentByPaymentId(
              token,
              paymentId
            );

          let factorData = [];

          if (
            riskData?.assessmentId
          ) {
            factorData =
              await getRiskAssessmentFactors(
                token,
                riskData.assessmentId
              );
          }

          const [
            routeData,
            explanationData,
            historyData
          ] =
            await Promise.all([
              getApprovalRoute(
                token,
                paymentId
              ),
              getApprovalExplanation(
                token,
                paymentId
              ),
              getApprovalHistory(
                token,
                paymentId
              )
            ]);

          let processingData =
            null;

          try {
            processingData =
              await getPaymentProcessingStatus(
                token,
                paymentId
              );
          } catch {
            processingData =
              null;
          }

          if (cancelled) {
            return;
          }

          setPayment(paymentData);

          setTimeline(
            Array.isArray(
              timelineData
            )
              ? timelineData
              : []
          );

          setRisk(riskData);

          setFactors(
            Array.isArray(
              factorData
            )
              ? factorData
              : []
          );

          setRoute(routeData);
          setExplanation(explanationData);
          setHistory(historyData);
          setProcessing(processingData);

        } catch (err) {
          if (!cancelled) {
            setError(
              err.message ||
              "Unable to load payment audit."
            );
          }

        } finally {
          if (!cancelled) {
            setLoading(false);
          }
        }
      }

      loadAudit();

      return () => {
        cancelled = true;
      };
    },
    [
      token,
      paymentId
    ]
  );


  if (loading) {
    return (
      <section className="finance-manager-audit-details">
        <div className="audit-loading-card">
          <h1>
            Payment Audit
          </h1>

          <p role="status">
            Loading payment audit...
          </p>
        </div>
      </section>
    );
  }


  if (error) {
    return (
      <section className="finance-manager-audit-details">
        <div className="audit-error-card">
          <h1>
            Payment Audit
          </h1>

          <p role="alert">
            {error}
          </p>

          <Link to="/audit/payments">
            ← Back to Payment Audit
          </Link>
        </div>
      </section>
    );
  }


  const decisions =
    Array.isArray(
      history?.decisions
    )
      ? history.decisions
      : [];

  const escalations =
    Array.isArray(
      history?.escalations
    )
      ? history.escalations
      : [];


  return (
    <section className="finance-manager-audit-details">

      {/* Header */}

      <div className="audit-details-header">
        <div>
          <Link
            to="/audit/payments"
            className="audit-back-link"
          >
            ← Back to Payment Audit
          </Link>

          <h1>
            Payment Audit #{paymentId}
          </h1>

          <p>
            Finance Manager read-only audit view
          </p>
        </div>

        <div className="audit-header-status">
          <span className="audit-header-label">
            Current Status
          </span>

          <StatusBadge
            value={payment?.status}
          />
        </div>
      </div>


      {/* Summary */}

      <section className="audit-summary-section">
        <div className="audit-summary-card">
          <span>Payment Amount</span>
          <strong>
            {formatAmount(
              payment?.paymentAmount
            )}
          </strong>
        </div>

        <div className="audit-summary-card">
          <span>Risk Score</span>
          <strong>
            {displayValue(
              risk?.riskScore
            )}
          </strong>
        </div>

        <div className="audit-summary-card">
          <span>Risk Band</span>
          <strong>
            <StatusBadge
              value={risk?.riskBand}
            />
          </strong>
        </div>

        <div className="audit-summary-card">
          <span>Required Level</span>
          <strong>
            {normalizeApprovalLevel(
              route?.requiredLevel
            )}
          </strong>
        </div>
      </section>


      {/* Payment */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>Payment</h2>
            <p>
              Core payment request information
            </p>
          </div>
        </div>

        <div className="audit-field-grid">
          <AuditField
            label="Payment ID"
            value={payment?.paymentId}
          />

          <AuditField
            label="Invoice ID"
            value={payment?.invoiceId}
          />

          <AuditField
            label="Supplier ID"
            value={payment?.supplierId}
          />

          <AuditField
            label="Requester ID"
            value={payment?.requesterId}
          />

          <AuditField
            label="Amount"
            value={formatAmount(
              payment?.paymentAmount
            )}
          />

          <div className="audit-field-card">
            <div className="audit-field-label">
              Status
            </div>

            <div className="audit-field-value">
              <StatusBadge
                value={payment?.status}
              />
            </div>
          </div>

          <AuditField
            label="Created At"
            value={formatDate(
              payment?.createdAt
            )}
          />

          <AuditField
            label="Submitted At"
            value={formatDate(
              payment?.submittedAt
            )}
          />

          <AuditField
            label="Approved At"
            value={formatDate(
              payment?.approvedAt
            )}
          />

          <AuditField
            label="Completed At"
            value={formatDate(
              payment?.completedAt
            )}
          />
        </div>
      </section>


      {/* Risk */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>
              Risk Assessment
            </h2>

            <p>
              Explainable rule-based risk evaluation
            </p>
          </div>

          <StatusBadge
            value={risk?.riskBand}
          />
        </div>

        <div className="audit-field-grid">
          <AuditField
            label="Assessment ID"
            value={risk?.assessmentId}
          />

          <AuditField
            label="Assessment Status"
            value={risk?.status}
          />

          <AuditField
            label="Risk Score"
            value={risk?.riskScore}
          />

          <AuditField
            label="Risk Band"
            value={risk?.riskBand}
          />

          <AuditField
            label="Risk Approval Level"
            value={
              normalizeApprovalLevel(
                risk?.riskApprovalLevel
              )
            }
          />

          <AuditField
            label="Configuration Version"
            value={
              risk?.configurationVersionId
            }
          />

          <AuditField
            label="Completed At"
            value={formatDate(
              risk?.completedAt
            )}
          />
        </div>

        <div className="audit-subsection">
          <h3>
            Risk Factors
          </h3>

          {factors.length === 0 ? (
            <div className="audit-empty-state">
              No risk factors available.
            </div>
          ) : (
            <div className="audit-table-wrapper">
              <table className="audit-table">
                <thead>
                  <tr>
                    <th>Rule</th>
                    <th>Triggered</th>
                    <th>Weight</th>
                    <th>Contribution</th>
                    <th>Explanation</th>
                  </tr>
                </thead>

                <tbody>
                  {factors.map(
                    (
                      factor,
                      index
                    ) => (
                      <tr
                        key={
                          `${factor.ruleCode}-${index}`
                        }
                      >
                        <td>
                          <strong>
                            {factor.ruleCode}
                          </strong>
                        </td>

                        <td>
                          <StatusBadge
                            value={
                              factor.triggered
                                ? "YES"
                                : "NO"
                            }
                          />
                        </td>

                        <td>
                          {factor.configuredWeight}
                        </td>

                        <td>
                          {factor.contribution}
                        </td>

                        <td>
                          {factor.explanation}
                        </td>
                      </tr>
                    )
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>


      {/* Approval Routing */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>
              Approval Routing
            </h2>

            <p>
              Required approval determined from
              amount and risk
            </p>
          </div>
        </div>

        <div className="audit-routing-grid">
          <div className="audit-routing-level">
            <span>
              Amount Level
            </span>

            <strong>
              {normalizeApprovalLevel(
                route?.amountApprovalLevel
              )}
            </strong>
          </div>

          <div className="audit-routing-symbol">
            +
          </div>

          <div className="audit-routing-level">
            <span>
              Risk Level
            </span>

            <strong>
              {normalizeApprovalLevel(
                route?.riskApprovalLevel
              )}
            </strong>
          </div>

          <div className="audit-routing-symbol">
            →
          </div>

          <div className="audit-routing-level audit-routing-result">
            <span>
              Required Level
            </span>

            <strong>
              {normalizeApprovalLevel(
                route?.requiredLevel
              )}
            </strong>
          </div>
        </div>

        <div className="audit-explanation-box">
          <span>
            Decision Explanation
          </span>

          <strong>
            {displayValue(
              explanation?.explanation ||
              route?.explanation
            )}
          </strong>
        </div>
      </section>


      {/* Approval History */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>
              Approval History
            </h2>

            <p>
              Approval assignment, SLA and
              decision history
            </p>
          </div>

          <StatusBadge
            value={history?.task?.status}
          />
        </div>

        {history?.task && (
          <div className="audit-field-grid">
            <AuditField
              label="Task ID"
              value={history.task.id}
            />

            <AuditField
              label="Assigned Approver"
              value={
                history.task.assignedApproverId
              }
            />

            <AuditField
              label="Decided By"
              value={
                history.task.decidedBy
              }
            />

            <AuditField
              label="SLA Minutes"
              value={
                history.task.slaMinutes
              }
            />

            <AuditField
              label="Due At"
              value={formatDate(
                history.task.dueAt
              )}
            />

            <AuditField
              label="Remark"
              value={
                history.task.remark
              }
            />
          </div>
        )}

        <div className="audit-subsection">
          <h3>
            Decisions
          </h3>

          {decisions.length === 0 ? (
            <div className="audit-empty-state">
              No approval decisions recorded.
            </div>
          ) : (
            <div className="audit-table-wrapper">
              <table className="audit-table">
                <thead>
                  <tr>
                    <th>Decision</th>
                    <th>Approver</th>
                    <th>Decided At</th>
                    <th>Remark</th>
                  </tr>
                </thead>

                <tbody>
                  {decisions.map(
                    (decision) => (
                      <tr key={decision.id}>
                        <td>
                          <StatusBadge
                            value={
                              decision.decision
                            }
                          />
                        </td>

                        <td>
                          {decision.approverId}
                        </td>

                        <td>
                          {formatDate(
                            decision.decidedAt
                          )}
                        </td>

                        <td>
                          {displayValue(
                            decision.remark
                          )}
                        </td>
                      </tr>
                    )
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="audit-subsection">
          <h3>
            Escalations
          </h3>

          {escalations.length === 0 ? (
            <div className="audit-empty-state">
              No escalations recorded.
            </div>
          ) : (
            <div className="audit-table-wrapper">
              <table className="audit-table">
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>Details</th>
                  </tr>
                </thead>

                <tbody>
                  {escalations.map(
                    (
                      escalation,
                      index
                    ) => (
                      <tr key={index}>
                        <td>
                          Escalation #{index + 1}
                        </td>

                        <td>
                          <pre className="audit-inline-json">
                            {JSON.stringify(
                              escalation,
                              null,
                              2
                            )}
                          </pre>
                        </td>
                      </tr>
                    )
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>


      {/* Processing */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>
              Payment Processing
            </h2>

            <p>
              Downstream payment execution status
            </p>
          </div>

          {processing && (
            <StatusBadge
              value={
                processing.processingStatus
              }
            />
          )}
        </div>

        {!processing ? (
          <div className="audit-empty-state">
            No processing record available.
          </div>
        ) : (
          <div className="audit-field-grid">
            <AuditField
              label="Payment Status"
              value={
                processing.paymentStatus
              }
            />

            <AuditField
              label="Processing Status"
              value={
                processing.processingStatus
              }
            />

            <AuditField
              label="Attempt Count"
              value={
                processing.attemptCount
              }
            />

            <AuditField
              label="Stripe PaymentIntent"
              value={
                processing.stripePaymentIntentId
              }
            />

            <AuditField
              label="Last Error"
              value={
                processing.lastError
              }
            />
          </div>
        )}
      </section>


      {/* Lifecycle */}

      <section className="audit-section-card">
        <div className="audit-section-heading">
          <div>
            <h2>
              Lifecycle Timeline
            </h2>

            <p>
              Complete payment status history
            </p>
          </div>
        </div>

        {timeline.length === 0 ? (
          <div className="audit-empty-state">
            No timeline entries available.
          </div>
        ) : (
          <div className="audit-timeline">
            {timeline.map(
              (
                entry,
                index
              ) => (
                <div
                  className="audit-timeline-item"
                  key={
                    entry.historyId
                  }
                >
                  <div className="audit-timeline-marker">
                    <div className="audit-timeline-dot" />

                    {index <
                      timeline.length - 1 && (
                      <div className="audit-timeline-line" />
                    )}
                  </div>

                  <div className="audit-timeline-content">
                    <div className="audit-timeline-top">
                      <StatusBadge
                        value={
                          entry.newStatus
                        }
                      />

                      <span>
                        {formatDate(
                          entry.changedAt
                        )}
                      </span>
                    </div>

                    <p>
                      {displayValue(
                        entry.reason
                      )}
                    </p>

                    <small>
                      Changed by:{" "}
                      {displayValue(
                        entry.changedBy
                      )}
                    </small>
                  </div>
                </div>
              )
            )}
          </div>
        )}
      </section>

    </section>
  );
}