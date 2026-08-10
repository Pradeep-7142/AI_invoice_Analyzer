import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/layouts/AppLayout";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { AnalyticsPage } from "@/pages/AnalyticsPage";
import { InvoicesPage } from "@/pages/InvoicesPage";
import { InvoiceDetailPage } from "@/pages/InvoiceDetailPage";
import { VendorsPage } from "@/pages/VendorsPage";
import { PaymentsPage } from "@/pages/PaymentsPage";
import { BudgetsPage } from "@/pages/BudgetsPage";
import { OrganizationUsersPage } from "@/pages/settings/OrganizationUsersPage";
import { FinanceSettingsPage } from "@/pages/settings/FinanceSettingsPage";
import { ProtectedRoute } from "@/features/auth/ProtectedRoute";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: "dashboard", element: <DashboardPage /> },
          { path: "invoices", element: <InvoicesPage /> },
          { path: "invoices/:invoiceId", element: <InvoiceDetailPage /> },
          { path: "vendors", element: <VendorsPage /> },
          { path: "payments", element: <PaymentsPage /> },
          { path: "budgets", element: <BudgetsPage /> },
          { path: "analytics", element: <AnalyticsPage /> },
          { path: "settings/users", element: <OrganizationUsersPage /> },
          { path: "settings/finance", element: <FinanceSettingsPage /> },
        ],
      },
    ],
  },
]);
