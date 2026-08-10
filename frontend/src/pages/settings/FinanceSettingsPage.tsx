import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Box, Button, Paper, Skeleton, Stack, TextField, Typography } from "@mui/material";
import { financeSettingsApi } from "@/api/financeSettingsApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

export function FinanceSettingsPage() {
  const { role } = useAuth();
  const isAdmin = role === "ORGANIZATION_ADMIN";
  const queryClient = useQueryClient();
  const [managerThreshold, setManagerThreshold] = useState("");
  const [adminThreshold, setAdminThreshold] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const settingsQuery = useQuery({
    queryKey: ["finance-settings"],
    queryFn: financeSettingsApi.get,
    enabled: isAdmin,
  });

  useEffect(() => {
    const settings = settingsQuery.data;
    if (!settings) return;
    setManagerThreshold(settings.managerApprovalThreshold != null ? String(settings.managerApprovalThreshold) : "");
    setAdminThreshold(settings.adminApprovalThreshold != null ? String(settings.adminApprovalThreshold) : "");
  }, [settingsQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      financeSettingsApi.update({
        managerApprovalThreshold: managerThreshold.trim() === "" ? null : Number(managerThreshold),
        adminApprovalThreshold: adminThreshold.trim() === "" ? null : Number(adminThreshold),
      }),
    onSuccess: (settings) => {
      queryClient.setQueryData(["finance-settings"], settings);
      setActionError(null);
      setSaved(true);
    },
    onError: (error) => {
      setActionError(apiErrorMessage(error));
      setSaved(false);
    },
  });

  if (!isAdmin) {
    return (
      <Box>
        <Typography variant="h4" fontWeight={700}>
          Finance Settings
        </Typography>
        <Alert severity="info" sx={{ mt: 2 }}>
          Only organization administrators can configure approval thresholds.
        </Alert>
      </Box>
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" fontWeight={700}>
          Finance Settings
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Invoices at or above these amounts need a human approval before they can be paid.
        </Typography>
      </Box>

      {actionError && <Alert severity="error" onClose={() => setActionError(null)}>{actionError}</Alert>}
      {saved && <Alert severity="success" onClose={() => setSaved(false)}>Settings saved.</Alert>}

      <Paper variant="outlined" sx={{ p: 3, maxWidth: 480 }}>
        {settingsQuery.isLoading ? (
          <Skeleton variant="rectangular" height={120} />
        ) : (
          <Stack spacing={2}>
            <TextField
              label="Finance manager approval threshold"
              type="number"
              fullWidth
              helperText="Invoices at or above this amount need a finance manager or admin's approval. Leave blank for no threshold."
              value={managerThreshold}
              onChange={(e) => setManagerThreshold(e.target.value)}
            />
            <TextField
              label="Organization admin approval threshold"
              type="number"
              fullWidth
              helperText="Invoices at or above this amount need an organization admin's approval specifically. Leave blank for no threshold."
              value={adminThreshold}
              onChange={(e) => setAdminThreshold(e.target.value)}
            />
            <Box>
              <Button variant="contained" onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
                Save
              </Button>
            </Box>
          </Stack>
        )}
      </Paper>
    </Stack>
  );
}
