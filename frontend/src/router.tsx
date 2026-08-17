import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/layouts/AppLayout";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { AiAssistantPage } from "@/pages/AiAssistantPage";
import { InvoicesPage } from "@/pages/InvoicesPage";
import { InvoiceDetailPage } from "@/pages/InvoiceDetailPage";
import { VendorsPage } from "@/pages/VendorsPage";
import { UsersPage } from "@/pages/UsersPage";
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
          { path: "invoices/:id", element: <InvoiceDetailPage /> },
          { path: "vendors", element: <VendorsPage /> },
          { path: "ai-assistant", element: <AiAssistantPage /> },
          { path: "users", element: <UsersPage /> },
          { path: "*", element: <Navigate to="/dashboard" replace /> },
        ],
      },
    ],
  },
]);
