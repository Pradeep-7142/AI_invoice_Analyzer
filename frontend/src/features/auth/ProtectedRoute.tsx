import { Box, CircularProgress } from "@mui/material";
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/features/auth/AuthContext";

export function ProtectedRoute() {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return (
      <Box sx={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
