import {
  Link,
  useParams
} from "react-router";

import {
  useEffect,
  useState
} from "react";

function PaymentSubmissionSuccessPage() {

  const {
    paymentId
  } = useParams();

  const [
    payment,
    setPayment
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

      async function loadPayment() {

        try {

          const token =
            sessionStorage.getItem(
              "accessToken"
            );

          const response =
            await fetch(
              `/payment-api/api/v1/payment-requests/${paymentId}`,
              {
                headers: {
                  Authorization:
                    `Bearer ${token}`
                }
              }
            );

          if (!response.ok) {

            throw new Error(
              "Unable to load submitted payment."
            );
          }

          const data =
            await response.json();

          setPayment(
            data
          );

        } catch (err) {

          setError(
            err.message
          );

        } finally {

          setLoading(
            false
          );
        }
      }

      loadPayment();

    },
    [
      paymentId
    ]
  );

  if (loading) {

    return (
      <div className="page">
        <h1>
          Payment Submission
        </h1>

        <div className="card">
          Loading payment details...
        </div>
      </div>
    );
  }

  if (error) {

    return (
      <div className="page">
        <h1>
          Payment Submission
        </h1>

        <div className="card">
          <p className="error">
            {error}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="page">

      <h1>
        Payment Submitted Successfully
      </h1>

      <div className="card">

        <h2>
          Submission Details
        </h2>

        <p>
          Your payment request was submitted
          successfully and has been sent for
          validation.
        </p>

        <div className="submission-details">

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
              Payment Amount:
            </strong>{" "}
            {payment.paymentAmount}
          </p>

          <p>
            <strong>
              Status:
            </strong>{" "}

            <span className="status">
              {payment.status}
            </span>
          </p>

          <p>
            <strong>
              Submitted By:
            </strong>{" "}
            {payment.requesterId}
          </p>

          <p>
            <strong>
              Submitted At:
            </strong>{" "}
            {payment.submittedAt}
          </p>

        </div>

        <div className="form-row">

          <Link
            to={`/payments/${payment.paymentId}`}
            className="button-link"
          >
            View Payment
          </Link>

          <Link
            to="/payments"
            className="button-link"
          >
            Back to Payments
          </Link>

        </div>

      </div>

    </div>
  );
}

export default PaymentSubmissionSuccessPage;