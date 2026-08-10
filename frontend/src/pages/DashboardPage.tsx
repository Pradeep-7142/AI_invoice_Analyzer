import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from "@mui/material";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import LightbulbOutlinedIcon from "@mui/icons-material/LightbulbOutlined";
import { dashboardApi } from "@/api/dashboardApi";
import { aiApi } from "@/api/aiApi";
import { useAuth } from "@/features/auth/AuthContext";
import type { InvoiceSummary } from "@/types/invoice";
import type { BudgetStatus } from "@/types/finance";
import type { QuickInsight } from "@/types/ai";

function formatCurrency(amount: number | null, currency: string) {
  if (amount === null) return "—";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount);
}

function InvoiceAttentionTable({ invoices }: { invoices: InvoiceSummary[] }) {
  const navigate = useNavigate();
  return (
    <Table size="small">
      <TableBody>
        {invoices.map((invoice) => (
          <TableRow key={invoice.id} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/invoices/${invoice.id}`)}>
            <TableCell>{invoice.invoiceNumber ?? <em>Untitled</em>}</TableCell>
            <TableCell>{invoice.vendor?.name ?? "—"}</TableCell>
            <TableCell align="right">{formatCurrency(invoice.totalAmount, invoice.currency)}</TableCell>
            <TableCell><Chip size="small" label={invoice.status.replaceAll("_", " ")} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function BudgetAttentionTable({ budgets }: { budgets: BudgetStatus[] }) {
  const navigate = useNavigate();
  return (
    <Table size="small">
      <TableBody>
        {budgets.map((budget) => (
          <TableRow key={budget.budgetId} hover sx={{ cursor: "pointer" }} onClick={() => navigate("/budgets")}>
            <TableCell>{budget.category}</TableCell>
            <TableCell align="right">{formatCurrency(budget.actualSpend, budget.currency)} of {formatCurrency(budget.monthlyLimit, budget.currency)}</TableCell>
            <TableCell align="right"><Chip size="small" color="error" label={`${budget.percentUsed.toFixed(0)}%`} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function AttentionSection({ title, emptyLabel, children, count }: { title: string; emptyLabel: string; count: number; children: ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="subtitle1">{title}</Typography>
        {count > 0 && <Chip size="small" label={count} />}
      </Stack>
      {count === 0 ? (
        <Typography variant="body2" color="text.secondary">{emptyLabel}</Typography>
      ) : (
        children
      )}
    </Paper>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const actionCenterQuery = useQuery({ queryKey: ["action-center"], queryFn: dashboardApi.actionCenter });
  const quickInsightsQuery = useQuery({ queryKey: ["ai-quick-insights"], queryFn: aiApi.getQuickInsights });

  if (actionCenterQuery.isLoading) {
    return <Skeleton variant="rectangular" height={400} />;
  }

  if (actionCenterQuery.isError) {
    return <Alert severity="error">Unable to load your dashboard right now.</Alert>;
  }

  const data = actionCenterQuery.data!;
  const totalAttentionItems =
    data.pendingMyApproval.length + data.needsMyAttention.length + data.overdueInvoices.length + data.overBudgetCategories.length;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" alignItems={{ sm: "center" }} spacing={2}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Welcome back{user ? `, ${user.fullName.split(" ")[0]}` : ""}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Everything below is live — nothing here is a stored notification, so it's never stale.
          </Typography>
        </Box>

        <Button
          component={RouterLink}
          to="/ai-assistant"
          variant="contained"
          startIcon={<AutoAwesomeIcon />}
          sx={{ alignSelf: { xs: "flex-start", sm: "center" } }}
        >
          Open Finance Copilot
        </Button>
      </Stack>

      {/* AI Quick Insights Banner */}
      {quickInsightsQuery.data && quickInsightsQuery.data.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2, backgroundColor: "background.paper", borderColor: "divider" }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
            <AutoAwesomeIcon color="primary" fontSize="small" />
            <Typography variant="subtitle2" fontWeight={700}>
              AI Financial Briefing & Alerts
            </Typography>
          </Stack>
          <Grid container spacing={2}>
            {quickInsightsQuery.data.slice(0, 3).map((insight: QuickInsight, idx: number) => (
              <Grid item xs={12} md={4} key={idx}>
                <Card variant="outlined" sx={{ height: "100%" }}>
                  <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                      <LightbulbOutlinedIcon
                        fontSize="small"
                        color={insight.severity === "CRITICAL" ? "error" : insight.severity === "WARNING" ? "warning" : "primary"}
                      />
                      <Typography variant="subtitle2" fontWeight={600}>
                        {insight.title}
                      </Typography>
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      {insight.summary}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Paper>
      )}

      {totalAttentionItems === 0 && (
        <Alert severity="success" icon={<CheckCircleOutlineIcon fontSize="inherit" />}>
          You're all caught up — nothing needs your attention right now.
        </Alert>
      )}

      <Stack spacing={2}>
        <AttentionSection title="Pending your approval" emptyLabel="Nothing waiting on your approval." count={data.pendingMyApproval.length}>
          <InvoiceAttentionTable invoices={data.pendingMyApproval} />
        </AttentionSection>

        <AttentionSection title="Needs your attention" emptyLabel="None of your submissions were rejected or disputed." count={data.needsMyAttention.length}>
          <InvoiceAttentionTable invoices={data.needsMyAttention} />
        </AttentionSection>

        <AttentionSection title="Overdue invoices" emptyLabel="Nothing is overdue." count={data.overdueInvoices.length}>
          <InvoiceAttentionTable invoices={data.overdueInvoices} />
        </AttentionSection>

        <AttentionSection title="Over-budget categories this month" emptyLabel="Every budget is within its cap." count={data.overBudgetCategories.length}>
          <BudgetAttentionTable budgets={data.overBudgetCategories} />
        </AttentionSection>
      </Stack>

      <Stack direction="row" spacing={1}>
        <Button component={RouterLink} to="/invoices">View all invoices</Button>
        <Button component={RouterLink} to="/analytics">View analytics</Button>
        <Button component={RouterLink} to="/ai-assistant" startIcon={<AutoAwesomeIcon />}>Ask AI Copilot</Button>
      </Stack>
    </Stack>
  );
}
