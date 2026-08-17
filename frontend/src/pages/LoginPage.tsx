import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  InputAdornment,
  IconButton,
  Link,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { useAuth } from "@/features/auth/AuthContext";
import { loginSchema, type LoginFormValues } from "@/features/auth/schemas";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null);
    try {
      await login(values);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setServerError(apiErrorMessage(error, "Unable to sign in. Please check your credentials."));
    }
  };

  const fillDemoUser = (email: string) => {
    setValue("email", email);
    setValue("password", "Password123!");
    setServerError(null);
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "radial-gradient(ellipse at top, #EFF6FF 0%, #F8FAFC 70%)",
        p: 2,
      }}
    >
      <Card
        sx={{
          width: "100%",
          maxWidth: 440,
          p: 1,
          boxShadow: "0 20px 40px rgba(15, 23, 42, 0.08)",
          borderRadius: "16px",
          border: "1px solid #E2E8F0",
        }}
      >
        <CardContent sx={{ p: 3 }}>
          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack spacing={2.5}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1 }}>
                <Box
                  sx={{
                    width: 40,
                    height: 40,
                    borderRadius: "10px",
                    background: "linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#FFF",
                  }}
                >
                  <AutoAwesomeIcon />
                </Box>
                <Box>
                  <Typography variant="h5" fontWeight={700} sx={{ letterSpacing: "-0.02em" }}>
                    InvoiceIQ
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    AI-Powered Invoice Intelligence
                  </Typography>
                </Box>
              </Box>

              <Typography variant="body2" color="text.secondary">
                Sign in to manage and analyze invoices with OCR extraction.
              </Typography>

              {serverError && <Alert severity="error">{serverError}</Alert>}

              <TextField
                label="Email address"
                type="email"
                fullWidth
                autoComplete="email"
                error={!!errors.email}
                helperText={errors.email?.message}
                {...register("email")}
              />

              <TextField
                label="Password"
                type={showPassword ? "text" : "password"}
                fullWidth
                autoComplete="current-password"
                error={!!errors.password}
                helperText={errors.password?.message}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={() => setShowPassword(!showPassword)} edge="end" size="small">
                        {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
                {...register("password")}
              />

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={isSubmitting}
                sx={{ py: 1.3, fontSize: "0.95rem" }}
              >
                {isSubmitting ? "Signing in..." : "Sign in to Dashboard"}
              </Button>

              <Divider sx={{ my: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  DEMO ACCOUNTS
                </Typography>
              </Divider>

              <Stack direction="row" spacing={1.5}>
                <Button
                  variant="outlined"
                  size="small"
                  fullWidth
                  onClick={() => fillDemoUser("admin@invoiceiq.com")}
                  sx={{ borderColor: "#BFDBFE", color: "#1D4ED8", "&:hover": { bgcolor: "#EFF6FF" } }}
                >
                  Fill Admin
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  fullWidth
                  onClick={() => fillDemoUser("employee@invoiceiq.com")}
                  sx={{ borderColor: "#CCFBF1", color: "#0F766E", "&:hover": { bgcolor: "#F0FDFA" } }}
                >
                  Fill Employee
                </Button>
              </Stack>
              <Typography variant="caption" color="text.secondary" align="center">
                Demo Password: <strong>Password123!</strong>
              </Typography>

              <Typography variant="body2" color="text.secondary" align="center" sx={{ pt: 1 }}>
                Need an account?{" "}
                <Link component={RouterLink} to="/register" fontWeight={600}>
                  Create an account
                </Link>
              </Typography>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
