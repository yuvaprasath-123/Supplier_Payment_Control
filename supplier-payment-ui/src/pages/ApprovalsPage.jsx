import { useEffect, useState } from "react";
import { useNavigate } from "react-router";

import { useAuth } from "../auth/AuthContext";

import {
  getApprovalTasks,
  approveTask,
  rejectTask,
  overrideAndApproveTask
} from "../api/approvalApi";

import {
  getRiskAssessmentByPaymentId
} from "../api/riskAssessmentApi";

import "./ApprovalsPage.css";


function formatDateTime(value) {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString();
}


function formatApprovalLevel(level) {
  if (level === null || level === undefined) {
    return "—";
  }

  const value = String(level);

  return value.toUpperCase().startsWith("L")
    ? value.toUpperCase()
    : `L${value}`;
}


export default function ApprovalsPage() {
  const {
    token,
    user,
    hasRole
  } = useAuth();

  const navigate = useNavigate();


  /*
   * =========================================================
   * APPROVAL TASK STATE
   * =========================================================
   */
  const [tasks, setTasks] =
    useState([]);

  const [status, setStatus] =
    useState("");

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  const [success, setSuccess] =
    useState("");

  const [refreshKey, setRefreshKey] =
    useState(0);


  /*
   * =========================================================
   * APPROVAL DECISION STATE
   * =========================================================
   */
  const [selectedTask, setSelectedTask] =
    useState(null);

  const [action, setAction] =
    useState("");

  const [remark, setRemark] =
    useState("");

  const [submitting, setSubmitting] =
    useState(false);


  /*
   * =========================================================
   * RISK ASSESSMENT STATE
   * =========================================================
   */
  const [riskTask, setRiskTask] =
    useState(null);

  const [riskAssessment, setRiskAssessment] =
    useState(null);

  const [riskLoading, setRiskLoading] =
    useState(false);

  const [riskError, setRiskError] =
    useState("");


  /*
   * =========================================================
   * AUTHENTICATED USER
   * =========================================================
   */
  const currentUserId =
    Number(user?.userId);

  const isApprover =
    hasRole("FINANCE_APPROVER");

  const isManager =
    hasRole("FINANCE_MANAGER");


  /*
   * Finance Approvers and Finance Managers
   * are allowed to view risk assessment details.
   */
  const canViewRisk =
    isApprover || isManager;


  /*
   * =========================================================
   * LOAD APPROVAL TASKS
   * =========================================================
   */
  useEffect(() => {
    let cancelled = false;

    async function loadTasks() {
      setLoading(true);
      setError("");

      try {
        const result =
          await getApprovalTasks(
            token,
            status
              ? { status }
              : {}
          );

        if (!cancelled) {
          setTasks(
            Array.isArray(result)
              ? result
              : []
          );
        }

      } catch (err) {

        if (!cancelled) {
          setError(
            err.message ||
            "Unable to load approvals."
          );

          setTasks([]);
        }

      } finally {

        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadTasks();

    return () => {
      cancelled = true;
    };

  }, [
    token,
    status,
    refreshKey
  ]);


  /*
   * =========================================================
   * SORT TASKS
   * =========================================================
   *
   * Latest/highest Payment ID appears first.
   *
   * Example:
   *
   * 470
   * 450
   * 431
   * 430
   * 411
   * 410
   */
  const sortedTasks =
    [...tasks].sort(
      (a, b) =>
        Number(b.paymentId) -
        Number(a.paymentId)
    );


  /*
   * =========================================================
   * APPROVAL PERMISSION HELPERS
   * =========================================================
   */
  function isOpenTask(task) {
    return [
      "PENDING_APPROVAL",
      "ESCALATED"
    ].includes(task.status);
  }


  function canDecide(task) {
    return (
      isOpenTask(task) &&
      Number(task.assignedApproverId) ===
        currentUserId &&
      Number(task.requesterId) !==
        currentUserId
    );
  }


  function canOverride(task) {
    return (
      isManager &&
      [
        "PENDING_APPROVAL",
        "ESCALATED",
        "MANUAL_REVIEW"
      ].includes(task.status) &&
      Number(task.requesterId) !==
        currentUserId
    );
  }


  /*
   * =========================================================
   * VIEW RISK ASSESSMENT
   * =========================================================
   */
  async function viewRisk(task) {
    if (!canViewRisk) {
      setError(
        "You are not authorized to view risk assessment details."
      );

      return;
    }

    setRiskTask(task);
    setRiskAssessment(null);
    setRiskError("");
    setRiskLoading(true);

    setError("");
    setSuccess("");

    try {
      const result =
        await getRiskAssessmentByPaymentId(
          token,
          task.paymentId
        );

      setRiskAssessment(result);

    } catch (err) {

      if (err.status === 404) {

        setRiskError(
          "Risk assessment is not available for this payment yet."
        );

      } else if (err.status === 403) {

        setRiskError(
          "You are not authorized to view this risk assessment."
        );

      } else if (err.status === 401) {

        setRiskError(
          "Your session has expired. Please sign in again."
        );

      } else {

        setRiskError(
          err.message ||
          "Unable to load risk assessment."
        );
      }

    } finally {
      setRiskLoading(false);
    }
  }


  function closeRisk() {
    if (riskLoading) {
      return;
    }

    setRiskTask(null);
    setRiskAssessment(null);
    setRiskError("");
  }


  /*
   * =========================================================
   * OPEN APPROVAL ACTION
   * =========================================================
   */
  function openAction(
    task,
    selectedAction
  ) {
    setSelectedTask(task);
    setAction(selectedAction);
    setRemark("");

    setError("");
    setSuccess("");
  }


  function closeAction() {
    if (submitting) {
      return;
    }

    setSelectedTask(null);
    setAction("");
    setRemark("");
  }


  /*
   * =========================================================
   * SUBMIT APPROVAL DECISION
   * =========================================================
   */
  async function submitDecision(event) {
    event.preventDefault();

    if (
      !selectedTask ||
      submitting
    ) {
      return;
    }


    if (
      !Number.isSafeInteger(
        currentUserId
      ) ||
      currentUserId <= 0
    ) {

      setError(
        "Authenticated user ID is unavailable."
      );

      return;
    }


    /*
     * Rejection and manager override
     * require justification.
     */
    if (
      (
        action === "reject" ||
        action === "override"
      ) &&
      !remark.trim()
    ) {

      setError(
        "A justification is required."
      );

      return;
    }


    const labels = {
      approve: "Approve",
      reject: "Reject",
      override: "Override and approve"
    };


    if (
      !window.confirm(
        `${labels[action]} task ${selectedTask.id}?`
      )
    ) {
      return;
    }


    const paymentId =
      selectedTask.paymentId;

    const taskId =
      selectedTask.id;

    const completedAction =
      action;


    setSubmitting(true);
    setError("");
    setSuccess("");


    const decision = {
      approverId: currentUserId,
      remark:
        remark.trim() || null
    };


    try {

      /*
       * APPROVE
       */
      if (
        completedAction ===
        "approve"
      ) {

        await approveTask(
          token,
          taskId,
          decision
        );


      /*
       * REJECT
       */
      } else if (
        completedAction ===
        "reject"
      ) {

        await rejectTask(
          token,
          taskId,
          decision
        );


      /*
       * MANAGER OVERRIDE
       */
      } else if (
        completedAction ===
        "override"
      ) {

        await overrideAndApproveTask(
          token,
          taskId,
          decision
        );


      } else {

        throw new Error(
          "Unknown approval action."
        );
      }


      /*
       * Reset decision state.
       */
      setSelectedTask(null);
      setAction("");
      setRemark("");


      /*
       * Reset risk panel because task state
       * may have changed.
       */
      setRiskTask(null);
      setRiskAssessment(null);
      setRiskError("");


      /*
       * Refresh approval table.
       */
      setRefreshKey(
        value => value + 1
      );


      /*
       * Approved payments continue to
       * payment processing.
       */
      if (
        completedAction ===
          "approve" ||
        completedAction ===
          "override"
      ) {

        navigate(
          `/payment-processing?paymentId=${encodeURIComponent(
            paymentId
          )}`,
          {
            state: {
              fromApproval: true,
              taskId
            }
          }
        );

        return;
      }


      /*
       * Rejection remains on approval page.
       */
      setSuccess(
        `Task ${taskId} rejected successfully.`
      );


    } catch (err) {

      setError(
        err.message ||
        "Unable to complete approval action."
      );

    } finally {

      setSubmitting(false);
    }
  }


  /*
   * =========================================================
   * PAGE
   * =========================================================
   */
  return (
    <section className="approvals-page">

      {/* =====================================================
          HEADER
          ===================================================== */}

      <div className="approvals-header">

        <h1>
          Approval Workflow
        </h1>

        <p>
          Review payment approval tasks,
          risk assessment details and
          approval status.
        </p>

      </div>


      {/* =====================================================
          STATUS FILTER
          ===================================================== */}

      <div className="approval-filters">

        <label htmlFor="approval-status">
          Filter by status
        </label>

        <select
          id="approval-status"
          value={status}
          onChange={
            event =>
              setStatus(
                event.target.value
              )
          }
        >

          <option value="">
            All statuses
          </option>

          <option value="PENDING_APPROVAL">
            Pending Approval
          </option>

          <option value="ESCALATED">
            Escalated
          </option>

          <option value="MANUAL_REVIEW">
            Manual Review
          </option>

          <option value="APPROVED">
            Approved
          </option>

          <option value="REJECTED">
            Rejected
          </option>

        </select>

      </div>


      {/* =====================================================
          ERROR / SUCCESS
          ===================================================== */}

      {error && (
        <p
          role="alert"
          className="approval-error"
        >
          {error}
        </p>
      )}


      {success && (
        <p
          role="status"
          className="approval-success"
        >
          {success}
        </p>
      )}


      {/* =====================================================
          LOADING
          ===================================================== */}

      {loading && (
        <p role="status">
          Loading approvals...
        </p>
      )}


      {!loading &&
        !error &&
        sortedTasks.length === 0 && (

          <p>
            No approval tasks found.
          </p>

        )}


      {/* =====================================================
          APPROVAL TABLE
          ===================================================== */}

      {!loading &&
        sortedTasks.length > 0 && (

        <div className="approvals-table-wrapper">

          <table className="approvals-table">

            <thead>

              <tr>

                <th>
                  Task ID
                </th>

                <th>
                  Payment ID
                </th>

                <th>
                  Assigned Approver
                </th>

                <th>
                  Status
                </th>

                <th>
                  Due At
                </th>

                <th>
                  Actions
                </th>

              </tr>

            </thead>


            <tbody>

              {sortedTasks.map(
                task => (

                  <tr key={task.id}>

                    <td>
                      {task.id}
                    </td>


                    <td>
                      {task.paymentId}
                    </td>


                    <td>
                      {
                        task.assignedApproverId ??
                        "—"
                      }
                    </td>


                    <td>
                      {task.status}
                    </td>


                    <td>
                      {
                        task.dueAt
                          ? new Date(
                              task.dueAt
                            ).toLocaleString()
                          : "—"
                      }
                    </td>


                    <td>

                      <div className="approval-actions">


                        {/* ===============================
                            VIEW RISK
                            =============================== */}

                        {canViewRisk && (

                          <button
                            type="button"
                            onClick={() =>
                              viewRisk(task)
                            }
                          >
                            View Risk
                          </button>

                        )}


                        {/* ===============================
                            APPROVE / REJECT
                            =============================== */}

                        {canDecide(task) && (

                          <>

                            <button
                              type="button"
                              onClick={() =>
                                openAction(
                                  task,
                                  "approve"
                                )
                              }
                            >
                              Approve
                            </button>


                            <button
                              type="button"
                              onClick={() =>
                                openAction(
                                  task,
                                  "reject"
                                )
                              }
                            >
                              Reject
                            </button>

                          </>

                        )}


                        {/* ===============================
                            MANAGER OVERRIDE
                            =============================== */}

                        {canOverride(task) && (

                          <button
                            type="button"
                            onClick={() =>
                              openAction(
                                task,
                                "override"
                              )
                            }
                          >
                            Manager Override
                          </button>

                        )}


                        {!canViewRisk &&
                          !canDecide(task) &&
                          !canOverride(task) &&
                          "—"}

                      </div>

                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

        </div>

      )}


      {/* =====================================================
          RISK ASSESSMENT DETAILS
          ===================================================== */}

      {riskTask &&
        canViewRisk && (

        <div className="card">

          <h2>
            Risk Assessment Details
          </h2>


          <p>
            <strong>
              Payment ID:
            </strong>{" "}
            {riskTask.paymentId}
          </p>


          <p>
            <strong>
              Approval Task ID:
            </strong>{" "}
            {riskTask.id}
          </p>


          {riskLoading ? (

            <p>
              Loading risk assessment...
            </p>

          ) : riskError ? (

            <p
              role="alert"
              className="approval-error"
            >
              {riskError}
            </p>

          ) : !riskAssessment ? (

            <p>
              No risk assessment available.
            </p>

          ) : (

            <>

              <p>
                <strong>
                  Assessment ID:
                </strong>{" "}
                {
                  riskAssessment.assessmentId ??
                  "—"
                }
              </p>


              <p>
                <strong>
                  Assessment Status:
                </strong>{" "}
                {
                  riskAssessment.status ??
                  "—"
                }
              </p>


              <p>
                <strong>
                  Risk Score:
                </strong>{" "}
                {
                  riskAssessment.riskScore ??
                  "—"
                }
              </p>


              <p>
                <strong>
                  Risk Band:
                </strong>{" "}
                {
                  riskAssessment.riskBand ??
                  "—"
                }
              </p>


              <p>
                <strong>
                  Risk Approval Level:
                </strong>{" "}
                {formatApprovalLevel(
                  riskAssessment
                    .riskApprovalLevel
                )}
              </p>


              <p>
                <strong>
                  Configuration Version ID:
                </strong>{" "}
                {
                  riskAssessment
                    .configurationVersionId ??
                  "—"
                }
              </p>


              <p>
                <strong>
                  Completed At:
                </strong>{" "}
                {formatDateTime(
                  riskAssessment.completedAt
                )}
              </p>


              {/* ===========================================
                  RISK FACTOR BREAKDOWN
                  =========================================== */}

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

                    {
                      !riskAssessment.factors ||
                      riskAssessment
                        .factors
                        .length === 0
                        ? (

                          <tr>

                            <td colSpan={5}>
                              No risk factors available.
                            </td>

                          </tr>

                        )
                        : (

                          riskAssessment
                            .factors
                            .map(
                              (
                                factor,
                                index
                              ) => (

                                <tr
                                  key={
                                    factor.ruleCode ??
                                    index
                                  }
                                >

                                  <td>
                                    {
                                      factor.ruleCode ??
                                      "—"
                                    }
                                  </td>


                                  <td>
                                    {
                                      factor.triggered
                                        ? "Yes"
                                        : "No"
                                    }
                                  </td>


                                  <td>
                                    {
                                      factor
                                        .configuredWeight ??
                                      "—"
                                    }
                                  </td>


                                  <td>
                                    {
                                      factor
                                        .contribution ??
                                      "—"
                                    }
                                  </td>


                                  <td>
                                    {
                                      factor
                                        .explanation ??
                                      "—"
                                    }
                                  </td>

                                </tr>

                              )
                            )

                        )
                    }

                  </tbody>

                </table>

              </div>

            </>

          )}


          {/* ===============================================
              RISK BUTTONS
              =============================================== */}

          <div className="approval-actions">

            <button
              type="button"
              onClick={() =>
                viewRisk(riskTask)
              }
              disabled={riskLoading}
            >
              {
                riskLoading
                  ? "Refreshing..."
                  : "Refresh Risk"
              }
            </button>


            <button
              type="button"
              onClick={closeRisk}
              disabled={riskLoading}
            >
              Close Risk
            </button>

          </div>

        </div>

      )}


      {/* =====================================================
          APPROVAL DECISION FORM
          ===================================================== */}

      {selectedTask && (

        <form
          className="approval-decision-form"
          onSubmit={submitDecision}
        >

          <h2>

            {
              action === "approve"
                ? "Approve Payment"
                : action === "reject"
                  ? "Reject Payment"
                  : "Manager Override"
            }

          </h2>


          <p>
            Task #{selectedTask.id}
            {" — "}
            Payment #
            {selectedTask.paymentId}
          </p>


          {/* ===============================================
              REVIEW RISK BEFORE DECISION
              =============================================== */}

          {canViewRisk && (

            <button
              type="button"
              onClick={() =>
                viewRisk(
                  selectedTask
                )
              }
              disabled={riskLoading}
            >
              Review Risk Assessment
            </button>

          )}


          <label htmlFor="approval-remark">

            Remark

            {
              action !== "approve"
                ? " (required)"
                : ""
            }

          </label>


          <textarea
            id="approval-remark"
            value={remark}
            onChange={
              event =>
                setRemark(
                  event.target.value
                )
            }
            maxLength={2000}
            rows={3}
            required={
              action !== "approve"
            }
            placeholder="Enter your decision notes"
          />


          <div className="approval-actions">

            <button
              type="submit"
              disabled={submitting}
            >

              {
                submitting
                  ? "Submitting..."
                  : "Confirm Decision"
              }

            </button>


            <button
              type="button"
              disabled={submitting}
              onClick={closeAction}
            >
              Cancel
            </button>

          </div>

        </form>

      )}

    </section>
  );
}