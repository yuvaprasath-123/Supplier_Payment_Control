import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";

import {
  getCurrentUser,
  login as loginRequest,
  verifyOtp as verifyOtpRequest
} from "../api/authApi";

const AuthContext =
  createContext(null);

const TOKEN_KEY =
  "accessToken";

const MFA_CHALLENGE_KEY =
  "mfaChallengeId";

const MFA_USERNAME_KEY =
  "mfaUsername";

export function AuthProvider({
  children
}) {

  const [
    token,
    setToken
  ] = useState(
    () =>
      sessionStorage.getItem(
        TOKEN_KEY
      )
  );

  const [
    user,
    setUser
  ] = useState(null);

  const [
    loading,
    setLoading
  ] = useState(true);

  async function loadCurrentUser(
    currentToken
  ) {

    if (!currentToken) {

      setUser(null);
      setLoading(false);

      return;
    }

    try {

      const currentUser =
        await getCurrentUser(
          currentToken
        );

      setUser(
        currentUser
      );

    } catch (error) {

      sessionStorage.removeItem(
        TOKEN_KEY
      );

      setToken(null);
      setUser(null);

    } finally {

      setLoading(false);
    }
  }

  useEffect(
    () => {

      loadCurrentUser(
        token
      );

    },
    [token]
  );

  async function login(
    username,
    password
  ) {

    const response =
      await loginRequest(
        username,
        password
      );

    if (
      !response.mfaRequired ||
      !response.challengeId
    ) {

      throw new Error(
        "MFA challenge was not returned by the server."
      );
    }

    sessionStorage.setItem(
      MFA_CHALLENGE_KEY,
      response.challengeId
    );

    sessionStorage.setItem(
      MFA_USERNAME_KEY,
      username
    );

    return response;
  }

  async function verifyOtp(
    otp
  ) {

    const challengeId =
      sessionStorage.getItem(
        MFA_CHALLENGE_KEY
      );

    if (!challengeId) {

      throw new Error(
        "MFA challenge is missing. Please login again."
      );
    }

    const response =
      await verifyOtpRequest(
        challengeId,
        otp
      );

    const accessToken =
      response.accessToken;

    if (!accessToken) {

      throw new Error(
        "Authentication token was not returned by the server."
      );
    }

    sessionStorage.setItem(
      TOKEN_KEY,
      accessToken
    );

    sessionStorage.removeItem(
      MFA_CHALLENGE_KEY
    );

    sessionStorage.removeItem(
      MFA_USERNAME_KEY
    );

    setToken(
      accessToken
    );

    try {

      const currentUser =
        await getCurrentUser(
          accessToken
        );

      setUser(
        currentUser
      );

      return currentUser;

    } catch (error) {

      sessionStorage.removeItem(
        TOKEN_KEY
      );

      setToken(null);
      setUser(null);

      throw error;
    }
  }

  function cancelMfa() {

    sessionStorage.removeItem(
      MFA_CHALLENGE_KEY
    );

    sessionStorage.removeItem(
      MFA_USERNAME_KEY
    );
  }

  function getPendingMfaUsername() {

    return sessionStorage.getItem(
      MFA_USERNAME_KEY
    );
  }

  function hasPendingMfaChallenge() {

    return Boolean(
      sessionStorage.getItem(
        MFA_CHALLENGE_KEY
      )
    );
  }

  function logout() {

    sessionStorage.removeItem(
      TOKEN_KEY
    );

    sessionStorage.removeItem(
      MFA_CHALLENGE_KEY
    );

    sessionStorage.removeItem(
      MFA_USERNAME_KEY
    );

    setToken(null);
    setUser(null);
  }

  function hasRole(
    role
  ) {

    if (
      !user ||
      !Array.isArray(
        user.roles
      )
    ) {

      return false;
    }

    return user.roles.includes(
      role
    );
  }

  const value =
    useMemo(
      () => ({
        token,
        user,
        loading,
        login,
        verifyOtp,
        cancelMfa,
        getPendingMfaUsername,
        hasPendingMfaChallenge,
        logout,
        hasRole,
        isAuthenticated:
          Boolean(
            token &&
            user
          )
      }),
      [
        token,
        user,
        loading
      ]
    );

  return (
    <AuthContext.Provider
      value={
        value
      }
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {

  const context =
    useContext(
      AuthContext
    );

  if (!context) {

    throw new Error(
      "useAuth must be used inside AuthProvider"
    );
  }

  return context;
}