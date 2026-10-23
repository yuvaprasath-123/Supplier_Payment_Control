
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";

const ArrowIcon = () => (
  <span aria-hidden="true">→</span>
);

function DashboardCard({
  title,
  description,
  to,
  action,
  icon,
  accent
}) {
  return (
    <article
      className="card"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        minHeight: "190px",
        borderTop: `3px solid ${accent}`
      }}
    >
      <div
        style={{
          fontSize: "28px",
          lineHeight: 1
        }}
        aria-hidden="true"
      >
        {icon}
      </div>

      <h2 style={{ margin: 0 }}>{title}</h2>

      <p
        style={{
          margin: 0,
          flex: 1,
          opacity: 0.85
        }}
      >
        {description}
      </p>

      <Link
        to={to}
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "8px",
          fontWeight: 600,
          textDecoration: "none"
        }}
      >
        {action}
        <ArrowIcon />
      </Link>
    </article>
  );
}

export default function DashboardPage() {
  const { t } = useTranslation();

  const { user, hasRole } = useAuth();

  const isRequester = hasRole("PAYMENT_REQUESTER");
  const isApprover = hasRole("FINANCE_APPROVER");
  const isManager = hasRole("FINANCE_MANAGER");

  const userName =
    user?.displayName ||
    user?.username ||
    user?.userId ||
    "User";

  const roleLabel = isManager
    ? "Finance Manager"
    : isApprover
      ? "Finance Approver"
      : "Payment Requester";

  const cards = [];

  if (isRequester) {
    cards.push(
      {
        title: t("dashboard.suppliers.title", "Suppliers"),
        description: t(
          "dashboard.suppliers.description",
          "Create and manage supplier records."
        ),
        action: t(
          "dashboard.suppliers.action",
          "Manage Suppliers"
        ),
        to: "/suppliers",
        icon: "🏢",
        accent: "#3b82f6"
      },
      {
        title: t("dashboard.invoices.title", "Invoices"),
        description: t(
          "dashboard.invoices.description",
          "Create and view supplier invoices."
        ),
        action: t(
          "dashboard.invoices.action",
          "Manage Invoices"
        ),
        to: "/invoices",
        icon: "📄",
        accent: "#8b5cf6"
      },
      {
        title: t(
          "dashboard.payments.title",
          "Payment Requests"
        ),
        description: t(
          "dashboard.payments.description",
          "Create, review, and submit payments for validation."
        ),
        action: t(
          "dashboard.payments.action",
          "Manage Payments"
        ),
        to: "/payments",
        icon: "💳",
        accent: "#10b981"
      }
    );
  }

  if (isApprover || isManager) {
    cards.push({
      title: "Approval Workflow",
      description:
        "Review payment approval tasks and take permitted approval decisions.",
      action: "View Approvals",
      to: "/approvals",
      icon: "✅",
      accent: "#10b981"
    });

    cards.push({
      title: "Requester Management",
      description:
        "Create Payment Requester accounts for supplier payment operations.",
      action: "Create Requester",
      to: "/users/create-requester",
      icon: "👤",
      accent: "#3b82f6"
    });
  }

  if (isManager) {
    cards.push(
      {
        title: "Payment Processing",
        description:
          "Track completed payments, processing attempts, and Stripe Sandbox transaction references.",
        action: "View Processing",
        to: "/payment-processing",
        icon: "💰",
        accent: "#8b5cf6"
      },
      {
        title: "Audit Timeline",
        description:
          "Review available audit events and payment activity.",
        action: "View Audit Timeline",
        to: "/audit",
        icon: "📋",
        accent: "#f59e0b"
      },
      {
        title: "Business Configuration",
        description:
          "Manage payment-related business configuration and control settings.",
        action: "Open Configuration",
        to: "/business-configuration",
        icon: "⚙️",
        accent: "#06b6d4"
      }
    );
  }

  return (
    <div className="page">
      <h1>
        {t("dashboard.title", "Dashboard")}
      </h1>

      <section
        className="card"
        style={{
          marginBottom: "24px",
          padding: "28px"
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: "16px"
          }}
        >
          <div>
            <p
              style={{
                margin: "0 0 8px",
                fontSize: "13px",
                opacity: 0.75,
                textTransform: "uppercase",
                letterSpacing: "1px"
              }}
            >
              {roleLabel} Workspace
            </p>

            <h2 style={{ margin: "0 0 12px" }}>
              {t("dashboard.welcome", "Welcome")},{" "}
              {userName}
            </h2>

            <p style={{ margin: 0, opacity: 0.85 }}>
              {t(
                "dashboard.subtitle",
                "Supplier Payment Authorization & Control System"
              )}
            </p>
          </div>

          <span
            style={{
              display: "inline-block",
              padding: "10px 16px",
              borderRadius: "999px",
              border: "1px solid currentColor",
              fontSize: "13px",
              fontWeight: 600
            }}
          >
            {roleLabel}
          </span>
        </div>
      </section>

      <section>
        <h2 style={{ marginBottom: "16px" }}>
          Quick Access
        </h2>

        <div className="dashboard-grid">
          {cards.map((card) => (
            <DashboardCard
              key={card.to}
              {...card}
            />
          ))}
        </div>
      </section>

      {isManager && (
        <section
          className="card"
          style={{
            marginTop: "24px"
          }}
        >
          <h2>Payment Processing Overview</h2>

          <p>
            Monitor payment execution and transaction
            references through the Payment Processing module.
          </p>

          <p>
            The Stripe Sandbox demonstration payment
            is available for review.
          </p>

          <Link
            to="/payment-processing"
            style={{ fontWeight: 600 }}
          >
            View Payment Processing →
          </Link>
        </section>
      )}
    </div>
  );
}
