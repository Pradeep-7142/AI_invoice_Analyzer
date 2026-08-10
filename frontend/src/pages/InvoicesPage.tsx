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
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import { invoicesApi } from "@/api/invoicesApi";
import { aiApi } from "@/api/aiApi";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import { downloadBlob } from "@/utils/downloadBlob";
import type { InvoiceStatus, InvoiceSummary } from "@/types/invoice";
import type { NaturalSearchResponse } from "@/types/ai";

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
  const [naturalQuery, setNaturalQuery] = useState("");
  const [naturalSearchResult, setNaturalSearchResult] = useState<NaturalSearchResponse | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const invoicesQuery = useQuery({
    queryKey: ["invoices", statusFilter],
    queryFn: () => invoicesApi.list(statusFilter),
    enabled: !naturalSearchResult,
  });

  const naturalSearchMutation = useMutation({
    mutationFn: (query: string) => aiApi.naturalSearch(query),
    onSuccess: (data) => {
      setNaturalSearchResult(data);
    },
  });

  const uploadMutation = useMutation({
    mutationFn: invoicesApi.upload,
    onSuccess: (invoice) => {
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      navigate(`/invoices/${invoice.id}`);
    },
    onError: (error) => setUploadError(apiErrorMessage(error)),
  });

  const invoices: InvoiceSummary[] = naturalSearchResult
    ? naturalSearchResult.results
    : invoicesQuery.data?.content ?? [];

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

  const handleNaturalSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!naturalQuery.trim()) {
      setNaturalSearchResult(null);
      return;
    }
    naturalSearchMutation.mutate(naturalQuery.trim());
  };

  const clearNaturalSearch = () => {
    setNaturalQuery("");
    setNaturalSearchResult(null);
  };

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Invoices
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Upload a document to start tracking an invoice or query using natural language search.
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

      {/* Search & Filter Bar */}
      <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems="center">
        <Box component="form" onSubmit={handleNaturalSearchSubmit} sx={{ flexGrow: 1, width: "100%" }}>
          <TextField
            fullWidth
            size="small"
            placeholder="AI Search: e.g. 'approved cloud invoices over 50000' or 'overdue from AWS'"
            value={naturalQuery}
            onChange={(e) => setNaturalQuery(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <AutoAwesomeIcon color="primary" fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  {naturalQuery && (
                    <IconButton size="small" onClick={clearNaturalSearch}>
                      <ClearIcon fontSize="small" />
                    </IconButton>
                  )}
                  <IconButton size="small" type="submit" disabled={naturalSearchMutation.isPending}>
                    <SearchIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        </Box>

        <TextField
          select
          size="small"
          label="Status"
          value={statusFilter}
          onChange={(e) => {
            setNaturalSearchResult(null);
            setStatusFilter(e.target.value as InvoiceStatus | "");
          }}
          sx={{ minWidth: 200 }}
        >
          {STATUS_OPTIONS.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>
              {opt.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {/* AI Interpretation Chip */}
      {naturalSearchResult && (
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            icon={<AutoAwesomeIcon />}
            label={naturalSearchResult.criteria.interpretation}
            color="primary"
            variant="outlined"
            onDelete={clearNaturalSearch}
          />
          <Typography variant="caption" color="text.secondary">
            Found {naturalSearchResult.totalMatches} match(es)
          </Typography>
        </Stack>
      )}

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
            {(invoicesQuery.isLoading || naturalSearchMutation.isPending) &&
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

            {!invoicesQuery.isLoading && !naturalSearchMutation.isPending && invoices.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: "center" }}>
                    No invoices match your filter criteria.
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
