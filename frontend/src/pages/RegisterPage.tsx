import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Link, Stack, TextField, Typography } from "@mui/material";
import { useAuth } from "@/features/auth/AuthContext";
import { registerSchema, type RegisterFormValues } from "@/features/auth/schemas";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

export function RegisterPage() {
  const { register: registerOrganization } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterFormValues) => {
    setServerError(null);
    try {
      await registerOrganization(values);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setServerError(apiErrorMessage(error, "Unable to create your organization. Please try again."));
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
        py: 4,
      }}
    >
      <Card sx={{ width: 420 }}>
        <CardContent>
          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack spacing={2}>
              <Typography variant="h5" fontWeight={700} color="primary">
                Create your organization
              </Typography>
              <Typography variant="body2" color="text.secondary">
                You&apos;ll be set up as the organization administrator.
              </Typography>

              {serverError && <Alert severity="error">{serverError}</Alert>}

              <TextField
                label="Organization name"
                fullWidth
                error={!!errors.organizationName}
                helperText={errors.organizationName?.message}
                {...register("organizationName")}
              />
              <TextField
                label="Your full name"
                fullWidth
                autoComplete="name"
                error={!!errors.fullName}
                helperText={errors.fullName?.message}
                {...register("fullName")}
              />
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
                autoComplete="new-password"
                error={!!errors.password}
                helperText={errors.password?.message ?? "At least 8 characters."}
                {...register("password")}
              />
              <Button type="submit" variant="contained" fullWidth disabled={isSubmitting}>
                {isSubmitting ? "Creating..." : "Create organization"}
              </Button>
              <Typography variant="body2" color="text.secondary" align="center">
                Already have an account? <Link component={RouterLink} to="/login">Sign in</Link>
              </Typography>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
