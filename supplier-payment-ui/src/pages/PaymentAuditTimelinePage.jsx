
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { getPaymentAuditTimeline } from "../api/auditApi";

function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString();
}

export default function PaymentAuditTimelinePage() {
  const { token } = useAuth();

  const [paymentId, setPaymentId] = useState("3");
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searched, setSearched] = useState(false);

  async function handleSearch(event) {
    event.preventDefault();

    const id = paymentId.trim();

    if (!/^[1-9]\d*$/.test(id)) {
      setError("Enter a valid positive Payment ID.");
      return;
    }

    setLoading(true);
    setError("");
    setEvents([]);
    setSearched(false);

    try {
      const result = await getPaymentAuditTimeline(token, id);

      setEvents(Array.isArray(result) ? result : []);
      setSearched(true);
    } catch (err) {
      setError(
        err.message || "Unable to retrieve payment audit history."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="page">
      <h1>Payment Audit Timeline</h1>

      <p>
        View recorded financial audit events for a payment.
      </p>

      <form
        onSubmit={handleSearch}
        className="card"
        style={{
          display: "flex",
          alignItems: "end",
          flexWrap: "wrap",
          gap: "16px"
        }}
      >
        <div>
          <label htmlFor="audit-payment-id">
            Payment ID
          </label>

          <input
            id="audit-payment-id"
            type="number"
            min="1"
            step="1"
            required
            value={paymentId}
            onChange={(event) =>
              setPaymentId(event.target.value)
            }
          />
        </div>

        <button type="submit" disabled={loading}>
          {loading ? "Loading..." : "View Audit History"}
        </button>
      </form>

      {error && (
        <p role="alert">{error}</p>
      )}

      {searched && events.length === 0 && (
        <div className="card">
          <p>No audit events found for payment {paymentId}.</p>
        </div>
      )}

      {events.length > 0 && (
        <div className="card">
          <h2>Audit Events</h2>

          <div style={{ overflowX: "auto" }}>
            <table>
              <thead>
                <tr>
                  <th>Event ID</th>
                  <th>Event Type</th>
                  <th>Resource</th>
                  <th>Actor</th>
                  <th>Occurred At</th>
                </tr>
              </thead>

              <tbody>
                {events.map((auditEvent) => (
                  <tr key={auditEvent.id}>
                    <td>{auditEvent.id}</td>
                    <td>{auditEvent.eventType}</td>
                    <td>
                      {auditEvent.resourceType} #
                      {auditEvent.resourceId}
                    </td>
                    <td>{auditEvent.actorId ?? "—"}</td>
                    <td>
                      {formatDate(auditEvent.occurredAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
