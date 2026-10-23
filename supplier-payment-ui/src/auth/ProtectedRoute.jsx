import {
  Navigate
} from "react-router";

import {
  useAuth
} from "../auth/AuthContext";

export default function ProtectedRoute({
  children,
  allowedRoles = []
}) {

  const {
    user,
    loading,
    isAuthenticated
  } = useAuth();

  if (loading) {

    return (
      <div className="page">

        <p>
          Loading...
        </p>

      </div>
    );
  }

  if (!isAuthenticated) {

    return (
      <Navigate
        to="/login"
        replace
      />
    );
  }

  if (
    allowedRoles.length > 0
  ) {

    const userRoles =
      Array.isArray(
        user?.roles
      )
        ? user.roles
        : [];

    const hasAllowedRole =
      allowedRoles.some(
        role =>
          userRoles.includes(
            role
          )
      );

    if (!hasAllowedRole) {

      return (
        <Navigate
          to="/"
          replace
        />
      );
    }
  }

  return children;
}