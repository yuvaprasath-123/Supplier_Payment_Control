import {
  useCallback,
  useEffect,
  useState
} from "react";

import {
  useNavigate,
  useParams
} from "react-router";

import {
  getPayment,
  getPaymentTimeline,
  submitPayment,
  updatePayment
} from "../api/paymentApi";

import {
  getRiskAssessmentByPaymentId
} from "../api/riskAssessmentApi";

import {
  getPaymentSlaTracker
} from "../api/approvalSlaApi";

import {
  useAuth
} from "../auth/AuthContext";

import ErrorMessage from "../components/ErrorMessage";


const SLA_REFRESH_INTERVAL_MS = 15000;


function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString();
}


function formatApprovalLevel(level) {
  if (
    level === null ||
    level === undefined
  ) {
    return "-";
  }

  return `L${String(level).replace(/^L/i, "")}`;
}


function formatDuration(totalSeconds) {
  if (!Number.isFinite(totalSeconds)) {
    return "-";
  }

  const seconds =
    Math.max(
      0,
      Math.floor(totalSeconds)
    );

  const days =
    Math.floor(
      seconds / 86400
    );

  const hours =
    Math.floor(
      (seconds % 86400) / 3600
    );

  const minutes =
    Math.floor(
      (seconds % 3600) / 60
    );

  const remainingSeconds =
    seconds % 60;

  const parts = [];

  if (days > 0) {
    parts.push(`${days}d`);
  }

  if (
    hours > 0 ||
    days > 0
  ) {
    parts.push(`${hours}h`);
  }

  parts.push(`${minutes}m`);
  parts.push(`${remainingSeconds}s`);

  return parts.join(" ");
}


function formatEscalationAction(action) {
  const labels = {
    AUTO_ESCALATED:
      "Automatically escalated",

    AUTO_REASSIGNED_TO_L3_BACKUP:
      "Automatically reassigned to L3 backup",

    AUTO_MANUAL_REVIEW:
      "Automatically moved to manual review",

    ESCALATED:
      "Manually escalated",

    REASSIGNED_TO_BACKUP:
      "Reassigned to backup",

    MANUAL_REVIEW:
      "Moved to manual review"
  };

  return (
    labels[action] ||
    action ||
    "-"
  );
}


function isOpenApprovalStatus(status) {
  return (
    status === "PENDING_APPROVAL" ||
    status === "ESCALATED"
  );
}


function SlaCountdown({
  dueAt,
  status,
  now
}) {
  if (!dueAt) {
    return <span>-</span>;
  }

  if (
    !isOpenApprovalStatus(status)
  ) {
    if (
      status === "MANUAL_REVIEW"
    ) {
      return (
        <strong>
          SLA expired — manual review required
        </strong>
      );
    }

    return (
      <span>
        Approval task{" "}
        {status?.toLowerCase() || "closed"}
      </span>
    );
  }

  const dueTimestamp =
    new Date(dueAt).getTime();

  if (
    Number.isNaN(dueTimestamp)
  ) {
    return (
      <span>
        Invalid SLA deadline
      </span>
    );
  }

  const remainingSeconds =
    Math.floor(
      (
        dueTimestamp -
        now
      ) / 1000
    );

  if (
    remainingSeconds <= 0
  ) {
    return (
      <strong>
        SLA expired — awaiting automatic processing
      </strong>
    );
  }

  return (
    <strong>
      {formatDuration(
        remainingSeconds
      )} remaining
    </strong>
  );
}


function ApprovalSlaTracker({
  tracker,
  loading,
  error,
  onRefresh,
  now
}) {
  if (
    loading &&
    !tracker
  ) {
    return (
      <div className="card">
        <h2>
          Automatic SLA Approval Tracker
        </h2>

        <p>
          Loading approval information...
        </p>
      </div>
    );
  }

  if (!tracker) {
    return (
      <div className="card">
        <h2>
          Automatic SLA Approval Tracker
        </h2>

        {error ? (
          <ErrorMessage
            message={error}
          />
        ) : (
          <p>
            No approval task is available
            for this payment yet.
          </p>
        )}

        <button
          type="button"
          onClick={onRefresh}
          disabled={loading}
        >
          {loading
            ? "Refreshing..."
            : "Refresh SLA Tracker"}
        </button>
      </div>
    );
  }

  const task =
    tracker.task;

  const escalations =
    tracker.escalations || [];

  const decisions =
    tracker.decisions || [];

  if (!task) {
    return (
      <div className="card">
        <h2>
          Automatic SLA Approval Tracker
        </h2>

        <p>
          Approval task details are unavailable.
        </p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>
        Automatic SLA Approval Tracker
      </h2>

      <p>
        Track the current approver,
        SLA deadline, and automatic
        escalation history.
      </p>

      <p>
        <strong>
          Payment ID:
        </strong>{" "}
        {tracker.paymentId}
      </p>

      <p>
        <strong>
          Approval Task ID:
        </strong>{" "}
        {task.taskId}
      </p>

      <p>
        <strong>
          Payment Status:
        </strong>{" "}

        <span className="status">
          {tracker.paymentStatus}
        </span>
      </p>

      <p>
        <strong>
          Original Required Approval Level:
        </strong>{" "}

        {formatApprovalLevel(
          tracker.originalRequiredLevel
        )}
      </p>

      <p>
        <strong>
          Current Approval Level:
        </strong>{" "}

        {formatApprovalLevel(
          task.currentLevel
        )}
      </p>

      <p>
        <strong>
          Approval Task Status:
        </strong>{" "}

        <span className="status">
          {task.status}
        </span>
      </p>

      <p>
        <strong>
          Currently Assigned Approver:
        </strong>{" "}

        {task.assignedApproverId != null
          ? `User ${task.assignedApproverId}`
          : "-"}
      </p>

      <p>
        <strong>
          Configured SLA Duration:
        </strong>{" "}

        {task.slaMinutes != null
          ? `${task.slaMinutes} minutes`
          : "-"}
      </p>

      <p>
        <strong>
          Task Created At:
        </strong>{" "}

        {formatDateTime(
          task.createdAt
        )}
      </p>

      <p>
        <strong>
          Current SLA Deadline:
        </strong>{" "}

        {formatDateTime(
          task.dueAt
        )}
      </p>

      <p>
        <strong>
          SLA Countdown:
        </strong>{" "}

        <SlaCountdown
          dueAt={task.dueAt}
          status={task.status}
          now={now}
        />
      </p>

      {task.status ===
        "MANUAL_REVIEW" && (
        <p>
          <strong>
            Attention:
          </strong>{" "}
          The approval task requires
          manual review by an authorized
          Finance Manager.
        </p>
      )}

      {task.status ===
        "ESCALATED" && (
        <p>
          This task has been escalated
          and is waiting for the currently
          assigned approver.
        </p>
      )}

      {error && (
        <ErrorMessage
          message={error}
        />
      )}

      <button
        type="button"
        onClick={onRefresh}
        disabled={loading}
      >
        {loading
          ? "Refreshing..."
          : "Refresh SLA Tracker"}
      </button>

      <p>
        <small>
          The tracker refreshes automatically
          every 15 seconds while this page is
          open. The countdown updates every
          second.
        </small>
      </p>

      <h3>
        Escalation History
      </h3>

      {escalations.length === 0 ? (
        <p>
          No escalation has occurred
          for this payment.
        </p>
      ) : (
        <div
          style={{
            overflowX: "auto"
          }}
        >
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Action</th>
                <th>Previous Level</th>
                <th>New Level</th>
                <th>Previous Approver</th>
                <th>New Approver</th>
                <th>Reason</th>
              </tr>
            </thead>

            <tbody>
              {escalations.map(
                event => (
                  <tr
                    key={
                      event.escalationId
                    }
                  >
                    <td>
                      {formatDateTime(
                        event.occurredAt
                      )}
                    </td>

                    <td>
                      <strong>
                        {formatEscalationAction(
                          event.action
                        )}
                      </strong>
                    </td>

                    <td>
                      {formatApprovalLevel(
                        event.previousLevel
                      )}
                    </td>

                    <td>
                      {formatApprovalLevel(
                        event.newLevel
                      )}
                    </td>

                    <td>
                      {event.previousApproverId != null
                        ? `User ${event.previousApproverId}`
                        : "-"}
                    </td>

                    <td>
                      {event.assignedApproverId != null
                        ? `User ${event.assignedApproverId}`
                        : "-"}
                    </td>

                    <td>
                      {event.remark || "-"}
                    </td>
                  </tr>
                )
              )}
            </tbody>
          </table>
        </div>
      )}

      <h3>
        Approval Decisions
      </h3>

      {decisions.length === 0 ? (
        <p>
          No approval decision has been
          recorded yet.
        </p>
      ) : (
        <div
          style={{
            overflowX: "auto"
          }}
        >
          <table>
            <thead>
              <tr>
                <th>Decision</th>
                <th>Approver</th>
                <th>Decision Time</th>
                <th>Remark</th>
              </tr>
            </thead>

            <tbody>
              {decisions.map(
                decision => (
                  <tr
                    key={
                      decision.decisionId
                    }
                  >
                    <td>
                      {decision.decision}
                    </td>

                    <td>
                      User{" "}
                      {decision.approverId}
                    </td>

                    <td>
                      {formatDateTime(
                        decision.decidedAt
                      )}
                    </td>

                    <td>
                      {decision.remark || "-"}
                    </td>
                  </tr>
                )
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}


export default function PaymentDetailsPage() {
  const {
    paymentId
  } = useParams();

  const navigate =
    useNavigate();

  const {
    token,
    hasRole
  } = useAuth();


  /*
   * -------------------------------------------------------
   * RISK INFORMATION ACCESS CONTROL
   * -------------------------------------------------------
   *
   * PAYMENT_REQUESTER must NOT see:
   *
   * - Risk score
   * - Risk band
   * - Required approval level
   * - Assessment ID
   * - Risk factors
   * - Rule weights
   * - Risk contribution
   * - Risk explanations
   * - Risk refresh controls
   *
   * Only internal Finance roles may access this information.
   */
  const canViewRiskAssessment =
    hasRole("FINANCE_APPROVER") ||
    hasRole("FINANCE_MANAGER");


  const [
    payment,
    setPayment
  ] = useState(null);

  const [
    timeline,
    setTimeline
  ] = useState([]);

  const [
    paymentAmount,
    setPaymentAmount
  ] = useState("");

  const [
    error,
    setError
  ] = useState("");

  const [
    loading,
    setLoading
  ] = useState(true);

  const [
    updating,
    setUpdating
  ] = useState(false);

  const [
    submitting,
    setSubmitting
  ] = useState(false);


  /*
   * Risk state remains available for authorized
   * Finance roles, but Requesters never load it.
   */
  const [
    riskAssessment,
    setRiskAssessment
  ] = useState(null);

  const [
    riskLoading,
    setRiskLoading
  ] = useState(false);

  const [
    riskError,
    setRiskError
  ] = useState("");


  const [
    slaTracker,
    setSlaTracker
  ] = useState(null);

  const [
    slaLoading,
    setSlaLoading
  ] = useState(false);

  const [
    slaError,
    setSlaError
  ] = useState("");

  const [
    now,
    setNow
  ] = useState(
    () => Date.now()
  );


  const loadPayment =
    useCallback(
      async () => {
        try {
          setError("");

          const [
            paymentData,
            timelineData
          ] =
            await Promise.all([
              getPayment(
                token,
                paymentId
              ),

              getPaymentTimeline(
                token,
                paymentId
              )
            ]);

          setPayment(
            paymentData
          );

          setTimeline(
            timelineData || []
          );

          setPaymentAmount(
            paymentData.paymentAmount
          );

          return paymentData;

        } catch (err) {
          setError(
            err.message
          );

          return null;

        } finally {
          setLoading(false);
        }
      },
      [
        token,
        paymentId
      ]
    );


  /*
   * -------------------------------------------------------
   * RISK LOAD
   * -------------------------------------------------------
   *
   * Important security-related UI behavior:
   *
   * Do not even send the risk API request for a requester.
   */
  const loadRiskAssessment =
    useCallback(
      async () => {
        if (
          !canViewRiskAssessment
        ) {
          setRiskAssessment(
            null
          );

          setRiskError("");

          return;
        }

        setRiskLoading(true);
        setRiskError("");
        setRiskAssessment(null);

        try {
          const data =
            await getRiskAssessmentByPaymentId(
              token,
              paymentId
            );

          setRiskAssessment(
            data
          );

        } catch (err) {
          if (
            err.status !== 404
          ) {
            setRiskError(
              err.message ||
              "Unable to load risk assessment."
            );
          }

        } finally {
          setRiskLoading(false);
        }
      },
      [
        token,
        paymentId,
        canViewRiskAssessment
      ]
    );


  const loadSlaTracker =
    useCallback(
      async () => {
        if (
          !token ||
          !paymentId
        ) {
          return;
        }

        setSlaLoading(true);
        setSlaError("");

        try {
          const data =
            await getPaymentSlaTracker(
              token,
              paymentId
            );

          setSlaTracker(
            data
          );

        } catch (err) {
          if (
            err.status === 404
          ) {
            setSlaTracker(null);

          } else if (
            err.status === 403
          ) {
            setSlaTracker(null);

            setSlaError(
              "You are not authorized to view the SLA tracker for this payment."
            );

          } else if (
            err.status === 401
          ) {
            setSlaTracker(null);

            setSlaError(
              "Your session is invalid or expired. Please sign in again."
            );

          } else if (
            err.status === 409
          ) {
            setSlaTracker(null);

            setSlaError(
              "Approval tracking is not available yet. The payment may still be awaiting risk assessment or workflow creation."
            );

          } else {
            setSlaError(
              err.message ||
              "Unable to refresh SLA tracking."
            );
          }

        } finally {
          setSlaLoading(false);
        }
      },
      [
        token,
        paymentId
      ]
    );


  /*
   * -------------------------------------------------------
   * INITIAL PAGE LOAD
   * -------------------------------------------------------
   */
  useEffect(
    () => {
      if (
        !token ||
        !paymentId
      ) {
        return;
      }

      let active = true;

      async function loadDetails() {
        setLoading(true);
        setError("");

        setRiskError("");
        setRiskAssessment(null);

        setSlaTracker(null);
        setSlaError("");

        try {
          const [
            paymentData,
            timelineData
          ] =
            await Promise.all([
              getPayment(
                token,
                paymentId
              ),

              getPaymentTimeline(
                token,
                paymentId
              )
            ]);

          if (!active) {
            return;
          }

          setPayment(
            paymentData
          );

          setTimeline(
            timelineData || []
          );

          setPaymentAmount(
            paymentData.paymentAmount
          );

        } catch (err) {
          if (active) {
            setError(
              err.message
            );

            setPayment(null);
          }

        } finally {
          if (active) {
            setLoading(false);
          }
        }


        /*
         * REQUESTER STOPS HERE.
         *
         * No risk-assessment API request is sent.
         */
        if (
          !active ||
          !canViewRiskAssessment
        ) {
          setRiskLoading(false);

          return;
        }


        setRiskLoading(true);

        try {
          const data =
            await getRiskAssessmentByPaymentId(
              token,
              paymentId
            );

          if (active) {
            setRiskAssessment(
              data
            );
          }

        } catch (err) {
          if (
            active &&
            err.status !== 404
          ) {
            setRiskError(
              err.message ||
              "Unable to load risk assessment."
            );
          }

        } finally {
          if (active) {
            setRiskLoading(false);
          }
        }
      }


      loadDetails();


      return () => {
        active = false;
      };
    },
    [
      token,
      paymentId,
      canViewRiskAssessment
    ]
  );


  /*
   * -------------------------------------------------------
   * SLA TRACKER REFRESH
   * -------------------------------------------------------
   */
  useEffect(
    () => {
      if (
        !token ||
        !paymentId
      ) {
        return;
      }

      let active = true;

      let requestInProgress =
        false;


      async function refresh() {
        if (
          requestInProgress ||
          !active
        ) {
          return;
        }

        requestInProgress =
          true;

        try {
          const data =
            await getPaymentSlaTracker(
              token,
              paymentId
            );

          if (active) {
            setSlaTracker(
              data
            );

            setSlaError("");
          }

        } catch (err) {
          if (!active) {
            return;
          }

          if (
            err.status === 404
          ) {
            setSlaTracker(null);
            setSlaError("");

          } else if (
            err.status === 403
          ) {
            setSlaTracker(null);

            setSlaError(
              "You are not authorized to view the SLA tracker for this payment."
            );

          } else if (
            err.status === 401
          ) {
            setSlaTracker(null);

            setSlaError(
              "Your session is invalid or expired. Please sign in again."
            );

          } else if (
            err.status === 409
          ) {
            setSlaTracker(null);

            setSlaError(
              "Approval tracking is not available yet. The payment may still be awaiting risk assessment or workflow creation."
            );

          } else {
            setSlaError(
              err.message ||
              "Unable to refresh SLA tracking."
            );
          }

        } finally {
          requestInProgress =
            false;
        }
      }


      setSlaTracker(null);
      setSlaError("");

      refresh();


      const refreshInterval =
        setInterval(
          refresh,
          SLA_REFRESH_INTERVAL_MS
        );


      return () => {
        active = false;

        clearInterval(
          refreshInterval
        );
      };
    },
    [
      token,
      paymentId
    ]
  );


  /*
   * Countdown clock.
   */
  useEffect(
    () => {
      const countdownInterval =
        setInterval(
          () => {
            setNow(
              Date.now()
            );
          },
          1000
        );

      return () => {
        clearInterval(
          countdownInterval
        );
      };
    },
    []
  );


  async function handleUpdate(
    event
  ) {
    event.preventDefault();

    if (updating) {
      return;
    }

    setUpdating(true);
    setError("");

    try {
      await updatePayment(
        token,
        paymentId,
        paymentAmount
      );

      await loadPayment();

    } catch (err) {
      if (
        err.status === 409
      ) {
        setError(
          "This payment request was modified by another user. Refresh and try again."
        );

        return;
      }

      setError(
        err.message
      );

    } finally {
      setUpdating(false);
    }
  }


  async function handleSubmitPayment() {
    if (submitting) {
      return;
    }

    setSubmitting(true);
    setError("");

    try {
      await submitPayment(
        token,
        paymentId
      );

      navigate(
        `/payments/${paymentId}/submission-success`
      );

    } catch (err) {
      let message =
        err.message ||
        "Payment submission failed.";

      if (
        err.status === 409
      ) {
        message =
          "This payment request was already modified or submitted by another user.";
      }

      navigate(
        `/payments/${paymentId}/submission-failure`,
        {
          state: {
            message
          }
        }
      );

    } finally {
      setSubmitting(false);
    }
  }


  if (loading) {
    return (
      <div className="page">
        <p>
          Loading payment...
        </p>
      </div>
    );
  }


  if (!payment) {
    return (
      <div className="page">
        <ErrorMessage
          message={
            error ||
            "Payment request not found"
          }
        />
      </div>
    );
  }


  return (
    <div className="page">

      <h1>
        Payment Details
      </h1>


      <ErrorMessage
        message={error}
      />


      {/* ==================================================
          PAYMENT DETAILS
          ================================================== */}

      <div className="card">

        <p>
          <strong>
            Payment ID:
          </strong>{" "}
          {payment.paymentId}
        </p>


        <p>
          <strong>
            Invoice ID:
          </strong>{" "}
          {payment.invoiceId}
        </p>


        <p>
          <strong>
            Supplier ID:
          </strong>{" "}
          {payment.supplierId}
        </p>


        <p>
          <strong>
            Requester ID:
          </strong>{" "}
          {payment.requesterId}
        </p>


        <p>
          <strong>
            Payment Amount:
          </strong>{" "}
          ₹
          {Number(
            payment.paymentAmount
          ).toLocaleString(
            "en-IN"
          )}
        </p>


        <p>
          <strong>
            Status:
          </strong>{" "}

          <span className="status">
            {payment.status}
          </span>
        </p>


        {/*
         * IMPORTANT:
         *
         * These fields used to be visible to requester.
         *
         * They are now restricted to authorized
         * Finance roles.
         */}

        {canViewRiskAssessment &&
          payment.riskScore !== null &&
          payment.riskScore !== undefined && (
          <p>
            <strong>
              Risk Score:
            </strong>{" "}
            {payment.riskScore}
          </p>
        )}


        {canViewRiskAssessment &&
          payment.riskBand && (
          <p>
            <strong>
              Risk Band:
            </strong>{" "}
            {payment.riskBand}
          </p>
        )}


        {canViewRiskAssessment &&
          payment.requiredApprovalLevel != null && (
          <p>
            <strong>
              Required Approval Level:
            </strong>{" "}
            {payment.requiredApprovalLevel}
          </p>
        )}


        {payment.transactionReference && (
          <p>
            <strong>
              Transaction Reference:
            </strong>{" "}
            {payment.transactionReference}
          </p>
        )}


        {payment.completedAt && (
          <p>
            <strong>
              Completed At:
            </strong>{" "}

            {formatDateTime(
              payment.completedAt
            )}
          </p>
        )}


        {payment.failedAt && (
          <p>
            <strong>
              Failed At:
            </strong>{" "}

            {formatDateTime(
              payment.failedAt
            )}
          </p>
        )}


        {payment.failureReason && (
          <p>
            <strong>
              Failure Reason:
            </strong>{" "}
            {payment.failureReason}
          </p>
        )}

      </div>


      {/* ==================================================
          RISK ASSESSMENT

          REQUESTER WILL NOT SEE THIS ENTIRE SECTION.
          ================================================== */}

      {canViewRiskAssessment && (
        <div className="card">

          <h2>
            Risk Assessment Details
          </h2>


          {riskLoading ? (

            <p>
              Loading risk assessment...
            </p>

          ) : riskError ? (

            <ErrorMessage
              message={riskError}
            />

          ) : !riskAssessment ? (

            <p>
              No risk assessment available
              for this payment.
            </p>

          ) : (

            <>
              <p>
                <strong>
                  Assessment ID:
                </strong>{" "}
                {riskAssessment.assessmentId}
              </p>


              <p>
                <strong>
                  Assessment Status:
                </strong>{" "}
                {riskAssessment.status}
              </p>


              <p>
                <strong>
                  Risk Score:
                </strong>{" "}
                {riskAssessment.riskScore}
              </p>


              <p>
                <strong>
                  Risk Band:
                </strong>{" "}
                {riskAssessment.riskBand}
              </p>


              <p>
                <strong>
                  Risk Approval Level:
                </strong>{" "}

                {formatApprovalLevel(
                  riskAssessment.riskApprovalLevel
                )}
              </p>


              <p>
                <strong>
                  Configuration Version ID:
                </strong>{" "}
                {riskAssessment.configurationVersionId}
              </p>


              <p>
                <strong>
                  Completed At:
                </strong>{" "}

                {formatDateTime(
                  riskAssessment.completedAt
                )}
              </p>


              <h3>
                Risk Factor Breakdown
              </h3>


              <div
                style={{
                  overflowX: "auto"
                }}
              >
                <table>
                  <thead>
                    <tr>
                      <th>
                        Risk Rule
                      </th>

                      <th>
                        Triggered
                      </th>

                      <th>
                        Configured Weight
                      </th>

                      <th>
                        Contribution
                      </th>

                      <th>
                        Explanation
                      </th>
                    </tr>
                  </thead>


                  <tbody>

                    {(
                      !riskAssessment.factors ||
                      riskAssessment.factors.length === 0
                    ) ? (

                      <tr>
                        <td
                          colSpan={5}
                        >
                          No risk factors available.
                        </td>
                      </tr>

                    ) : (

                      riskAssessment.factors.map(
                        factor => (
                          <tr
                            key={
                              factor.ruleCode
                            }
                          >
                            <td>
                              {factor.ruleCode}
                            </td>

                            <td>
                              {factor.triggered
                                ? "Yes"
                                : "No"}
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
                      )

                    )}

                  </tbody>
                </table>
              </div>
            </>
          )}


          <button
            type="button"
            onClick={
              loadRiskAssessment
            }
            disabled={
              riskLoading
            }
          >
            {riskLoading
              ? "Refreshing..."
              : "Refresh Risk Assessment"}
          </button>

        </div>
      )}


      {/* ==================================================
          SLA TRACKER
          ================================================== */}

      <ApprovalSlaTracker
        tracker={slaTracker}
        loading={slaLoading}
        error={slaError}
        onRefresh={loadSlaTracker}
        now={now}
      />


      {/* ==================================================
          PAYMENT COMPLETE
          ================================================== */}

      {payment.status ===
        "COMPLETE" && (
        <div className="card">

          <h2>
            Payment Completed
          </h2>

          <p>
            The payment has been
            processed successfully.
          </p>

          {payment.transactionReference && (
            <p>
              <strong>
                Stripe Transaction Reference:
              </strong>{" "}
              {payment.transactionReference}
            </p>
          )}

        </div>
      )}


      {/* ==================================================
          PAYMENT FAILED
          ================================================== */}

      {payment.status ===
        "FAILED" && (
        <div className="card">

          <h2>
            Payment Failed
          </h2>

          <p>
            The payment could not
            be completed.
          </p>

          {payment.failureReason && (
            <p>
              <strong>
                Reason:
              </strong>{" "}
              {payment.failureReason}
            </p>
          )}

        </div>
      )}


      {/* ==================================================
          DRAFT PAYMENT ACTIONS
          ================================================== */}

      {payment.status ===
        "DRAFT" && (

        <form
          className="card form-row"
          onSubmit={
            handleUpdate
          }
        >

          <input
            type="number"
            min="0.01"
            step="0.01"
            value={
              paymentAmount
            }
            onChange={
              event =>
                setPaymentAmount(
                  event.target.value
                )
            }
            required
            disabled={
              updating ||
              submitting
            }
          />


          <button
            type="submit"
            disabled={
              updating ||
              submitting
            }
          >
            {updating
              ? "Updating..."
              : "Update Payment"}
          </button>


          <button
            type="button"
            onClick={
              handleSubmitPayment
            }
            disabled={
              updating ||
              submitting
            }
          >
            {submitting
              ? "Submitting..."
              : "Submit Payment"}
          </button>

        </form>
      )}


      {/* ==================================================
          TIMELINE
          ================================================== */}

      <div className="card">

        <h2>
          Timeline
        </h2>


        {timeline.length === 0 ? (

          <p>
            No status history available.
          </p>

        ) : (

          <div
            style={{
              overflowX: "auto"
            }}
          >
            <table>

              <thead>
                <tr>
                  <th>
                    Previous Status
                  </th>

                  <th>
                    New Status
                  </th>

                  <th>
                    Changed By
                  </th>

                  <th>
                    Changed At
                  </th>

                  <th>
                    Reason
                  </th>
                </tr>
              </thead>


              <tbody>

                {timeline.map(
                  history => (
                    <tr
                      key={
                        history.id
                      }
                    >

                      <td>
                        {history.previousStatus ||
                          "-"}
                      </td>

                      <td>
                        {history.newStatus}
                      </td>

                      <td>
                        {history.changedBy}
                      </td>

                      <td>
                        {formatDateTime(
                          history.changedAt
                        )}
                      </td>

                      <td>
                        {history.reason ||
                          "-"}
                      </td>

                    </tr>
                  )
                )}

              </tbody>
            </table>
          </div>
        )}

      </div>

    </div>
  );
}