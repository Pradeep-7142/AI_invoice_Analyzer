import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link as RouterLink } from "react-router-dom";
import {
  Box,
  Button,
  Chip,
  MenuItem,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import FileDownloadOutlinedIcon from "@mui/icons-material/FileDownloadOutlined";
import { paymentsApi } from "@/api/paymentsApi";
import { downloadBlob } from "@/utils/downloadBlob";
import type { PaymentStatus } from "@/types/finance";

const STATUS_OPTIONS: (PaymentStatus | "")[] = ["", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED"];

const PAYMENT_STATUS_COLOR: Record<PaymentStatus, "default" | "warning" | "success" | "error"> = {
  SCHEDULED: "warning",
  COMPLETED: "success",
  FAILED: "error",
  CANCELLED: "default",
};

export function PaymentsPage() {
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | "">("");
  const [isExporting, setIsExporting] = useState(false);

  const paymentsQuery = useQuery({
    queryKey: ["payments", statusFilter],
    queryFn: () => paymentsApi.list(statusFilter),
  });

  const payments = paymentsQuery.data?.content ?? [];

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const blob = await paymentsApi.exportCsv(statusFilter);
      downloadBlob(blob, "payments.csv");
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Payments
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Every scheduled and completed payment across your organization's invoices.
          </Typography>
        </Box>
        <Button startIcon={<FileDownloadOutlinedIcon />} onClick={handleExport} disabled={isExporting}>
          Export CSV
        </Button>
      </Stack>

      <TextField
        select
        size="small"
        label="Status"
        value={statusFilter}
        onChange={(e) => setStatusFilter(e.target.value as PaymentStatus | "")}
        sx={{ maxWidth: 220 }}
      >
        {STATUS_OPTIONS.map((s) => (
          <MenuItem key={s} value={s}>{s === "" ? "All" : s}</MenuItem>
        ))}
      </TextField>

      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Invoice</TableCell>
              <TableCell>Vendor</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Method</TableCell>
              <TableCell>Scheduled</TableCell>
              <TableCell>Completed</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Invoice</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paymentsQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={8}><Skeleton variant="text" /></TableCell>
                </TableRow>
              ))}

            {payments.map((payment) => (
              <TableRow key={payment.id} hover>
                <TableCell>{payment.invoiceNumber ?? "Untitled invoice"}</TableCell>
                <TableCell>{payment.vendorName ?? "—"}</TableCell>
                <TableCell>{payment.amount} {payment.currency}</TableCell>
                <TableCell>{payment.method.replaceAll("_", " ")}</TableCell>
                <TableCell>{payment.scheduledDate}</TableCell>
                <TableCell>{payment.completedAt ? payment.completedAt.slice(0, 10) : "—"}</TableCell>
                <TableCell>
                  <Chip size="small" label={payment.status} color={PAYMENT_STATUS_COLOR[payment.status]} />
                </TableCell>
                <TableCell align="right">
                  <Button size="small" component={RouterLink} to={`/invoices/${payment.invoiceId}`}>
                    View
                  </Button>
                </TableCell>
              </TableRow>
            ))}

            {!paymentsQuery.isLoading && payments.length === 0 && (
              <TableRow>
                <TableCell colSpan={8}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No payments yet.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}
