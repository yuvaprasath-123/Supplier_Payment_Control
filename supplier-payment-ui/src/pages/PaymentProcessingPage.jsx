
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import {
  getPaymentProcessingStatus
} from "../api/paymentProcessingApi";

function display(value) {
  return value === null || value === undefined || value === ""
    ? "—"
    : String(value);
}

export default function PaymentProcessingPage() {
  const { token } = useAuth();

  const [paymentId, setPaymentId] = useState("210");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSearch(event) {
    event.preventDefault();

    const id = paymentId.trim();

    if (!/^[1-9]\d*$/.test(id)) {
      setError("Enter a valid positive Payment ID.");
      setResult(null);
      return;
    }

    setLoading(true);
    setError("");
    setResult(null);

    try {
      const data = await getPaymentProcessingStatus(token, id);
      setResult(data);
    } catch (err) {
      setError(err.message || "Unable to retrieve processing status.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="page">
      <h1>Payment Processing</h1>

      <p>
        View payment processing results and Stripe
        transaction references.
      </p>

      <form
        className="card"
        onSubmit={handleSearch}
        style={{
          display: "flex",
          gap: "16px",
          alignItems: "end",
          flexWrap: "wrap"
        }}
      >
        <div>
          <label htmlFor="processing-payment-id">
            Payment ID
          </label>

          <input
            id="processing-payment-id"
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
          {loading ? "Loading..." : "Check Status"}
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      {result && (
        <div className="card">
          <h2>Processing Details</h2>

          <p>
            <strong>Payment ID:</strong>{" "}
            {display(result.paymentId)}
          </p>

          <p>
            <strong>Payment Status:</strong>{" "}
            {display(result.paymentStatus)}
          </p>

          <p>
            <strong>Processing Status:</strong>{" "}
            {display(result.processingStatus)}
          </p>

          <p>
            <strong>Attempt Count:</strong>{" "}
            {display(result.attemptCount)}
          </p>

          <p>
            <strong>Stripe PaymentIntent:</strong>{" "}
            {display(result.stripePaymentIntentId)}
          </p>

          {result.lastError && (
            <p role="alert">
              <strong>Processing Issue:</strong>{" "}
              {result.lastError}
            </p>
          )}

          {!result.processingStatus && (
            <p>
              No payment-processing attempt has been
              recorded yet.
            </p>
          )}
        </div>
      )}
    </section>
  );
}
