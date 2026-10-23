import {
  Link,
  useLocation,
  useParams
} from "react-router";

function PaymentSubmissionFailurePage() {

  const {
    paymentId
  } = useParams();

  const location =
    useLocation();

  const message =
    location.state?.message
    ||
    "Payment submission failed.";

  return (
    <div className="page">

      <h1>
        Payment Submission Failed
      </h1>

      <div className="card">

        <p className="error">
          {message}
        </p>

        <p>
          The payment request was not
          successfully submitted.
        </p>

        <div className="form-row">

          <Link
            to={`/payments/${paymentId}`}
            className="button-link"
          >
            Return to Payment
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

export default PaymentSubmissionFailurePage;