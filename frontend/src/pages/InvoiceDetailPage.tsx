import { useEffect, useState } from "react";
import { useForm, useFieldArray } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
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
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AddIcon from "@mui/icons-material/Add";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import FileDownloadOutlinedIcon from "@mui/icons-material/FileDownloadOutlined";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import SendIcon from "@mui/icons-material/Send";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import VerifiedIcon from "@mui/icons-material/Verified";
import CheckIcon from "@mui/icons-material/Check";
import BlockIcon from "@mui/icons-material/Block";
import ArchiveIcon from "@mui/icons-material/Archive";
import { invoicesApi, type InvoiceUpdatePayload } from "@/api/invoicesApi";
import { vendorsApi } from "@/api/vendorsApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import { downloadBlob } from "@/utils/downloadBlob";
import type { InvoiceStatus, ValidationStatus, Vendor } from "@/types/invoice";

const STATUS_COLOR: Record<InvoiceStatus, "default" | "warning" | "success" | "info" | "error"> = {
  UPLOADED: "default",
  PROCESSING: "info",
  NEEDS_REVIEW: "warning",
  VERIFIED: "success",
  APPROVED: "success",
  REJECTED: "error",
  ARCHIVED: "default",
};

const VALIDATION_ICON: Record<ValidationStatus, JSX.Element> = {
  PASS: <CheckCircleOutlineIcon fontSize="small" color="success" />,
  WARNING: <WarningAmberOutlinedIcon fontSize="small" color="warning" />,
  ERROR: <ErrorOutlineIcon fontSize="small" color="error" />,
};

function confidenceHelperText(fieldConfidence: Record<string, number> | null | undefined, field: string) {
  const confidence = fieldConfidence?.[field];
  if (confidence === undefined) return undefined;
  const percent = Math.round(confidence * 100);
  return confidence < 0.75
    ? `⚠ AI extracted with ${percent}% confidence — verify`
    : `AI extracted (${percent}% confidence)`;
}

interface FormValues {
  vendorId: string | null;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  currency: string;
  subtotalAmount: string;
  taxAmount: string;
  discountAmount: string;
  totalAmount: string;
  notes: string;
  lineItems: {
    description: string;
    quantity: number;
    unitPrice: number;
    taxAmount: number;
    discountAmount: number;
    totalAmount: number;
  }[];
}

export function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAdmin } = useAuth();

  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // AI Q&A State
  const [aiQuestion, setAiQuestion] = useState("");
  const [aiConversation, setAiConversation] = useState<
    { role: "user" | "assistant"; content: string }[]
  >([]);
  const [isAskingAi, setIsAskingAi] = useState(false);

  const invoiceQuery = useQuery({
    queryKey: ["invoice", id],
    queryFn: () => invoicesApi.get(id!),
    enabled: !!id,
  });

  const vendorsQuery = useQuery({
    queryKey: ["vendors"],
    queryFn: () => vendorsApi.list(),
  });

  const { register, control, handleSubmit, reset, watch, setValue } = useForm<FormValues>({
    defaultValues: {
      vendorId: null,
      invoiceNumber: "",
      invoiceDate: "",
      dueDate: "",
      currency: "INR",
      subtotalAmount: "",
      taxAmount: "",
      discountAmount: "",
      totalAmount: "",
      notes: "",
      lineItems: [],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control,
    name: "lineItems",
  });

  useEffect(() => {
    if (invoiceQuery.data) {
      const inv = invoiceQuery.data;
      reset({
        vendorId: inv.vendor?.id ?? null,
        invoiceNumber: inv.invoiceNumber ?? "",
        invoiceDate: inv.invoiceDate ?? "",
        dueDate: inv.dueDate ?? "",
        currency: inv.currency ?? "INR",
        subtotalAmount: inv.subtotalAmount !== null ? String(inv.subtotalAmount) : "",
        taxAmount: inv.taxAmount !== null ? String(inv.taxAmount) : "",
        discountAmount: inv.discountAmount !== null ? String(inv.discountAmount) : "",
        totalAmount: inv.totalAmount !== null ? String(inv.totalAmount) : "",
        notes: inv.notes ?? "",
        lineItems: (inv.lineItems ?? []).map((li) => ({
          description: li.description,
          quantity: li.quantity,
          unitPrice: li.unitPrice,
          taxAmount: li.taxAmount,
          discountAmount: li.discountAmount,
          totalAmount: li.totalAmount,
        })),
      });
    }
  }, [invoiceQuery.data, reset]);

  const updateMutation = useMutation({
    mutationFn: (payload: InvoiceUpdatePayload) => invoicesApi.update(id!, payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(["invoice", id], updated);
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      setSuccessMessage("Invoice saved successfully.");
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const verifyMutation = useMutation({
    mutationFn: () => invoicesApi.verify(id!),
    onSuccess: (updated) => {
      queryClient.setQueryData(["invoice", id], updated);
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      setSuccessMessage("Invoice marked as VERIFIED.");
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const approveMutation = useMutation({
    mutationFn: () => invoicesApi.approve(id!),
    onSuccess: (updated) => {
      queryClient.setQueryData(["invoice", id], updated);
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      setSuccessMessage("Invoice APPROVED.");
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => invoicesApi.reject(id!, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(["invoice", id], updated);
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      setRejectDialogOpen(false);
      setRejectReason("");
      setSuccessMessage("Invoice REJECTED.");
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const archiveMutation = useMutation({
    mutationFn: () => invoicesApi.archive(id!),
    onSuccess: (updated) => {
      queryClient.setQueryData(["invoice", id], updated);
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      setSuccessMessage("Invoice ARCHIVED.");
      setActionError(null);
    },
    onError: (err) => setActionError(apiErrorMessage(err)),
  });

  const onSave = (values: FormValues) => {
    setActionError(null);
    setSuccessMessage(null);
    const payload: InvoiceUpdatePayload = {
      vendorId: values.vendorId || null,
      invoiceNumber: values.invoiceNumber.trim() || null,
      invoiceDate: values.invoiceDate || null,
      dueDate: values.dueDate || null,
      currency: values.currency || "INR",
      subtotalAmount: values.subtotalAmount ? Number(values.subtotalAmount) : null,
      taxAmount: values.taxAmount ? Number(values.taxAmount) : null,
      discountAmount: values.discountAmount ? Number(values.discountAmount) : null,
      totalAmount: values.totalAmount ? Number(values.totalAmount) : null,
      notes: values.notes || null,
      lineItems: values.lineItems.map((li, idx) => ({
        lineOrder: idx,
        description: li.description,
        quantity: Number(li.quantity),
        unitPrice: Number(li.unitPrice),
        taxAmount: Number(li.taxAmount || 0),
        discountAmount: Number(li.discountAmount || 0),
        totalAmount: Number(li.totalAmount),
      })),
    };
    updateMutation.mutate(payload);
  };

  const handleDownloadDoc = async () => {
    try {
      const blob = await invoicesApi.downloadDocument(id!);
      const filename = invoiceQuery.data?.documents?.[0]?.originalFilename || "invoice_document";
      downloadBlob(blob, filename);
    } catch (e) {
      setActionError("Unable to download document.");
    }
  };

  const handleAskAi = async (customPrompt?: string) => {
    const q = customPrompt || aiQuestion;
    if (!q.trim()) return;

    setAiConversation((prev) => [...prev, { role: "user", content: q }]);
    setAiQuestion("");
    setIsAskingAi(true);

    try {
      const res = await invoicesApi.askQuestion(id!, q);
      setAiConversation((prev) => [...prev, { role: "assistant", content: res.answer }]);
    } catch (e) {
      setAiConversation((prev) => [
        ...prev,
        { role: "assistant", content: "Sorry, I could not process your question for this invoice." },
      ]);
    } finally {
      setIsAskingAi(false);
    }
  };

  if (invoiceQuery.isLoading) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={80} sx={{ borderRadius: "12px" }} />
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Skeleton variant="rectangular" height={500} sx={{ borderRadius: "12px" }} />
          </Grid>
          <Grid item xs={12} md={4}>
            <Skeleton variant="rectangular" height={500} sx={{ borderRadius: "12px" }} />
          </Grid>
        </Grid>
      </Stack>
    );
  }

  if (invoiceQuery.isError || !invoiceQuery.data) {
    return (
      <Alert severity="error">
        Invoice not found. <Button onClick={() => navigate("/invoices")}>Back to Invoices</Button>
      </Alert>
    );
  }

  const invoice = invoiceQuery.data;
  const isArchived = invoice.status === "ARCHIVED";
  const confidence = invoice.fieldConfidence;
  const vendors: Vendor[] = Array.isArray(vendorsQuery.data)
    ? vendorsQuery.data
    : Array.isArray((vendorsQuery.data as any)?.content)
    ? (vendorsQuery.data as any).content
    : [];

  return (
    <Stack spacing={3}>
      {/* Top Header */}
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ sm: "center" }}
        spacing={2}
      >
        <Stack direction="row" alignItems="center" spacing={1.5}>
          <IconButton onClick={() => navigate("/invoices")} size="small">
            <ArrowBackIcon />
          </IconButton>
          <Box>
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <Typography variant="h5" fontWeight={700}>
                {invoice.invoiceNumber || "Draft Invoice"}
              </Typography>
              <Chip
                label={invoice.status.replace("_", " ")}
                color={STATUS_COLOR[invoice.status] ?? "default"}
                size="small"
                sx={{ fontWeight: 600 }}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary">
              Submitted by {invoice.submittedBy?.fullName} on{" "}
              {new Date(invoice.createdAt).toLocaleDateString()}
            </Typography>
          </Box>
        </Stack>

        <Stack direction="row" spacing={1} flexWrap="wrap">
          {invoice.documents?.length > 0 && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<FileDownloadOutlinedIcon />}
              onClick={handleDownloadDoc}
            >
              Download PDF
            </Button>
          )}

          {!isArchived && invoice.status === "NEEDS_REVIEW" && (
            <Button
              variant="contained"
              color="primary"
              size="small"
              startIcon={<VerifiedIcon />}
              onClick={() => verifyMutation.mutate()}
              disabled={verifyMutation.isPending}
            >
              Verify Invoice
            </Button>
          )}

          {!isArchived && isAdmin && invoice.status !== "APPROVED" && (
            <Button
              variant="contained"
              color="success"
              size="small"
              startIcon={<CheckIcon />}
              onClick={() => approveMutation.mutate()}
              disabled={approveMutation.isPending}
            >
              Approve
            </Button>
          )}

          {!isArchived && isAdmin && invoice.status !== "REJECTED" && (
            <Button
              variant="outlined"
              color="error"
              size="small"
              startIcon={<BlockIcon />}
              onClick={() => setRejectDialogOpen(true)}
            >
              Reject
            </Button>
          )}

          {!isArchived && (
            <Button
              variant="outlined"
              color="inherit"
              size="small"
              startIcon={<ArchiveIcon />}
              onClick={() => archiveMutation.mutate()}
              disabled={archiveMutation.isPending}
            >
              Archive
            </Button>
          )}
        </Stack>
      </Stack>

      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}
      {successMessage && (
        <Alert severity="success" onClose={() => setSuccessMessage(null)}>
          {successMessage}
        </Alert>
      )}

      {/* Main Content Layout */}
      <Grid container spacing={3}>
        {/* Left Column: Editable Form & Line Items */}
        <Grid item xs={12} lg={8}>
          <Stack spacing={3}>
            {/* Invoice Form Card */}
            <Card>
              <CardContent sx={{ p: 3 }}>
                <Box component="form" onSubmit={handleSubmit(onSave)}>
                  <Stack spacing={2.5}>
                    <Typography variant="subtitle1" fontWeight={700}>
                      Invoice Details
                    </Typography>

                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <Autocomplete
                          options={vendors}
                          getOptionLabel={(option) => option?.name || ""}
                          isOptionEqualToValue={(option, val) => option?.id === val?.id}
                          value={vendors.find((v) => v.id === watch("vendorId")) || null}
                          disabled={isArchived}
                          onChange={(_, val) => setValue("vendorId", val ? val.id : null)}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              label="Vendor"
                              placeholder="Select or search vendor..."
                              helperText={
                                confidenceHelperText(confidence, "vendor") ||
                                (invoice.vendorNameRaw
                                  ? `Extracted raw name: ${invoice.vendorNameRaw}`
                                  : undefined)
                              }
                            />
                          )}
                        />
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <TextField
                          label="Invoice Number"
                          fullWidth
                          disabled={isArchived}
                          {...register("invoiceNumber")}
                          helperText={confidenceHelperText(confidence, "invoiceNumber")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <TextField
                          label="Invoice Date"
                          type="date"
                          fullWidth
                          InputLabelProps={{ shrink: true }}
                          disabled={isArchived}
                          {...register("invoiceDate")}
                          helperText={confidenceHelperText(confidence, "invoiceDate")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <TextField
                          label="Due Date"
                          type="date"
                          fullWidth
                          InputLabelProps={{ shrink: true }}
                          disabled={isArchived}
                          {...register("dueDate")}
                          helperText={confidenceHelperText(confidence, "dueDate")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={3}>
                        <TextField
                          label="Subtotal Amount"
                          type="number"
                          inputProps={{ step: "any", min: "0" }}
                          fullWidth
                          disabled={isArchived}
                          {...register("subtotalAmount")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={3}>
                        <TextField
                          label="Tax Amount"
                          type="number"
                          inputProps={{ step: "any", min: "0" }}
                          fullWidth
                          disabled={isArchived}
                          {...register("taxAmount")}
                          helperText={confidenceHelperText(confidence, "taxAmount")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={3}>
                        <TextField
                          label="Discount Amount"
                          type="number"
                          inputProps={{ step: "any", min: "0" }}
                          fullWidth
                          disabled={isArchived}
                          {...register("discountAmount")}
                        />
                      </Grid>

                      <Grid item xs={12} sm={3}>
                        <TextField
                          label="Total Amount"
                          type="number"
                          inputProps={{ step: "any", min: "0" }}
                          fullWidth
                          disabled={isArchived}
                          {...register("totalAmount")}
                          helperText={confidenceHelperText(confidence, "totalAmount")}
                        />
                      </Grid>

                      <Grid item xs={12}>
                        <TextField
                          label="Notes / Comments"
                          multiline
                          rows={2}
                          fullWidth
                          disabled={isArchived}
                          {...register("notes")}
                        />
                      </Grid>
                    </Grid>

                    <Divider sx={{ my: 1 }} />

                    {/* Line Items Table */}
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                    >
                      <Typography variant="subtitle1" fontWeight={700}>
                        Line Items ({fields.length})
                      </Typography>
                      {!isArchived && (
                        <Button
                          size="small"
                          startIcon={<AddIcon />}
                          onClick={() =>
                            append({
                              description: "",
                              quantity: 1,
                              unitPrice: 0,
                              taxAmount: 0,
                              discountAmount: 0,
                              totalAmount: 0,
                            })
                          }
                        >
                          Add Item
                        </Button>
                      )}
                    </Stack>

                    <Paper variant="outlined" sx={{ borderRadius: "10px", overflow: "hidden" }}>
                      <Table size="small">
                        <TableHead sx={{ bgcolor: "#F8FAFC" }}>
                          <TableRow>
                            <TableCell sx={{ fontWeight: 600, width: "40%" }}>Description</TableCell>
                            <TableCell sx={{ fontWeight: 600, width: "15%" }}>Qty</TableCell>
                            <TableCell sx={{ fontWeight: 600, width: "20%" }}>Unit Price</TableCell>
                            <TableCell sx={{ fontWeight: 600, width: "20%" }}>Total</TableCell>
                            {!isArchived && <TableCell sx={{ width: "5%" }} />}
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {fields.map((field, index) => (
                            <TableRow key={field.id}>
                              <TableCell>
                                <TextField
                                  size="small"
                                  fullWidth
                                  disabled={isArchived}
                                  placeholder="Item description"
                                  {...register(`lineItems.${index}.description` as const)}
                                />
                              </TableCell>
                              <TableCell>
                                <TextField
                                  size="small"
                                  type="number"
                                  inputProps={{ step: "any", min: "0" }}
                                  fullWidth
                                  disabled={isArchived}
                                  {...register(`lineItems.${index}.quantity` as const)}
                                />
                              </TableCell>
                              <TableCell>
                                <TextField
                                  size="small"
                                  type="number"
                                  inputProps={{ step: "any", min: "0" }}
                                  fullWidth
                                  disabled={isArchived}
                                  {...register(`lineItems.${index}.unitPrice` as const)}
                                />
                              </TableCell>
                              <TableCell>
                                <TextField
                                  size="small"
                                  type="number"
                                  inputProps={{ step: "any", min: "0" }}
                                  fullWidth
                                  disabled={isArchived}
                                  {...register(`lineItems.${index}.totalAmount` as const)}
                                />
                              </TableCell>
                              {!isArchived && (
                                <TableCell>
                                  <IconButton
                                    size="small"
                                    color="error"
                                    onClick={() => remove(index)}
                                  >
                                    <DeleteOutlineIcon fontSize="small" />
                                  </IconButton>
                                </TableCell>
                              )}
                            </TableRow>
                          ))}
                          {fields.length === 0 && (
                            <TableRow>
                              <TableCell colSpan={5} align="center" sx={{ py: 2, color: "text.secondary" }}>
                                No line items extracted. Click &quot;Add Item&quot; to add manually.
                              </TableCell>
                            </TableRow>
                          )}
                        </TableBody>
                      </Table>
                    </Paper>

                    {!isArchived && (
                      <Box sx={{ display: "flex", justifyContent: "flex-end", pt: 1 }}>
                        <Button
                          type="submit"
                          variant="contained"
                          disabled={updateMutation.isPending}
                        >
                          {updateMutation.isPending ? "Saving..." : "Save Invoice Changes"}
                        </Button>
                      </Box>
                    )}
                  </Stack>
                </Box>
              </CardContent>
            </Card>
          </Stack>
        </Grid>

        {/* Right Column: Validation Checklist, Duplicates & AI Q&A */}
        <Grid item xs={12} lg={4}>
          <Stack spacing={3}>
            {/* Validation Checklist Card */}
            <Card>
              <CardContent sx={{ p: 2.5 }}>
                <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
                  Validation Checklist
                </Typography>
                <Stack spacing={1.2}>
                  {invoice.validationResults?.map((res, idx) => (
                    <Box
                      key={idx}
                      sx={{
                        p: 1.2,
                        borderRadius: "8px",
                        bgcolor:
                          res.status === "PASS"
                            ? "#F0FDF4"
                            : res.status === "WARNING"
                            ? "#FFFBEB"
                            : "#FEF2F2",
                        border: "1px solid",
                        borderColor:
                          res.status === "PASS"
                            ? "#BBF7D0"
                            : res.status === "WARNING"
                            ? "#FED7AA"
                            : "#FECACA",
                        display: "flex",
                        alignItems: "flex-start",
                        gap: 1,
                      }}
                    >
                      {VALIDATION_ICON[res.status]}
                      <Box>
                        <Typography
                          variant="caption"
                          fontWeight={700}
                          color={
                            res.status === "PASS"
                              ? "#15803D"
                              : res.status === "WARNING"
                              ? "#B45309"
                              : "#B91C1C"
                          }
                        >
                          {res.ruleCode}
                        </Typography>
                        <Typography variant="body2" sx={{ fontSize: "0.82rem" }}>
                          {res.message}
                        </Typography>
                      </Box>
                    </Box>
                  ))}
                  {(!invoice.validationResults || invoice.validationResults.length === 0) && (
                    <Typography variant="body2" color="text.secondary">
                      No validation rules evaluated yet.
                    </Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>

            {/* Duplicate Detection Card */}
            {invoice.duplicateWarnings && invoice.duplicateWarnings.length > 0 && (
              <Card sx={{ borderColor: "#FED7AA", bgcolor: "#FFFBEB" }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                    <WarningAmberOutlinedIcon color="warning" />
                    <Typography variant="subtitle1" fontWeight={700} color="#9A3412">
                      Potential Duplicate Detected
                    </Typography>
                  </Stack>
                  <Stack spacing={1}>
                    {invoice.duplicateWarnings.map((dup, i) => (
                      <Paper
                        key={i}
                        variant="outlined"
                        sx={{ p: 1.5, borderRadius: "8px", bgcolor: "#FFFFFF" }}
                      >
                        <Typography variant="body2" fontWeight={600}>
                          {dup.reason}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Match confidence: {Math.round(dup.probability * 100)}%
                        </Typography>
                      </Paper>
                    ))}
                  </Stack>
                </CardContent>
              </Card>
            )}

            {/* Embedded AI Assistant Card */}
            <Card sx={{ bgcolor: "#FFFFFF" }}>
              <CardContent sx={{ p: 2.5 }}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
                  <Box
                    sx={{
                      width: 28,
                      height: 28,
                      borderRadius: "6px",
                      background: "linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "#FFF",
                    }}
                  >
                    <AutoAwesomeIcon sx={{ fontSize: 16 }} />
                  </Box>
                  <Typography variant="subtitle1" fontWeight={700}>
                    AI Invoice Q&A
                  </Typography>
                </Stack>

                {/* Prebuilt Prompts */}
                <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 1.5, gap: 0.5 }}>
                  <Chip
                    size="small"
                    label="Summarize invoice"
                    onClick={() => handleAskAi("Summarize this invoice")}
                    sx={{ cursor: "pointer", fontSize: "0.75rem" }}
                  />
                  <Chip
                    size="small"
                    label="List line items"
                    onClick={() => handleAskAi("List all line items and their totals")}
                    sx={{ cursor: "pointer", fontSize: "0.75rem" }}
                  />
                  <Chip
                    size="small"
                    label="Check tax calculation"
                    onClick={() => handleAskAi("Is the tax calculation accurate?")}
                    sx={{ cursor: "pointer", fontSize: "0.75rem" }}
                  />
                </Stack>

                {/* Conversation Box */}
                <Paper
                  variant="outlined"
                  sx={{
                    p: 1.5,
                    maxHeight: 240,
                    minHeight: 120,
                    overflowY: "auto",
                    borderRadius: "10px",
                    bgcolor: "#F8FAFC",
                    mb: 1.5,
                  }}
                >
                  {aiConversation.length === 0 ? (
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ textAlign: "center", py: 4 }}
                    >
                      Ask questions about this invoice to extract insights.
                    </Typography>
                  ) : (
                    <Stack spacing={1.5}>
                      {aiConversation.map((msg, i) => (
                        <Box
                          key={i}
                          sx={{
                            p: 1,
                            borderRadius: "8px",
                            bgcolor: msg.role === "user" ? "#EFF6FF" : "#FFFFFF",
                            border: msg.role === "assistant" ? "1px solid #E2E8F0" : "none",
                            alignSelf: msg.role === "user" ? "flex-end" : "flex-start",
                            maxWidth: "90%",
                          }}
                        >
                          <Typography
                            variant="caption"
                            fontWeight={700}
                            color={msg.role === "user" ? "primary" : "text.secondary"}
                          >
                            {msg.role === "user" ? "You" : "AI Assistant"}
                          </Typography>
                          <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                            {msg.content}
                          </Typography>
                        </Box>
                      ))}
                      {isAskingAi && (
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1, p: 1 }}>
                          <CircularProgress size={16} />
                          <Typography variant="caption" color="text.secondary">
                            Thinking...
                          </Typography>
                        </Box>
                      )}
                    </Stack>
                  )}
                </Paper>

                {/* Input form */}
                <Stack direction="row" spacing={1}>
                  <TextField
                    size="small"
                    fullWidth
                    placeholder="Ask about this invoice..."
                    value={aiQuestion}
                    onChange={(e) => setAiQuestion(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && !e.shiftKey) {
                        e.preventDefault();
                        handleAskAi();
                      }
                    }}
                  />
                  <IconButton
                    color="primary"
                    onClick={() => handleAskAi()}
                    disabled={!aiQuestion.trim() || isAskingAi}
                  >
                    <SendIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Reject Invoice</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Reason for rejection"
            fullWidth
            multiline
            rows={3}
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="e.g. Incorrect tax invoice format, duplicate billing..."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={() => rejectMutation.mutate(rejectReason)}
            color="error"
            variant="contained"
            disabled={!rejectReason.trim() || rejectMutation.isPending}
          >
            {rejectMutation.isPending ? "Rejecting..." : "Reject Invoice"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
