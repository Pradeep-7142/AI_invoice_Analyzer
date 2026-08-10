import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Alert,
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
import UploadFileIcon from "@mui/icons-material/UploadFileOutlined";
import FileDownloadOutlinedIcon from "@mui/icons-material/FileDownloadOutlined";
import { invoicesApi } from "@/api/invoicesApi";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import { downloadBlob } from "@/utils/downloadBlob";
import type { InvoiceStatus } from "@/types/invoice";

const STATUS_OPTIONS: { value: InvoiceStatus | ""; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "NEEDS_REVIEW", label: "Needs review" },
  { value: "VERIFIED", label: "Verified" },
  { value: "PENDING_APPROVAL", label: "Pending approval" },
  { value: "APPROVED", label: "Approved" },
  { value: "PAYMENT_SCHEDULED", label: "Payment scheduled" },
  { value: "PARTIALLY_PAID", label: "Partially paid" },
  { value: "PAID", label: "Paid" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "DISPUTED", label: "Disputed" },
  { value: "ARCHIVED", label: "Archived" },
];

const STATUS_COLOR: Record<string, "default" | "warning" | "success" | "info" | "error"> = {
  NEEDS_REVIEW: "warning",
  VERIFIED: "success",
  PENDING_APPROVAL: "warning",
  APPROVED: "info",
  PAYMENT_SCHEDULED: "info",
  PARTIALLY_PAID: "warning",
  PAID: "success",
  OVERDUE: "error",
  DISPUTED: "error",
  ARCHIVED: "default",
};

function formatCurrency(amount: number | null, currency: string) {
  if (amount === null) return "—";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount);
}

export function InvoicesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | "">("");
  const [uploadError, setUploadError] = useState<string | null>(null);

  const invoicesQuery = useQuery({
    queryKey: ["invoices", statusFilter],
    queryFn: () => invoicesApi.list(statusFilter),
  });

  const uploadMutation = useMutation({
    mutationFn: invoicesApi.upload,
    onSuccess: (invoice) => {
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      navigate(`/invoices/${invoice.id}`);
    },
    onError: (error) => setUploadError(apiErrorMessage(error)),
  });

  const invoices = invoicesQuery.data?.content ?? [];
  const [isExporting, setIsExporting] = useState(false);

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const blob = await invoicesApi.exportCsv(statusFilter);
      downloadBlob(blob, "invoices.csv");
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Invoices
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Upload a document to start tracking an invoice.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            startIcon={<FileDownloadOutlinedIcon />}
            onClick={handleExport}
            disabled={isExporting}
          >
            Export CSV
          </Button>
          <Button
            variant="contained"
            startIcon={<UploadFileIcon />}
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadMutation.isPending}
          >
            {uploadMutation.isPending ? "Uploading..." : "Upload invoice"}
          </Button>
        </Stack>
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,.webp"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (file) {
              setUploadError(null);
              uploadMutation.mutate(file);
            }
          }}
        />
      </Stack>

      {uploadError && (
        <Alert severity="error" onClose={() => setUploadError(null)}>
          {uploadError}
        </Alert>
      )}

      <TextField
        select
        size="small"
        label="Status"
        value={statusFilter}
        onChange={(e) => setStatusFilter(e.target.value as InvoiceStatus | "")}
        sx={{ maxWidth: 220 }}
      >
        {STATUS_OPTIONS.map((opt) => (
          <MenuItem key={opt.value} value={opt.value}>
            {opt.label}
          </MenuItem>
        ))}
      </TextField>

      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Invoice #</TableCell>
              <TableCell>Vendor</TableCell>
              <TableCell>Date</TableCell>
              <TableCell>Total</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Submitted by</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {invoicesQuery.isLoading &&
              [1, 2, 3].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={6}><Skeleton variant="text" /></TableCell>
                </TableRow>
              ))}

            {invoices.map((invoice) => (
              <TableRow
                key={invoice.id}
                hover
                sx={{ cursor: "pointer" }}
                onClick={() => navigate(`/invoices/${invoice.id}`)}
              >
                <TableCell>{invoice.invoiceNumber ?? <em>Not entered</em>}</TableCell>
                <TableCell>{invoice.vendor?.name ?? <em>Unassigned</em>}</TableCell>
                <TableCell>{invoice.invoiceDate ?? "—"}</TableCell>
                <TableCell>{formatCurrency(invoice.totalAmount, invoice.currency)}</TableCell>
                <TableCell>
                  <Chip size="small" label={invoice.status.replaceAll("_", " ")} color={STATUS_COLOR[invoice.status] ?? "default"} />
                </TableCell>
                <TableCell>{invoice.submittedByName}</TableCell>
              </TableRow>
            ))}

            {!invoicesQuery.isLoading && invoices.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No invoices yet. Upload your first invoice to start tracking expenses.
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
