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
  IconButton,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { useAuth } from "@/features/auth/AuthContext";
import { registerSchema, type RegisterFormValues } from "@/features/auth/schemas";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterFormValues) => {
    setServerError(null);
    try {
      await registerUser(values);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setServerError(apiErrorMessage(error, "Unable to create your account. Please try again."));
    }
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
        py: 4,
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
                    Create Account
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Join InvoiceIQ platform
                  </Typography>
                </Box>
              </Box>

              {serverError && <Alert severity="error">{serverError}</Alert>}

              <TextField
                label="Full name"
                fullWidth
                autoComplete="name"
                error={!!errors.fullName}
                helperText={errors.fullName?.message}
                {...register("fullName")}
              />

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
                autoComplete="new-password"
                error={!!errors.password}
                helperText={errors.password?.message ?? "At least 8 characters."}
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
                {isSubmitting ? "Creating account..." : "Sign Up"}
              </Button>

              <Typography variant="body2" color="text.secondary" align="center" sx={{ pt: 1 }}>
                Already have an account?{" "}
                <Link component={RouterLink} to="/login" fontWeight={600}>
                  Sign in
                </Link>
              </Typography>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
