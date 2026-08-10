import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Grid,
  LinearProgress,
  MenuItem,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { analyticsApi } from "@/api/analyticsApi";
import { forecastApi } from "@/api/forecastApi";
import { budgetsApi } from "@/api/budgetsApi";
import { aiApi } from "@/api/aiApi";
import { useAuth } from "@/features/auth/AuthContext";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import SavingsOutlinedIcon from "@mui/icons-material/SavingsOutlined";
import Chip from "@mui/material/Chip";
import Button from "@mui/material/Button";
import { Link as RouterLink } from "react-router-dom";

const CAN_VIEW_ANALYTICS = new Set(["ORGANIZATION_ADMIN", "FINANCE_MANAGER", "ACCOUNTANT", "VIEWER"]);
const STATUS_COLORS: Record<string, string> = {
  NEEDS_REVIEW: "#f59e0b",
  VERIFIED: "#22c55e",
  PENDING_APPROVAL: "#f59e0b",
  APPROVED: "#3b82f6",
  PAYMENT_SCHEDULED: "#6366f1",
  PARTIALLY_PAID: "#eab308",
  PAID: "#16a34a",
  OVERDUE: "#ef4444",
  DISPUTED: "#dc2626",
  ARCHIVED: "#94a3b8",
};

function currency(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function StatCard({ label, value, secondary }: { label: string; value: string; secondary?: string }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="overline" color="text.secondary">{label}</Typography>
        <Typography variant="h5" fontWeight={700}>{value}</Typography>
        {secondary && <Typography variant="caption" color="text.secondary">{secondary}</Typography>}
      </CardContent>
    </Card>
  );
}

export function AnalyticsPage() {
  const { role } = useAuth();
  const canView = role !== null && CAN_VIEW_ANALYTICS.has(role);
  const [months, setMonths] = useState(6);

  const summaryQuery = useQuery({ queryKey: ["analytics-summary", months], queryFn: () => analyticsApi.summary(months), enabled: canView });
  const trendQuery = useQuery({ queryKey: ["analytics-trend", months], queryFn: () => analyticsApi.spendTrend(months), enabled: canView });
  const vendorsQuery = useQuery({ queryKey: ["analytics-vendors", months], queryFn: () => analyticsApi.topVendors(months, 8), enabled: canView });
  const categoriesQuery = useQuery({ queryKey: ["analytics-categories", months], queryFn: () => analyticsApi.categories(months), enabled: canView });
  const budgetHistoryQuery = useQuery({ queryKey: ["budget-history", months], queryFn: () => budgetsApi.history(Math.min(months, 12)), enabled: canView });
  const cashFlowQuery = useQuery({ queryKey: ["cash-flow"], queryFn: () => forecastApi.cashFlow(8), enabled: canView });
  const projectionQuery = useQuery({ queryKey: ["monthly-projection"], queryFn: () => forecastApi.monthlyProjection(3), enabled: canView });
  const costSavingsQuery = useQuery({ queryKey: ["ai-cost-savings"], queryFn: aiApi.getCostSavings, enabled: canView });

  if (!canView) {
    return (
      <Box>
        <Typography variant="h4" fontWeight={700}>Analytics</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>
          Organization-wide analytics aren't available to your role. Ask an admin or finance manager for access.
        </Alert>
      </Box>
    );
  }

  const summary = summaryQuery.data;
  const statusEntries = Object.entries(summary?.statusCounts ?? {}).filter(([, count]) => count > 0);

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>Analytics</Typography>
          <Typography variant="body2" color="text.secondary">
            Expense, vendor, and budget trends, plus a near-term cash-flow forecast built from committed payments.
          </Typography>
        </Box>
        <TextField select size="small" label="Period" value={months} onChange={(e) => setMonths(Number(e.target.value))} sx={{ width: 160 }}>
          <MenuItem value={3}>Last 3 months</MenuItem>
          <MenuItem value={6}>Last 6 months</MenuItem>
          <MenuItem value={12}>Last 12 months</MenuItem>
        </TextField>
      </Stack>

      {summaryQuery.isLoading ? (
        <Skeleton variant="rectangular" height={100} />
      ) : (
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label={`Total spend (${months}mo)`} value={currency(summary?.totalSpend)} secondary={`${summary?.invoiceCount ?? 0} invoices`} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Average invoice" value={currency(summary?.averageInvoiceAmount)} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Outstanding (all time)" value={currency(summary?.totalOutstanding)} secondary="Approved, scheduled, partial, or overdue" />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard label="Next month projection" value={currency(projectionQuery.data?.averageMonthlySpend)} secondary={`Avg. of last ${projectionQuery.data?.basedOnMonths ?? 3} months`} />
          </Grid>
        </Grid>
      )}

      {/* AI Cost Optimization & Intelligence */}
      {costSavingsQuery.data && costSavingsQuery.data.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2.5, backgroundColor: "background.paper", borderColor: "primary.main" }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <AutoAwesomeIcon color="primary" />
              <Typography variant="h6" fontWeight={700}>
                AI Cost-Saving & Optimization Insights
              </Typography>
            </Stack>
            <Button
              component={RouterLink}
              to="/ai-assistant"
              size="small"
              variant="outlined"
              startIcon={<AutoAwesomeIcon />}
            >
              Ask Finance Copilot
            </Button>
          </Stack>

          <Grid container spacing={2}>
            {costSavingsQuery.data.map((rec) => (
              <Grid item xs={12} md={4} key={rec.id}>
                <Card variant="outlined" sx={{ height: "100%", display: "flex", flexDirection: "column", p: 1 }}>
                  <CardContent sx={{ flexGrow: 1 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 1 }}>
                      <Typography variant="subtitle2" fontWeight={700}>
                        {rec.title}
                      </Typography>
                      <Chip
                        size="small"
                        label={rec.confidence + " IMPACT"}
                        color={rec.confidence === "HIGH" ? "error" : "warning"}
                        sx={{ fontSize: "0.65rem", fontWeight: 700 }}
                      />
                    </Stack>

                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      {rec.evidence}
                    </Typography>

                    <Alert severity="info" sx={{ py: 0.5, px: 1, fontSize: "0.8rem", mb: 1 }}>
                      <strong>Action:</strong> {rec.recommendedAction}
                    </Alert>

                    {rec.estimatedAnnualSaving > 0 && (
                      <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 1 }}>
                        <SavingsOutlinedIcon fontSize="small" color="success" />
                        <Typography variant="caption" fontWeight={700} color="success.main">
                          Est. Annual Savings: ₹{rec.estimatedAnnualSaving.toLocaleString()}
                        </Typography>
                      </Stack>
                    )}
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Paper>
      )}

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>Spend trend</Typography>
            {trendQuery.isLoading ? <Skeleton variant="rectangular" height={260} /> : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={trendQuery.data ?? []}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="month" />
                  <YAxis />
                  <Tooltip formatter={(value: number) => currency(value)} />
                  <Bar dataKey="totalSpend" name="Spend" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>Invoice status breakdown</Typography>
            {summaryQuery.isLoading ? <Skeleton variant="rectangular" height={260} /> : (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie data={statusEntries.map(([status, count]) => ({ name: status, value: count }))} dataKey="value" nameKey="name" outerRadius={90} label>
                    {statusEntries.map(([status]) => (
                      <Cell key={status} fill={STATUS_COLORS[status] ?? "#94a3b8"} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>Top vendors by spend</Typography>
            {vendorsQuery.isLoading ? <Skeleton variant="rectangular" height={280} /> : (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={vendorsQuery.data ?? []} layout="vertical" margin={{ left: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" />
                  <YAxis type="category" dataKey="vendorName" width={120} />
                  <Tooltip formatter={(value: number) => currency(value)} />
                  <Bar dataKey="totalSpend" name="Spend" fill="#6366f1" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
            {!vendorsQuery.isLoading && (vendorsQuery.data ?? []).length === 0 && (
              <Typography variant="body2" color="text.secondary">No vendor spend in this period.</Typography>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>Spend by category</Typography>
            {categoriesQuery.isLoading ? <Skeleton variant="rectangular" height={280} /> : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Category</TableCell>
                    <TableCell align="right">Spend</TableCell>
                    <TableCell align="right">Invoices</TableCell>
                    <TableCell>Budget</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(categoriesQuery.data ?? []).map((c) => {
                    const averagePerMonth = c.totalSpend / months;
                    return (
                      <TableRow key={c.category}>
                        <TableCell>{c.category}</TableCell>
                        <TableCell align="right">{currency(c.totalSpend)}</TableCell>
                        <TableCell align="right">{c.invoiceCount}</TableCell>
                        <TableCell sx={{ minWidth: 160 }}>
                          {c.budgetLimit != null ? (
                            <Box>
                              <LinearProgress
                                variant="determinate"
                                value={Math.min(100, (averagePerMonth / c.budgetLimit) * 100)}
                                color={averagePerMonth > c.budgetLimit ? "error" : "primary"}
                                sx={{ height: 6, borderRadius: 3 }}
                              />
                              <Typography variant="caption" color="text.secondary">
                                avg {currency(averagePerMonth)}/mo of {currency(c.budgetLimit)}/mo cap
                              </Typography>
                            </Box>
                          ) : (
                            <Typography variant="caption" color="text.secondary">No budget set</Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                  {(categoriesQuery.data ?? []).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4}>
                        <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
                          No categorized vendor spend in this period.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>Cash-flow forecast (next 8 weeks)</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
              Built only from money already committed — payments actually scheduled, and outstanding balances on
              invoices already due, that don't yet have a scheduled payment. Nothing here is predicted.
            </Typography>
            {cashFlowQuery.isLoading ? <Skeleton variant="rectangular" height={260} /> : (
              <>
                <Stack direction="row" spacing={3} sx={{ mb: 1 }}>
                  <Typography variant="body2"><strong>{currency(cashFlowQuery.data?.totalScheduled)}</strong> scheduled</Typography>
                  <Typography variant="body2"><strong>{currency(cashFlowQuery.data?.totalDueUnscheduled)}</strong> due, not yet scheduled</Typography>
                </Stack>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={cashFlowQuery.data?.weeks ?? []}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="weekStart" />
                    <YAxis />
                    <Tooltip formatter={(value: number) => currency(value)} />
                    <Legend />
                    <Bar dataKey="scheduledAmount" name="Scheduled" stackId="cash" fill="#3b82f6" />
                    <Bar dataKey="dueUnscheduledAmount" name="Due, unscheduled" stackId="cash" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </>
            )}
          </Paper>
        </Grid>

        {(budgetHistoryQuery.data ?? []).length > 0 && (
          <Grid item xs={12}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle1" gutterBottom>Budget history</Typography>
              <Grid container spacing={2}>
                {(budgetHistoryQuery.data ?? []).map((budget) => (
                  <Grid item xs={12} md={6} key={budget.budgetId}>
                    <Typography variant="body2" fontWeight={600}>{budget.category}</Typography>
                    <ResponsiveContainer width="100%" height={180}>
                      <LineChart data={budget.points}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="month" />
                        <YAxis />
                        <Tooltip formatter={(value: number) => currency(value)} />
                        <Legend />
                        <Line type="monotone" dataKey="actualSpend" name="Actual" stroke="#3b82f6" strokeWidth={2} />
                        <Line type="monotone" dataKey="monthlyLimit" name="Limit" stroke="#94a3b8" strokeDasharray="4 4" />
                      </LineChart>
                    </ResponsiveContainer>
                  </Grid>
                ))}
              </Grid>
            </Paper>
          </Grid>
        )}
      </Grid>
    </Stack>
  );
}
