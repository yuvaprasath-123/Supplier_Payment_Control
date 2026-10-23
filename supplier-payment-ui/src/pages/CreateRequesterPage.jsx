import {
  useState
} from "react";

import {
  useNavigate
} from "react-router";

import {
  createRequester
} from "../api/userProvisioningApi";

import {
  useAuth
} from "../auth/AuthContext";

import ErrorMessage
  from "../components/ErrorMessage";

export default function CreateRequesterPage() {

  const {
    token
  } = useAuth();

  const navigate =
    useNavigate();

  const [
    username,
    setUsername
  ] = useState("");

  const [
    password,
    setPassword
  ] = useState("");

  const [
    fullName,
    setFullName
  ] = useState("");

  const [
    email,
    setEmail
  ] = useState("");

  const [
    error,
    setError
  ] = useState("");

  const [
    success,
    setSuccess
  ] = useState("");

  const [
    submitting,
    setSubmitting
  ] = useState(false);

  async function handleSubmit(
    event
  ) {

    event.preventDefault();

    if (submitting) {
      return;
    }

    setSubmitting(
      true
    );

    setError("");
    setSuccess("");

    try {

      const user =
        await createRequester(
          token,
          {
            username,
            password,
            fullName,
            email
          }
        );

      setSuccess(
        `Requester ${user.username} created successfully.`
      );

      setUsername("");
      setPassword("");
      setFullName("");
      setEmail("");

    } catch (error) {

      setError(
        error.message
      );

    } finally {

      setSubmitting(
        false
      );
    }
  }

  return (
    <div className="page">

      <h1>
        Create Payment Requester
      </h1>

      <ErrorMessage
        message={
          error
        }
      />

      {
        success &&
        (
          <div className="card">

            <p>
              {success}
            </p>

          </div>
        )
      }

      <form
        className="card"
        onSubmit={
          handleSubmit
        }
      >

        <label>
          Full Name
        </label>

        <input
          type="text"
          value={
            fullName
          }
          onChange={
            event =>
              setFullName(
                event.target.value
              )
          }
          required
        />

        <label>
          Email
        </label>

        <input
          type="email"
          value={
            email
          }
          onChange={
            event =>
              setEmail(
                event.target.value
              )
          }
          required
        />

        <label>
          Username
        </label>

        <input
          type="text"
          value={
            username
          }
          onChange={
            event =>
              setUsername(
                event.target.value
              )
          }
          required
        />

        <label>
          Temporary Password
        </label>

        <input
          type="password"
          value={
            password
          }
          onChange={
            event =>
              setPassword(
                event.target.value
              )
          }
          minLength="8"
          required
        />

        <div className="form-row">

          <button
            type="submit"
            disabled={
              submitting
            }
          >
            {
              submitting
                ? "Creating..."
                : "Create Requester"
            }
          </button>

          <button
            type="button"
            onClick={
              () =>
                navigate("/")
            }
          >
            Cancel
          </button>

        </div>

      </form>

    </div>
  );
}