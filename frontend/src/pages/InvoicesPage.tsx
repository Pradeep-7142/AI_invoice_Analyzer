import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
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
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import { invoicesApi } from "@/api/invoicesApi";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import { downloadBlob } from "@/utils/downloadBlob";
import type { InvoiceStatus, InvoiceSummary } from "@/types/invoice";

const STATUS_OPTIONS: { value: InvoiceStatus | ""; label: string }[] = [
  { value: "", label: "All Statuses" },
  { value: "NEEDS_REVIEW", label: "Needs Review" },
  { value: "VERIFIED", label: "Verified" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "ARCHIVED", label: "Archived" },
];

const STATUS_COLOR: Record<string, "default" | "warning" | "success" | "info" | "error"> = {
  UPLOADED: "default",
  PROCESSING: "info",
  NEEDS_REVIEW: "warning",
  VERIFIED: "success",
  APPROVED: "success",
  REJECTED: "error",
  ARCHIVED: "default",
};

function formatCurrency(amount: number | null, currency = "INR") {
  if (amount === null || amount === undefined) return "—";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount);
}

export function InvoicesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | "">("");
  const [searchQuery, setSearchQuery] = useState("");
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);

  const invoicesQuery = useQuery({
    queryKey: ["invoices", statusFilter],
    queryFn: () => invoicesApi.list(statusFilter),
  });

  const uploadMutation = useMutation({
    mutationFn: invoicesApi.upload,
    onSuccess: (invoice) => {
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      navigate(`/invoices/${invoice.id}`);
    },
    onError: (error) => setUploadError(apiErrorMessage(error)),
  });

  const allInvoices: InvoiceSummary[] = invoicesQuery.data?.content ?? [];
  const filteredInvoices = allInvoices.filter((inv) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    const num = (inv.invoiceNumber || "").toLowerCase();
    const vendor = (inv.vendor?.name || "").toLowerCase();
    return num.includes(q) || vendor.includes(q);
  });

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
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ sm: "center" }}
        spacing={2}
      >
        <Box>
          <Typography variant="h4" fontWeight={700} color="#0F172A">
            Invoices
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Upload PDF/image invoices to extract data via OCR or inspect processed invoices.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            startIcon={<FileDownloadOutlinedIcon />}
            onClick={handleExport}
            disabled={isExporting}
          >
            {isExporting ? "Exporting..." : "Export CSV"}
          </Button>
          <Button
            variant="contained"
            startIcon={<UploadFileIcon />}
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadMutation.isPending}
          >
            {uploadMutation.isPending ? "Uploading & Processing..." : "Upload Invoice"}
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

      {/* Search & Filter Bar */}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems="center">
        <TextField
          fullWidth
          size="small"
          placeholder="Search by invoice number or vendor name..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon color="action" fontSize="small" />
              </InputAdornment>
            ),
            endAdornment: searchQuery ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearchQuery("")}>
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : null,
          }}
        />

        <TextField
          select
          size="small"
          label="Status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as InvoiceStatus | "")}
          sx={{ minWidth: { xs: "100%", sm: 200 } }}
        >
          {STATUS_OPTIONS.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>
              {opt.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: "14px" }}>
        <Table>
          <TableHead sx={{ bgcolor: "#F8FAFC" }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Invoice #</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Vendor</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Date</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Due Date</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, color: "#64748B" }}>
                Amount
              </TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#64748B" }}>Submitted By</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {invoicesQuery.isLoading &&
              [1, 2, 3, 4].map((i) => (
                <TableRow key={i}>
                  <TableCell colSpan={7}>
                    <Skeleton variant="text" height={32} />
                  </TableCell>
                </TableRow>
              ))}

            {!invoicesQuery.isLoading &&
              filteredInvoices.map((invoice) => (
                <TableRow
                  key={invoice.id}
                  hover
                  sx={{ cursor: "pointer" }}
                  onClick={() => navigate(`/invoices/${invoice.id}`)}
                >
                  <TableCell sx={{ fontWeight: 600, color: "#1D4ED8" }}>
                    {invoice.invoiceNumber || <em>Not entered</em>}
                  </TableCell>
                  <TableCell>{invoice.vendor?.name || <em>Unassigned</em>}</TableCell>
                  <TableCell>{invoice.invoiceDate || "—"}</TableCell>
                  <TableCell>{invoice.dueDate || "—"}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>
                    {formatCurrency(invoice.totalAmount, invoice.currency)}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={invoice.status.replace("_", " ")}
                      color={STATUS_COLOR[invoice.status] ?? "default"}
                      sx={{ fontSize: "0.75rem", fontWeight: 600 }}
                    />
                  </TableCell>
                  <TableCell>{invoice.submittedByName}</TableCell>
                </TableRow>
              ))}

            {!invoicesQuery.isLoading && filteredInvoices.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Box sx={{ py: 6, textAlign: "center" }}>
                    <Typography variant="body1" fontWeight={600} color="text.secondary">
                      No invoices found
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Upload an invoice PDF to start intelligent parsing.
                    </Typography>
                  </Box>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}
