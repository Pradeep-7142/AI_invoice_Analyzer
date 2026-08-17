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
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import AttachMoneyIcon from "@mui/icons-material/AttachMoney";
import PendingActionsIcon from "@mui/icons-material/PendingActions";
import VerifiedIcon from "@mui/icons-material/Verified";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { dashboardApi } from "@/api/dashboardApi";
import { useAuth } from "@/features/auth/AuthContext";
import type { InvoiceStatus } from "@/types/invoice";

function formatCurrency(amount: number | null, currency = "INR") {
  if (amount === null || amount === undefined) return "₹0.00";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount);
}

function getStatusChipColor(status: InvoiceStatus) {
  switch (status) {
    case "APPROVED":
    case "VERIFIED":
      return "success";
    case "NEEDS_REVIEW":
    case "UPLOADED":
      return "warning";
    case "REJECTED":
      return "error";
    default:
      return "default";
  }
}

export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: dashboardApi.getSummary,
  });

  if (isLoading) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={100} sx={{ borderRadius: "12px" }} />
        <Grid container spacing={3}>
          {[1, 2, 3, 4].map((i) => (
            <Grid item xs={12} sm={6} md={3} key={i}>
              <Skeleton variant="rectangular" height={120} sx={{ borderRadius: "12px" }} />
            </Grid>
          ))}
        </Grid>
        <Skeleton variant="rectangular" height={300} sx={{ borderRadius: "12px" }} />
      </Stack>
    );
  }

  if (isError || !data) {
    return (
      <Alert severity="error" action={<Button onClick={() => refetch()}>Retry</Button>}>
        Unable to load dashboard data. Please try again.
      </Alert>
    );
  }

  const vendorChartData = (data.topVendors || []).map((v) => ({
    name: v.vendorName.length > 15 ? v.vendorName.substring(0, 15) + "..." : v.vendorName,
    spend: v.totalAmount,
  }));

  const monthlyChartData = (data.monthlyTrends || []).map((m) => ({
    month: m.month,
    spend: m.totalAmount,
  }));

  return (
    <Stack spacing={3.5}>
      {/* Top Welcome Bar */}
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ sm: "center" }}
        spacing={2}
      >
        <Box>
          <Typography variant="h4" fontWeight={700} color="#0F172A">
            Overview Dashboard
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Welcome back, {user?.fullName}. Here is your live invoice processing activity.
          </Typography>
        </Box>

        <Stack direction="row" spacing={1.5}>
          <Button
            component={RouterLink}
            to="/ai-assistant"
            variant="outlined"
            startIcon={<AutoAwesomeIcon />}
            sx={{ borderColor: "#93C5FD", color: "#1D4ED8" }}
          >
            AI Assistant
          </Button>
          <Button
            component={RouterLink}
            to="/invoices"
            variant="contained"
            startIcon={<UploadFileIcon />}
          >
            Manage Invoices
          </Button>
        </Stack>
      </Stack>

      {/* KPI Metric Cards */}
      <Grid container spacing={2.5}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: "#FFFFFF", p: 0.5 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography variant="caption" fontWeight={600} color="text.secondary">
                    TOTAL INVOICES
                  </Typography>
                  <Typography variant="h4" fontWeight={700} sx={{ mt: 0.5, color: "#0F172A" }}>
                    {data.totalInvoices}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: "12px",
                    bgcolor: "#EFF6FF",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#2563EB",
                  }}
                >
                  <ReceiptLongIcon />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: "#FFFFFF", p: 0.5 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography variant="caption" fontWeight={600} color="text.secondary">
                    TOTAL SPEND
                  </Typography>
                  <Typography variant="h5" fontWeight={700} sx={{ mt: 0.5, color: "#0F172A" }}>
                    {formatCurrency(data.totalSpend)}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: "12px",
                    bgcolor: "#ECFDF5",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#059669",
                  }}
                >
                  <AttachMoneyIcon />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: "#FFFFFF", p: 0.5 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography variant="caption" fontWeight={600} color="text.secondary">
                    PENDING REVIEW
                  </Typography>
                  <Typography variant="h4" fontWeight={700} sx={{ mt: 0.5, color: "#D97706" }}>
                    {data.needsReviewCount}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: "12px",
                    bgcolor: "#FFFBEB",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#D97706",
                  }}
                >
                  <PendingActionsIcon />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: "#FFFFFF", p: 0.5 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography variant="caption" fontWeight={600} color="text.secondary">
                    VERIFIED & APPROVED
                  </Typography>
                  <Typography variant="h4" fontWeight={700} sx={{ mt: 0.5, color: "#16A34A" }}>
                    {data.verifiedCount + data.approvedCount}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: "12px",
                    bgcolor: "#F0FDF4",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "#16A34A",
                  }}
                >
                  <VerifiedIcon />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Visual Analytics Charts */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: "14px", height: "100%" }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>
              Spend by Top Vendors
            </Typography>
            {vendorChartData.length === 0 ? (
              <Box sx={{ py: 6, textAlign: "center" }}>
                <Typography variant="body2" color="text.secondary">
                  No vendor data recorded yet.
                </Typography>
              </Box>
            ) : (
              <Box sx={{ width: "100%", height: 260 }}>
                <ResponsiveContainer>
                  <BarChart data={vendorChartData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F1F5F9" />
                    <XAxis dataKey="name" stroke="#64748B" fontSize={12} />
                    <YAxis stroke="#64748B" fontSize={12} tickFormatter={(v) => `₹${v}`} />
                    <Tooltip
                      formatter={(val: number) => [formatCurrency(val), "Spend"]}
                      contentStyle={{ borderRadius: "8px", border: "1px solid #E2E8F0" }}
                    />
                    <Bar dataKey="spend" fill="#3B82F6" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: "14px", height: "100%" }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>
              Monthly Spend Trend
            </Typography>
            {monthlyChartData.length === 0 ? (
              <Box sx={{ py: 6, textAlign: "center" }}>
                <Typography variant="body2" color="text.secondary">
                  No monthly invoice history yet.
                </Typography>
              </Box>
            ) : (
              <Box sx={{ width: "100%", height: 260 }}>
                <ResponsiveContainer>
                  <BarChart data={monthlyChartData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F1F5F9" />
                    <XAxis dataKey="month" stroke="#64748B" fontSize={12} />
                    <YAxis stroke="#64748B" fontSize={12} tickFormatter={(v) => `₹${v}`} />
                    <Tooltip
                      formatter={(val: number) => [formatCurrency(val), "Total"]}
                      contentStyle={{ borderRadius: "8px", border: "1px solid #E2E8F0" }}
                    />
                    <Bar dataKey="spend" fill="#10B981" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>

      {/* Recent Invoices Table */}
      <Paper variant="outlined" sx={{ borderRadius: "14px", overflow: "hidden" }}>
        <Box sx={{ p: 2.5, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Typography variant="subtitle1" fontWeight={700}>
            Recent Invoices
          </Typography>
          <Button component={RouterLink} to="/invoices" size="small">
            View All
          </Button>
        </Box>

        <TableContainer>
          <Table size="small">
            <TableHead sx={{ bgcolor: "#F8FAFC" }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Invoice #</TableCell>
                <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Vendor</TableCell>
                <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Date</TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, color: "#64748B" }}>
                  Amount
                </TableCell>
                <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.recentInvoices?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ py: 3, color: "text.secondary" }}>
                    No invoices processed yet. Click &quot;Manage Invoices&quot; to upload your first invoice.
                  </TableCell>
                </TableRow>
              ) : (
                data.recentInvoices?.map((inv) => (
                  <TableRow
                    key={inv.id}
                    hover
                    sx={{ cursor: "pointer" }}
                    onClick={() => navigate(`/invoices/${inv.id}`)}
                  >
                    <TableCell sx={{ fontWeight: 600, color: "#1D4ED8" }}>
                      {inv.invoiceNumber || "Untitled"}
                    </TableCell>
                    <TableCell>{inv.vendor?.name || "—"}</TableCell>
                    <TableCell>{inv.invoiceDate || "—"}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {formatCurrency(inv.totalAmount, inv.currency)}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={inv.status.replace("_", " ")}
                        color={getStatusChipColor(inv.status)}
                        sx={{ fontSize: "0.75rem", fontWeight: 600 }}
                      />
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Stack>
  );
}
