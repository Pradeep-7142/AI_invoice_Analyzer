import { useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  LinearProgress,
  Paper,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { budgetsApi, type BudgetPayload } from "@/api/budgetsApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";

const CAN_MANAGE_BUDGETS = new Set(["ORGANIZATION_ADMIN", "FINANCE_MANAGER"]);

const budgetSchema = z.object({
  category: z.string().min(1, "Category is required.").max(100),
  monthlyLimit: z.coerce.number().positive("Monthly limit must be greater than zero."),
});
type BudgetFormValues = z.infer<typeof budgetSchema>;

function shiftMonth(month: string, delta: number): string {
  const parts = month.split("-");
  const year = Number(parts[0]);
  const m = Number(parts[1]);
  const date = new Date(year, m - 1 + delta, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export function BudgetsPage() {
  const { role } = useAuth();
  const canManage = role !== null && CAN_MANAGE_BUDGETS.has(role);
  const queryClient = useQueryClient();
  const [month, setMonth] = useState(currentMonth());
  const [dialogOpen, setDialogOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const statusQuery = useQuery({
    queryKey: ["budget-status", month],
    queryFn: () => budgetsApi.status(month),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<BudgetFormValues>({ resolver: zodResolver(budgetSchema) });

  const createMutation = useMutation({
    mutationFn: (payload: BudgetPayload) => budgetsApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["budget-status"] });
      setDialogOpen(false);
      reset();
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => budgetsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["budget-status"] }),
  });

  const statuses = statusQuery.data ?? [];
  const monthLabel = useMemo(() => {
    const parts = month.split("-");
    const year = Number(parts[0]);
    const m = Number(parts[1]);
    return new Date(year, m - 1, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
  }, [month]);

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Budgets
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Monthly spending caps per vendor category, checked against real invoice totals.
          </Typography>
        </Box>
        {canManage && (
          <Button variant="contained" onClick={() => { setActionError(null); setDialogOpen(true); }}>
            Add budget
          </Button>
        )}
      </Stack>

      <Stack direction="row" spacing={1} alignItems="center">
        <IconButton onClick={() => setMonth(shiftMonth(month, -1))}><ChevronLeftIcon /></IconButton>
        <Typography variant="subtitle1" sx={{ minWidth: 160, textAlign: "center" }}>{monthLabel}</Typography>
        <IconButton onClick={() => setMonth(shiftMonth(month, 1))}><ChevronRightIcon /></IconButton>
      </Stack>

      {statusQuery.isLoading && <Skeleton variant="rectangular" height={200} />}

      {!statusQuery.isLoading && statuses.length === 0 && (
        <Alert severity="info">No budgets configured yet. Vendors are assigned a category, and a budget caps monthly spend for that category.</Alert>
      )}

      <Grid container spacing={2}>
        {statuses.map((status) => (
          <Grid item xs={12} md={6} key={status.budgetId}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Box>
                  <Typography variant="subtitle1" fontWeight={600}>{status.category}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {status.actualSpend} / {status.monthlyLimit} {status.currency} ({status.invoiceCount} invoice{status.invoiceCount === 1 ? "" : "s"})
                  </Typography>
                </Box>
                {canManage && (
                  <IconButton size="small" onClick={() => {
                    if (window.confirm(`Delete the budget for "${status.category}"?`)) {
                      deleteMutation.mutate(status.budgetId);
                    }
                  }}>
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                )}
              </Stack>
              <LinearProgress
                variant="determinate"
                value={Math.min(100, status.percentUsed)}
                color={status.overBudget ? "error" : status.percentUsed >= 80 ? "warning" : "primary"}
                sx={{ mt: 1.5, mb: 0.5, height: 8, borderRadius: 4 }}
              />
              <Typography variant="caption" color={status.overBudget ? "error.main" : "text.secondary"}>
                {status.percentUsed.toFixed(1)}% used
                {status.overBudget && ` — over budget by ${(status.actualSpend - status.monthlyLimit).toFixed(2)} ${status.currency}`}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="xs">
        <Box component="form" onSubmit={handleSubmit((values) => createMutation.mutate(values))}>
          <DialogTitle>Add budget</DialogTitle>
          <DialogContent>
            {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField
                label="Category"
                fullWidth
                helperText={errors.category?.message ?? "Must match a vendor's category exactly."}
                error={!!errors.category}
                {...register("category")}
              />
              <TextField
                label="Monthly limit"
                type="number"
                fullWidth
                error={!!errors.monthlyLimit}
                helperText={errors.monthlyLimit?.message}
                {...register("monthlyLimit")}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              Add budget
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Stack>
  );
}
