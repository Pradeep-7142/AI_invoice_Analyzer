import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Link, Stack, TextField, Typography } from "@mui/material";
import { useAuth } from "@/features/auth/AuthContext";
import { loginSchema, type LoginFormValues } from "@/features/auth/schemas";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
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

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "background.default",
      }}
    >
      <Card sx={{ width: 380 }}>
        <CardContent>
          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack spacing={2}>
              <Typography variant="h5" fontWeight={700} color="primary">
                InvoiceIQ
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Sign in to your organization workspace.
              </Typography>

              {serverError && <Alert severity="error">{serverError}</Alert>}

              <TextField
                label="Email"
                type="email"
                fullWidth
                autoComplete="email"
                error={!!errors.email}
                helperText={errors.email?.message}
                {...register("email")}
              />
              <TextField
                label="Password"
                type="password"
                fullWidth
                autoComplete="current-password"
                error={!!errors.password}
                helperText={errors.password?.message}
                {...register("password")}
              />
              <Button type="submit" variant="contained" fullWidth disabled={isSubmitting}>
                {isSubmitting ? "Signing in..." : "Sign in"}
              </Button>
              <Typography variant="body2" color="text.secondary" align="center">
                Don&apos;t have an organization yet?{" "}
                <Link component={RouterLink} to="/register">
                  Create one
                </Link>
              </Typography>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
