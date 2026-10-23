import {
  useEffect,
  useState
} from "react";

import {
  useNavigate
} from "react-router";

import {
  useAuth
} from "../auth/AuthContext";

import {
  useTheme
} from "../theme/ThemeContext";


export default function OtpVerificationPage() {

  const navigate =
    useNavigate();

  const {
    verifyOtp,
    cancelMfa,
    getPendingMfaUsername,
    hasPendingMfaChallenge
  } = useAuth();

  const {
    theme,
    toggleTheme
  } = useTheme();

  const [
    otp,
    setOtp
  ] = useState("");

  const [
    error,
    setError
  ] = useState("");

  const [
    loading,
    setLoading
  ] = useState(false);

  const username =
    getPendingMfaUsername();


  useEffect(
    () => {

      if (
        !hasPendingMfaChallenge()
      ) {

        navigate(
          "/login",
          {
            replace: true
          }
        );
      }

    },
    [
      navigate,
      hasPendingMfaChallenge
    ]
  );


  function handleOtpChange(
    event
  ) {

    const value =
      event.target.value
        .replace(
          /\D/g,
          ""
        )
        .slice(
          0,
          6
        );

    setOtp(
      value
    );
  }


  async function handleSubmit(
    event
  ) {

    event.preventDefault();

    if (loading) {
      return;
    }

    if (
      otp.length !== 6
    ) {

      setError(
        "Enter the 6-digit OTP."
      );

      return;
    }

    setError("");
    setLoading(true);

    try {

      await verifyOtp(
        otp
      );

      navigate(
        "/",
        {
          replace: true
        }
      );

    } catch (err) {

      setError(
        err.message ||
        "OTP verification failed."
      );

    } finally {

      setLoading(false);
    }
  }


  function handleBackToLogin() {

    cancelMfa();

    navigate(
      "/login",
      {
        replace: true
      }
    );
  }


  return (
    <div className="login-page">

      <div className="login-card">

        <div className="login-toolbar">

          <button
            type="button"
            className="theme-toggle"
            onClick={
              toggleTheme
            }
            aria-label={
              theme === "dark"
                ? "Switch to light mode"
                : "Switch to dark mode"
            }
            title={
              theme === "dark"
                ? "Switch to light mode"
                : "Switch to dark mode"
            }
          >
            {
              theme === "dark"
                ? "☀ Light"
                : "🌙 Dark"
            }
          </button>

        </div>


        <h1>
          Verify OTP
        </h1>


        <p>
          Multi-factor authentication is required.
        </p>


        {
          username &&
          (
            <p>
              Signing in as{" "}
              <strong>
                {username}
              </strong>
            </p>
          )
        }


        <p>
          Enter the 6-digit OTP shown in the
          security-service console.
        </p>


        {
          error &&
          (
            <div className="error-message">

              {error}

            </div>
          )
        }


        <form
          onSubmit={
            handleSubmit
          }
        >

          <label
            htmlFor="otp"
          >
            One-Time Password
          </label>


          <input
            id="otp"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength="6"
            value={
              otp
            }
            onChange={
              handleOtpChange
            }
            placeholder="Enter 6-digit OTP"
            required
            disabled={
              loading
            }
          />


          <button
            type="submit"
            disabled={
              loading ||
              otp.length !== 6
            }
          >
            {
              loading
                ? "Verifying..."
                : "Verify OTP"
            }
          </button>


          <button
            type="button"
            onClick={
              handleBackToLogin
            }
            disabled={
              loading
            }
          >
            Back to Login
          </button>

        </form>

      </div>

    </div>
  );
}