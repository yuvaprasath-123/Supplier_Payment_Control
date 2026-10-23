import {
  BrowserRouter,
  Navigate,
  Route,
  Routes
} from "react-router";

import { AuthProvider } from "./auth/AuthContext";
import { ThemeProvider } from "./theme/ThemeContext";

import ProtectedRoute from "./auth/ProtectedRoute";
import Navbar from "./components/Navbar";

import LoginPage from "./pages/LoginPage";
import OtpVerificationPage from "./pages/OtpVerificationPage";
import DashboardPage from "./pages/DashboardPage";

import SuppliersPage from "./pages/SuppliersPage";
import InvoicesPage from "./pages/InvoicesPage";
import PaymentsPage from "./pages/PaymentsPage";
import PaymentDetailsPage from "./pages/PaymentDetailsPage";

import PaymentSubmissionSuccessPage
  from "./pages/PaymentSubmissionSuccessPage";

import PaymentSubmissionFailurePage
  from "./pages/PaymentSubmissionFailurePage";

import CreateRequesterPage
  from "./pages/CreateRequesterPage";

import BusinessConfigurationPage
  from "./pages/BusinessConfigurationPage";

import ApprovalsPage from "./pages/ApprovalsPage";

import PaymentAuditTimelinePage
  from "./pages/PaymentAuditTimelinePage";

import FinanceManagerAuditPage
  from "./pages/FinanceManagerAuditPage";

import FinanceManagerAuditDetailsPage
  from "./pages/FinanceManagerAuditDetailsPage";

import PaymentProcessingPage
  from "./pages/PaymentProcessingPage";

const ALL_ROLES = [
  "PAYMENT_REQUESTER",
  "FINANCE_APPROVER",
  "FINANCE_MANAGER"
];

const REQUESTER_ROLES = [
  "PAYMENT_REQUESTER"
];

const APPROVAL_ROLES = [
  "FINANCE_APPROVER",
  "FINANCE_MANAGER"
];

const MANAGER_ROLES = [
  "FINANCE_MANAGER"
];

function AppRoutes() {
  return (
    <>
      <Navbar />

      <main className="app-main">
        <Routes>
          {/* Authentication */}

          <Route
            path="/login"
            element={<LoginPage />}
          />

          <Route
            path="/verify-otp"
            element={<OtpVerificationPage />}
          />

          {/* Dashboard */}

          <Route
            path="/"
            element={
              <ProtectedRoute allowedRoles={ALL_ROLES}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />

          {/* Requester: Suppliers */}

          <Route
            path="/suppliers"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <SuppliersPage />
              </ProtectedRoute>
            }
          />

          {/* Requester: Invoices */}

          <Route
            path="/invoices"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <InvoicesPage />
              </ProtectedRoute>
            }
          />

          {/* Requester: Payments */}

          <Route
            path="/payments"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <PaymentsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/payments/:paymentId"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <PaymentDetailsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/payments/:paymentId/submission-success"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <PaymentSubmissionSuccessPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/payments/:paymentId/submission-failure"
            element={
              <ProtectedRoute allowedRoles={REQUESTER_ROLES}>
                <PaymentSubmissionFailurePage />
              </ProtectedRoute>
            }
          />

          {/* Finance Approval */}

          <Route
            path="/approvals"
            element={
              <ProtectedRoute allowedRoles={APPROVAL_ROLES}>
                <ApprovalsPage />
              </ProtectedRoute>
            }
          />

          {/* User Management */}

          <Route
            path="/users/create-requester"
            element={
              <ProtectedRoute allowedRoles={APPROVAL_ROLES}>
                <CreateRequesterPage />
              </ProtectedRoute>
            }
          />

          {/* Finance Manager: Configuration */}

          <Route
            path="/business-configuration"
            element={
              <ProtectedRoute allowedRoles={MANAGER_ROLES}>
                <BusinessConfigurationPage />
              </ProtectedRoute>
            }
          />

          {/* Finance Manager: Existing Audit Timeline */}

          <Route
            path="/audit"
            element={
              <ProtectedRoute allowedRoles={MANAGER_ROLES}>
                <PaymentAuditTimelinePage />
              </ProtectedRoute>
            }
          />

          {/* Finance Manager: Payment Audit */}

          <Route
            path="/audit/payments"
            element={
              <ProtectedRoute allowedRoles={MANAGER_ROLES}>
                <FinanceManagerAuditPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/audit/payments/:paymentId"
            element={
              <ProtectedRoute allowedRoles={MANAGER_ROLES}>
                <FinanceManagerAuditDetailsPage />
              </ProtectedRoute>
            }
          />

          {/* Finance Approval: Payment Processing */}

          <Route
            path="/payment-processing"
            element={
              <ProtectedRoute allowedRoles={APPROVAL_ROLES}>
                <PaymentProcessingPage />
              </ProtectedRoute>
            }
          />

          {/* Unknown routes */}

          <Route
            path="*"
            element={<Navigate to="/" replace />}
          />
        </Routes>
      </main>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}