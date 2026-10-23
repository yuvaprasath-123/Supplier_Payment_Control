import {
  NavLink,
  useNavigate
} from "react-router";

import { useTranslation } from "react-i18next";

import { useAuth } from "../auth/AuthContext";
import { useTheme } from "../theme/ThemeContext";

export default function Navbar() {
  const { t } = useTranslation();

  const {
    user,
    logout,
    hasRole,
    isAuthenticated
  } = useAuth();

  const {
    theme,
    toggleTheme
  } = useTheme();

  const navigate = useNavigate();

  if (!isAuthenticated) {
    return null;
  }

  const isRequester =
    hasRole("PAYMENT_REQUESTER");

  const isFinanceApprover =
    hasRole("FINANCE_APPROVER");

  const isFinanceManager =
    hasRole("FINANCE_MANAGER");

  const canViewApprovals =
    isFinanceApprover ||
    isFinanceManager;

  const canCreateRequester =
    isFinanceApprover ||
    isFinanceManager;

  const navigationItems = [
    {
      path: "/",
      label: t("nav.dashboard"),
      visible: true
    },
    {
      path: "/suppliers",
      label: t("nav.suppliers"),
      visible: isRequester
    },
    {
      path: "/invoices",
      label: t("nav.invoices"),
      visible: isRequester
    },
    {
      path: "/payments",
      label: t("nav.payments"),
      visible: isRequester
    },
    {
      path: "/approvals",
      label: "Approvals",
      visible: canViewApprovals
    },
    {
      path: "/payment-processing",
      label: "Payment Processing",
      visible: isFinanceManager
    },
    {
      path: "/audit/payments",
      label: "Payment Audit",
      visible: isFinanceManager
    },
    {
      path: "/audit",
      label: "Audit Timeline",
      visible: isFinanceManager
    },
    {
      path: "/business-configuration",
      label: "Business Configuration",
      visible: isFinanceManager
    },
    {
      path: "/users/create-requester",
      label: "Create Requester",
      visible: canCreateRequester
    }
  ];

  const visibleItems =
    navigationItems.filter(
      (item) => item.visible
    );

  function handleLogout() {
    logout();

    navigate(
      "/login",
      {
        replace: true
      }
    );
  }

  return (
    <header className="navbar">
      <div className="navbar-left">
        <NavLink
          to="/"
          className="navbar-brand"
        >
          {t("appName")}
        </NavLink>

        <nav
          className="navbar-links"
          aria-label="Main navigation"
        >
          {visibleItems.map(
            (item) => (
              <NavLink
                key={item.path}
                to={item.path}
                end={
                  item.path === "/" ||
                  item.path === "/audit"
                }
                className={({
                  isActive
                }) =>
                  isActive
                    ? "nav-link active"
                    : "nav-link"
                }
                aria-label={
                  item.label
                }
              >
                {item.label}
              </NavLink>
            )
          )}
        </nav>
      </div>

      <div className="navbar-right">
        <div className="navbar-user">
          {user?.displayName ||
            user?.username ||
            user?.userId ||
            ""}
        </div>

        <button
          type="button"
          className="theme-toggle"
          onClick={toggleTheme}
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
          {theme === "dark"
            ? "☀ Light"
            : "🌙 Dark"}
        </button>

        <button
          type="button"
          className="logout-button"
          onClick={handleLogout}
        >
          {t("nav.logout")}
        </button>
      </div>
    </header>
  );
}