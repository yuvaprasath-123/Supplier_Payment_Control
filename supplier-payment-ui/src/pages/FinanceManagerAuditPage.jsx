import {
  useEffect,
  useMemo,
  useState
} from "react";

import {
  Link
} from "react-router";

import {
  useAuth
} from "../auth/AuthContext";

import {
  getPayments
} from "../api/paymentApi";

import "./FinanceManagerAudit.css";

export default function FinanceManagerAuditPage() {
  const {
    token
  } = useAuth();

  const [
    payments,
    setPayments
  ] = useState([]);

  const [
    statusFilter,
    setStatusFilter
  ] = useState("");

  const [
    search,
    setSearch
  ] = useState("");

  const [
    loading,
    setLoading
  ] = useState(true);

  const [
    error,
    setError
  ] = useState("");

  async function loadPayments() {
    setLoading(true);
    setError("");

    try {
      const result =
        await getPayments(
          token
        );

      setPayments(
        Array.isArray(result)
          ? result
          : []
      );

    } catch (err) {
      setError(
        err.message ||
        "Unable to load payments for audit."
      );

      setPayments([]);

    } finally {
      setLoading(false);
    }
  }

  useEffect(
    () => {
      loadPayments();
    },
    [token]
  );

  const filteredPayments =
    useMemo(
      () => {
        const normalizedSearch =
          search
            .trim()
            .toLowerCase();

        return payments.filter(
          (payment) => {
            if (
              statusFilter &&
              payment.status !==
                statusFilter
            ) {
              return false;
            }

            if (
              !normalizedSearch
            ) {
              return true;
            }

            return [
              payment.paymentId,
              payment.invoiceId,
              payment.supplierId,
              payment.requesterId,
              payment.status,
              payment.riskBand,
              payment.requiredApprovalLevel
            ]
              .filter(
                (value) =>
                  value != null
              )
              .some(
                (value) =>
                  String(value)
                    .toLowerCase()
                    .includes(
                      normalizedSearch
                    )
              );
          }
        );
      },
      [
        payments,
        statusFilter,
        search
      ]
    );

  function formatAmount(
    value
  ) {
    if (
      value == null
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

  return (
    <section className="audit-payments-page">
      <div className="audit-payments-header">
        <div>
          <h1>
            Finance Manager Payment Audit
          </h1>

          <p>
            Read-only review of supplier
            payment requests, risk results,
            approval decisions and processing
            outcomes.
          </p>
        </div>

        <button
          type="button"
          onClick={
            loadPayments
          }
          disabled={
            loading
          }
        >
          {loading
            ? "Refreshing..."
            : "Refresh"}
        </button>
      </div>

      <div className="audit-payment-filters">
        <label>
          Search
          <input
            type="search"
            value={search}
            onChange={
              (event) =>
                setSearch(
                  event.target.value
                )
            }
            placeholder="Payment, invoice, supplier, requester..."
          />
        </label>

        <label>
          Status
          <select
            value={
              statusFilter
            }
            onChange={
              (event) =>
                setStatusFilter(
                  event.target.value
                )
            }
          >
            <option value="">
              All statuses
            </option>

            <option value="DRAFT">
              Draft
            </option>

            <option value="PENDING_VALIDATION">
              Pending Validation
            </option>

            <option value="PENDING_APPROVAL">
              Pending Approval
            </option>

            <option value="APPROVED">
              Approved
            </option>

            <option value="PROCESSING">
              Processing
            </option>

            <option value="COMPLETE">
              Complete
            </option>

            <option value="REJECTED">
              Rejected
            </option>

            <option value="FAILED">
              Failed
            </option>
          </select>
        </label>
      </div>

      {error && (
        <p
          role="alert"
          className="approval-error"
        >
          {error}
        </p>
      )}

      {loading && (
        <p role="status">
          Loading payment audit records...
        </p>
      )}

      {!loading &&
        !error &&
        filteredPayments.length === 0 && (
          <p>
            No payment audit records found.
          </p>
        )}

      {!loading &&
        filteredPayments.length > 0 && (
          <div className="approvals-table-wrapper">
            <table className="approvals-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Invoice ID</th>
                  <th>Supplier ID</th>
                  <th>Requester ID</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Risk</th>
                  <th>Required Level</th>
                  <th>Audit</th>
                </tr>
              </thead>

              <tbody>
                {filteredPayments.map(
                  (payment) => (
                    <tr
                      key={
                        payment.paymentId
                      }
                    >
                      <td>
                        {payment.paymentId}
                      </td>

                      <td>
                        {payment.invoiceId}
                      </td>

                      <td>
                        {payment.supplierId}
                      </td>

                      <td>
                        {payment.requesterId}
                      </td>

                      <td>
                        {formatAmount(
                          payment.paymentAmount
                        )}
                      </td>

                      <td>
                        {payment.status}
                      </td>

                      <td>
                        {payment.riskBand ??
                          "—"}
                      </td>

                      <td>
                        {payment.requiredApprovalLevel ??
                          "—"}
                      </td>

                      <td>
                        <Link
                          to={
                            `/audit/payments/${payment.paymentId}`
                          }
                        >
                          View Audit
                        </Link>
                      </td>
                    </tr>
                  )
                )}
              </tbody>
            </table>
          </div>
        )}
    </section>
  );
}