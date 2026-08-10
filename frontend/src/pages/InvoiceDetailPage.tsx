import { useEffect, useState } from "react";
import { useForm, useFieldArray } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
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
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AddIcon from "@mui/icons-material/Add";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import EventRepeatOutlinedIcon from "@mui/icons-material/EventRepeatOutlined";
import { Link as RouterLink } from "react-router-dom";
import { invoicesApi, type InvoiceUpdatePayload, type SchedulePaymentPayload } from "@/api/invoicesApi";
import { vendorsApi } from "@/api/vendorsApi";
import { useAuth } from "@/features/auth/AuthContext";
import { apiErrorMessage } from "@/utils/apiErrorMessage";
import type { ValidationStatus } from "@/types/invoice";
import type { PaymentMethod, PaymentStatus } from "@/types/finance";

const CAN_MANAGE_INVOICES = new Set(["ORGANIZATION_ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"]);
const CAN_DECIDE_APPROVALS = new Set(["ORGANIZATION_ADMIN", "FINANCE_MANAGER"]);
const PAYABLE_STATUSES = new Set(["APPROVED", "PAYMENT_SCHEDULED", "PARTIALLY_PAID", "OVERDUE"]);
const DISPUTABLE_STATUSES = new Set([
  "VERIFIED", "PENDING_APPROVAL", "APPROVED", "PAYMENT_SCHEDULED", "PARTIALLY_PAID", "OVERDUE", "PAID",
]);
const PAYMENT_METHODS: PaymentMethod[] = ["BANK_TRANSFER", "CARD", "CHEQUE", "CASH", "OTHER"];

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

const PAYMENT_STATUS_COLOR: Record<PaymentStatus, "default" | "warning" | "success" | "error"> = {
  SCHEDULED: "warning",
  COMPLETED: "success",
  FAILED: "error",
  CANCELLED: "default",
};

function riskColor(score: number): "success" | "warning" | "error" {
  if (score >= 50) return "error";
  if (score >= 20) return "warning";
  return "success";
}

const VALIDATION_ICON: Record<ValidationStatus, JSX.Element> = {
  PASS: <CheckCircleOutlineIcon fontSize="small" color="success" />,
  WARNING: <WarningAmberOutlinedIcon fontSize="small" color="warning" />,
  ERROR: <ErrorOutlineIcon fontSize="small" color="error" />,
};

// Matches the backend default (invoiceiq.ai.confidence-threshold) — not
// exposed via API today, so kept in sync by hand.
const CONFIDENCE_THRESHOLD = 0.75;

function confidenceHelperText(fieldConfidence: Record<string, number> | null | undefined, field: string): string | undefined {
  const confidence = fieldConfidence?.[field];
  if (confidence === undefined) return undefined;
  const percent = Math.round(confidence * 100);
  return confidence < CONFIDENCE_THRESHOLD ? `⚠ AI extracted this with ${percent}% confidence — please verify` : `AI extracted this with ${percent}% confidence`;
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
  lineItems: { description: string; quantity: string; unitPrice: string; taxAmount: string; discountAmount: string; totalAmount: string }[];
}

function toFormNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? "" : String(value);
}

function toApiNumber(value: string): number | null {
  return value.trim() === "" ? null : Number(value);
}

function DocumentViewer({ invoiceId }: { invoiceId: string }) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [contentType, setContentType] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let currentUrl: string | null = null;
    invoicesApi
      .downloadDocument(invoiceId)
      .then((blob) => {
        currentUrl = URL.createObjectURL(blob);
        setContentType(blob.type);
        setObjectUrl(currentUrl);
      })
      .catch((err) => setError(apiErrorMessage(err, "Unable to load the original document.")));
    return () => {
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [invoiceId]);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!objectUrl) return <Skeleton variant="rectangular" height={500} />;

  return contentType.startsWith("image/") ? (
    <Box component="img" src={objectUrl} alt="Invoice document" sx={{ width: "100%", borderRadius: 1 }} />
  ) : (
    <Box component="iframe" src={objectUrl} title="Invoice document" sx={{ width: "100%", height: 600, border: "none" }} />
  );
}

export function InvoiceDetailPage() {
  const { invoiceId } = useParams<{ invoiceId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { role, user } = useAuth();
  const canManage = role !== null && CAN_MANAGE_INVOICES.has(role);
  const canDecideApprovals = role !== null && CAN_DECIDE_APPROVALS.has(role);
  const [actionError, setActionError] = useState<string | null>(null);

  const invoiceQuery = useQuery({
    queryKey: ["invoice", invoiceId],
    queryFn: () => invoicesApi.get(invoiceId!),
    enabled: !!invoiceId,
  });

  const vendorsQuery = useQuery({ queryKey: ["vendors", ""], queryFn: () => vendorsApi.list() });

  const { register, control, handleSubmit, reset, watch, setValue } = useForm<FormValues>({
    defaultValues: { lineItems: [] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "lineItems" });

  useEffect(() => {
    const invoice = invoiceQuery.data;
    if (!invoice) return;
    reset({
      vendorId: invoice.vendor?.id ?? null,
      invoiceNumber: invoice.invoiceNumber ?? "",
      invoiceDate: invoice.invoiceDate ?? "",
      dueDate: invoice.dueDate ?? "",
      currency: invoice.currency,
      subtotalAmount: toFormNumber(invoice.subtotalAmount),
      taxAmount: toFormNumber(invoice.taxAmount),
      discountAmount: toFormNumber(invoice.discountAmount),
      totalAmount: toFormNumber(invoice.totalAmount),
      notes: invoice.notes ?? "",
      lineItems: invoice.lineItems.map((li) => ({
        description: li.description,
        quantity: String(li.quantity),
        unitPrice: String(li.unitPrice),
        taxAmount: String(li.taxAmount),
        discountAmount: String(li.discountAmount),
        totalAmount: String(li.totalAmount),
      })),
    });
  }, [invoiceQuery.data, reset]);

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) => {
      const payload: InvoiceUpdatePayload = {
        vendorId: values.vendorId,
        invoiceNumber: values.invoiceNumber || null,
        invoiceDate: values.invoiceDate || null,
        dueDate: values.dueDate || null,
        currency: values.currency || "INR",
        subtotalAmount: toApiNumber(values.subtotalAmount),
        taxAmount: toApiNumber(values.taxAmount),
        discountAmount: toApiNumber(values.discountAmount),
        totalAmount: toApiNumber(values.totalAmount),
        notes: values.notes || null,
        lineItems: values.lineItems.map((li) => ({
          description: li.description,
          quantity: Number(li.quantity),
          unitPrice: Number(li.unitPrice),
          taxAmount: toApiNumber(li.taxAmount) ?? 0,
          discountAmount: toApiNumber(li.discountAmount) ?? 0,
          totalAmount: Number(li.totalAmount),
        })),
      };
      return invoicesApi.update(invoiceId!, payload);
    },
    onSuccess: (invoice) => {
      queryClient.setQueryData(["invoice", invoiceId], invoice);
      setActionError(null);
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const verifyMutation = useMutation({
    mutationFn: () => invoicesApi.verify(invoiceId!),
    onSuccess: (invoice) => {
      queryClient.setQueryData(["invoice", invoiceId], invoice);
      setActionError(null);
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const archiveMutation = useMutation({
    mutationFn: () => invoicesApi.archive(invoiceId!),
    onSuccess: (invoice) => {
      queryClient.setQueryData(["invoice", invoiceId], invoice);
      setActionError(null);
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  function onActionSuccess(invoice: NonNullable<typeof invoiceQuery.data>) {
    queryClient.setQueryData(["invoice", invoiceId], invoice);
    setActionError(null);
  }

  const submitForApprovalMutation = useMutation({
    mutationFn: () => invoicesApi.submitForApproval(invoiceId!),
    onSuccess: onActionSuccess,
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const approveMutation = useMutation({
    mutationFn: () => invoicesApi.approve(invoiceId!),
    onSuccess: onActionSuccess,
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const rejectMutation = useMutation({
    mutationFn: (reason: string) => invoicesApi.reject(invoiceId!, reason),
    onSuccess: (invoice) => {
      onActionSuccess(invoice);
      setRejectDialogOpen(false);
      setRejectReason("");
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const [disputeDialogOpen, setDisputeDialogOpen] = useState(false);
  const [disputeReasonInput, setDisputeReasonInput] = useState("");
  const disputeMutation = useMutation({
    mutationFn: (reason: string) => invoicesApi.dispute(invoiceId!, reason),
    onSuccess: (invoice) => {
      onActionSuccess(invoice);
      setDisputeDialogOpen(false);
      setDisputeReasonInput("");
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const resolveDisputeMutation = useMutation({
    mutationFn: () => invoicesApi.resolveDispute(invoiceId!),
    onSuccess: onActionSuccess,
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
  const [paymentForm, setPaymentForm] = useState({
    amount: "", scheduledDate: new Date().toISOString().slice(0, 10), method: "BANK_TRANSFER" as PaymentMethod, reference: "", notes: "",
  });
  const schedulePaymentMutation = useMutation({
    mutationFn: (payload: SchedulePaymentPayload) => invoicesApi.schedulePayment(invoiceId!, payload),
    onSuccess: (invoice) => {
      onActionSuccess(invoice);
      setPaymentDialogOpen(false);
      setPaymentForm({ amount: "", scheduledDate: new Date().toISOString().slice(0, 10), method: "BANK_TRANSFER", reference: "", notes: "" });
    },
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const completePaymentMutation = useMutation({
    mutationFn: (paymentId: string) => invoicesApi.completePayment(invoiceId!, paymentId),
    onSuccess: onActionSuccess,
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  const cancelPaymentMutation = useMutation({
    mutationFn: (paymentId: string) => invoicesApi.cancelPayment(invoiceId!, paymentId),
    onSuccess: onActionSuccess,
    onError: (error) => setActionError(apiErrorMessage(error)),
  });

  if (invoiceQuery.isLoading) {
    return <Skeleton variant="rectangular" height={400} />;
  }

  if (invoiceQuery.isError) {
    return <Alert severity="error">{apiErrorMessage(invoiceQuery.error, "Invoice not found.")}</Alert>;
  }

  const invoice = invoiceQuery.data!;
  const isArchived = invoice.status === "ARCHIVED";
  const fieldsDisabled = !canManage || isArchived;
  const vendors = vendorsQuery.data?.content ?? [];
  const selectedVendorId = watch("vendorId");
  const latestDocument = invoice.documents[0];
  const extractedFieldCount = Object.keys(invoice.fieldConfidence ?? {}).length;
  const isSubmitter = invoice.submittedBy.id === user?.id;
  const canDecideThisInvoice = canDecideApprovals && !isSubmitter;
  const isPayable = PAYABLE_STATUSES.has(invoice.status);
  const isDisputable = DISPUTABLE_STATUSES.has(invoice.status);
  const outstanding = invoice.outstandingAmount ?? invoice.totalAmount ?? 0;

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1}>
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
          <Button onClick={() => navigate("/invoices")}>&larr; Back</Button>
          <Typography variant="h4" fontWeight={700}>
            {invoice.invoiceNumber ?? "Untitled invoice"}
          </Typography>
          <Chip label={invoice.status.replaceAll("_", " ")} color={STATUS_COLOR[invoice.status] ?? "default"} />
          <Chip
            label={`Risk ${invoice.riskScore}/100`}
            color={riskColor(invoice.riskScore)}
            variant={invoice.riskScore === 0 ? "outlined" : "filled"}
          />
          {invoice.status === "PENDING_APPROVAL" && invoice.requiredApprovalRole && (
            <Chip
              size="small"
              variant="outlined"
              label={`Needs ${invoice.requiredApprovalRole.replaceAll("_", " ")} approval`}
            />
          )}
        </Stack>
        {canManage && (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {invoice.status === "NEEDS_REVIEW" && (
              <Button variant="contained" onClick={() => verifyMutation.mutate()} disabled={verifyMutation.isPending}>
                Verify
              </Button>
            )}
            {invoice.status === "VERIFIED" && (
              <Button
                variant="contained"
                onClick={() => submitForApprovalMutation.mutate()}
                disabled={submitForApprovalMutation.isPending}
              >
                Submit for approval
              </Button>
            )}
            {invoice.status === "PENDING_APPROVAL" && canDecideApprovals && (
              <>
                <Button
                  variant="contained"
                  color="success"
                  onClick={() => approveMutation.mutate()}
                  disabled={approveMutation.isPending || !canDecideThisInvoice}
                  title={!canDecideThisInvoice ? "You cannot approve an invoice you submitted yourself." : undefined}
                >
                  Approve
                </Button>
                <Button
                  color="error"
                  onClick={() => setRejectDialogOpen(true)}
                  disabled={!canDecideThisInvoice}
                  title={!canDecideThisInvoice ? "You cannot reject an invoice you submitted yourself." : undefined}
                >
                  Reject
                </Button>
              </>
            )}
            {isPayable && (
              <Button variant="contained" onClick={() => setPaymentDialogOpen(true)}>
                Schedule payment
              </Button>
            )}
            {invoice.status === "DISPUTED" ? (
              <Button onClick={() => resolveDisputeMutation.mutate()} disabled={resolveDisputeMutation.isPending}>
                Resolve dispute
              </Button>
            ) : (
              isDisputable && <Button color="warning" onClick={() => setDisputeDialogOpen(true)}>Dispute</Button>
            )}
            {!isArchived && (
              <Button color="inherit" onClick={() => archiveMutation.mutate()} disabled={archiveMutation.isPending}>
                Archive
              </Button>
            )}
          </Stack>
        )}
      </Stack>

      {actionError && <Alert severity="error" onClose={() => setActionError(null)}>{actionError}</Alert>}

      {invoice.status === "DISPUTED" && invoice.disputeReason && (
        <Alert severity="error">
          <strong>Disputed:</strong> {invoice.disputeReason}
        </Alert>
      )}

      {latestDocument?.processingStatus === "REJECTED" && (
        <Alert severity="error">
          <strong>Document rejected:</strong> {latestDocument.rejectionReason}. You can still enter the invoice
          details manually below, or replace the document by uploading a new invoice.
        </Alert>
      )}
      {latestDocument?.processingStatus === "NEEDS_REVIEW" && (
        <Alert severity="info">
          No text could be extracted from this document automatically (it may be a scanned image without OCR
          available, or have no selectable text). Please enter the invoice details manually below.
        </Alert>
      )}
      {latestDocument?.processingStatus === "PROCESSED" && extractedFieldCount > 0 && (
        <Alert severity="success">
          AI extracted {extractedFieldCount} field{extractedFieldCount === 1 ? "" : "s"} from this document.
          Fields below a 75% confidence are flagged — please verify them before approving.
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Original document
            </Typography>
            <DocumentViewer invoiceId={invoice.id} />
          </Paper>
        </Grid>

        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 2 }} component="form" onSubmit={handleSubmit((v) => saveMutation.mutate(v))}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Autocomplete
                  disabled={fieldsDisabled}
                  options={vendors}
                  getOptionLabel={(v) => v.name}
                  value={vendors.find((v) => v.id === selectedVendorId) ?? null}
                  onChange={(_, value) => setValue("vendorId", value?.id ?? null)}
                  renderInput={(params) => <TextField {...params} label="Vendor" />}
                />
                {!selectedVendorId && invoice.vendorNameRaw && (
                  <Typography variant="caption" color="warning.main">
                    ⚠ AI detected vendor name "{invoice.vendorNameRaw}" but found no matching vendor record —
                    create one on the Vendors page, then select it here.
                  </Typography>
                )}
              </Grid>
              <Grid item xs={6}>
                <TextField
                  label="Invoice number"
                  fullWidth
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "invoiceNumber")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.invoiceNumber ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("invoiceNumber")}
                />
              </Grid>
              <Grid item xs={3}>
                <TextField
                  label="Invoice date"
                  type="date"
                  fullWidth
                  InputLabelProps={{ shrink: true }}
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "invoiceDate")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.invoiceDate ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("invoiceDate")}
                />
              </Grid>
              <Grid item xs={3}>
                <TextField
                  label="Due date"
                  type="date"
                  fullWidth
                  InputLabelProps={{ shrink: true }}
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "dueDate")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.dueDate ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("dueDate")}
                />
              </Grid>

              <Grid item xs={3}>
                <TextField label="Currency" fullWidth disabled={fieldsDisabled} {...register("currency")} />
              </Grid>
              <Grid item xs={3}>
                <TextField
                  label="Subtotal"
                  type="number"
                  fullWidth
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "subtotalAmount")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.subtotalAmount ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("subtotalAmount")}
                />
              </Grid>
              <Grid item xs={3}>
                <TextField
                  label="Tax"
                  type="number"
                  fullWidth
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "taxAmount")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.taxAmount ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("taxAmount")}
                />
              </Grid>
              <Grid item xs={3}>
                <TextField
                  label="Total"
                  type="number"
                  fullWidth
                  disabled={fieldsDisabled}
                  helperText={confidenceHelperText(invoice.fieldConfidence, "totalAmount")}
                  FormHelperTextProps={{ sx: { color: (invoice.fieldConfidence?.totalAmount ?? 1) < CONFIDENCE_THRESHOLD ? "warning.main" : undefined } }}
                  {...register("totalAmount")}
                />
              </Grid>

              <Grid item xs={12}>
                <TextField label="Notes" fullWidth multiline minRows={2} disabled={fieldsDisabled} {...register("notes")} />
              </Grid>

              <Grid item xs={12}>
                <Divider sx={{ my: 1 }} />
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                  <Typography variant="subtitle2">Line items</Typography>
                  {!fieldsDisabled && (
                    <Button
                      size="small"
                      startIcon={<AddIcon />}
                      onClick={() =>
                        append({ description: "", quantity: "1", unitPrice: "0", taxAmount: "0", discountAmount: "0", totalAmount: "0" })
                      }
                    >
                      Add line
                    </Button>
                  )}
                </Stack>

                <Stack spacing={1}>
                  {fields.map((field, index) => (
                    <Grid container spacing={1} key={field.id} alignItems="center">
                      <Grid item xs={4}>
                        <TextField
                          size="small"
                          fullWidth
                          placeholder="Description"
                          disabled={fieldsDisabled}
                          {...register(`lineItems.${index}.description`)}
                        />
                      </Grid>
                      <Grid item xs={2}>
                        <TextField size="small" type="number" fullWidth placeholder="Qty" disabled={fieldsDisabled} {...register(`lineItems.${index}.quantity`)} />
                      </Grid>
                      <Grid item xs={2}>
                        <TextField size="small" type="number" fullWidth placeholder="Unit price" disabled={fieldsDisabled} {...register(`lineItems.${index}.unitPrice`)} />
                      </Grid>
                      <Grid item xs={3}>
                        <TextField size="small" type="number" fullWidth placeholder="Total" disabled={fieldsDisabled} {...register(`lineItems.${index}.totalAmount`)} />
                      </Grid>
                      <Grid item xs={1}>
                        {!fieldsDisabled && (
                          <IconButton size="small" onClick={() => remove(index)}>
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        )}
                      </Grid>
                    </Grid>
                  ))}
                  {fields.length === 0 && (
                    <Typography variant="body2" color="text.secondary">
                      No line items yet.
                    </Typography>
                  )}
                </Stack>
              </Grid>

              {!fieldsDisabled && (
                <Grid item xs={12}>
                  <Button type="submit" variant="contained" disabled={saveMutation.isPending}>
                    {saveMutation.isPending ? "Saving..." : "Save changes"}
                  </Button>
                </Grid>
              )}
              {isArchived && (
                <Grid item xs={12}>
                  <Alert severity="info">This invoice is archived and cannot be edited.</Alert>
                </Grid>
              )}
            </Grid>
          </Paper>
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="h6" gutterBottom>
          Risk &amp; validation
        </Typography>

        {invoice.riskReasons.length > 0 && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Why this invoice is risk-scored {invoice.riskScore}/100
            </Typography>
            <Stack component="ol" sx={{ pl: 3, m: 0 }} spacing={0.5}>
              {invoice.riskReasons.map((reason, i) => (
                <Typography key={i} component="li" variant="body2">
                  {reason}
                </Typography>
              ))}
            </Stack>
          </Box>
        )}

        {invoice.duplicateWarnings.length > 0 && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Possible duplicates
            </Typography>
            <Stack spacing={1}>
              {invoice.duplicateWarnings.map((warning) => (
                <Alert
                  key={warning.invoiceId}
                  severity={warning.probability >= 0.9 ? "error" : "warning"}
                  icon={<ContentCopyOutlinedIcon fontSize="inherit" />}
                >
                  <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
                    <span>
                      {warning.reason} ({Math.round(warning.probability * 100)}% match)
                    </span>
                    <Button
                      size="small"
                      component={RouterLink}
                      to={`/invoices/${warning.invoiceId}`}
                    >
                      View invoice
                    </Button>
                  </Stack>
                </Alert>
              ))}
            </Stack>
          </Box>
        )}

        {invoice.anomaly && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Anomaly detected
            </Typography>
            <Alert severity={invoice.anomaly.severity === "HIGH" ? "error" : "warning"}>
              <strong>{invoice.anomaly.severity} anomaly:</strong> {invoice.anomaly.explanation}
            </Alert>
          </Box>
        )}

        {invoice.recurringExpense && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Recurring expense
            </Typography>
            <Alert severity="info" icon={<EventRepeatOutlinedIcon fontSize="inherit" />}>
              {invoice.recurringExpense.explanation}
            </Alert>
          </Box>
        )}

        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          Validation checklist
        </Typography>
        <Stack spacing={0.75}>
          {invoice.validationResults.map((result) => (
            <Stack key={result.rule} direction="row" spacing={1} alignItems="flex-start">
              {VALIDATION_ICON[result.status]}
              <Typography variant="body2">{result.message}</Typography>
            </Stack>
          ))}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1.5 }}>
          Validation is informational and should be verified by a qualified professional, especially GST/tax figures.
        </Typography>

        {invoice.approvalHistory.length > 0 && (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Approval history
            </Typography>
            <Stack spacing={1}>
              {invoice.approvalHistory.map((decision) => (
                <Typography key={decision.id} variant="body2">
                  <strong>{decision.decision}</strong> by {decision.decidedByName} ({decision.requiredRole.replaceAll("_", " ")}
                  {decision.thresholdAmount != null ? `, threshold ${decision.thresholdAmount}` : ""})
                  {decision.reason ? ` — "${decision.reason}"` : ""}
                </Typography>
              ))}
            </Stack>
          </>
        )}
      </Paper>

      {(isPayable || invoice.payments.length > 0) && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="h6">Payments</Typography>
            <Typography variant="body2" color="text.secondary">
              Paid {invoice.paidAmount ?? 0} of {invoice.totalAmount ?? 0} {invoice.currency}
              {outstanding > 0 && ` — ${outstanding} ${invoice.currency} outstanding`}
            </Typography>
          </Stack>

          {invoice.payments.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No payments scheduled yet.
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Amount</TableCell>
                  <TableCell>Method</TableCell>
                  <TableCell>Scheduled</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Reference</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {invoice.payments.map((payment) => (
                  <TableRow key={payment.id}>
                    <TableCell>{payment.amount} {payment.currency}</TableCell>
                    <TableCell>{payment.method.replaceAll("_", " ")}</TableCell>
                    <TableCell>{payment.scheduledDate}</TableCell>
                    <TableCell>
                      <Chip size="small" label={payment.status} color={PAYMENT_STATUS_COLOR[payment.status]} />
                    </TableCell>
                    <TableCell>{payment.reference ?? "—"}</TableCell>
                    <TableCell align="right">
                      {payment.status === "SCHEDULED" && canManage && (
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button
                            size="small"
                            onClick={() => completePaymentMutation.mutate(payment.id)}
                            disabled={completePaymentMutation.isPending}
                          >
                            Mark completed
                          </Button>
                          <Button
                            size="small"
                            color="inherit"
                            onClick={() => cancelPaymentMutation.mutate(payment.id)}
                            disabled={cancelPaymentMutation.isPending}
                          >
                            Cancel
                          </Button>
                        </Stack>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          Submitted by
        </Typography>
        <Typography variant="body2">{invoice.submittedBy.fullName} ({invoice.submittedBy.email})</Typography>
      </Paper>

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Reject invoice</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label="Reason"
            sx={{ mt: 1 }}
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            disabled={!rejectReason.trim() || rejectMutation.isPending}
            onClick={() => rejectMutation.mutate(rejectReason.trim())}
          >
            Reject
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={disputeDialogOpen} onClose={() => setDisputeDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Dispute invoice</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label="Reason"
            sx={{ mt: 1 }}
            value={disputeReasonInput}
            onChange={(e) => setDisputeReasonInput(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDisputeDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="warning"
            disabled={!disputeReasonInput.trim() || disputeMutation.isPending}
            onClick={() => disputeMutation.mutate(disputeReasonInput.trim())}
          >
            Dispute
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={paymentDialogOpen} onClose={() => setPaymentDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Schedule payment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Amount"
              type="number"
              fullWidth
              helperText={`Outstanding balance: ${outstanding} ${invoice.currency}`}
              value={paymentForm.amount}
              onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })}
            />
            <TextField
              label="Scheduled date"
              type="date"
              fullWidth
              InputLabelProps={{ shrink: true }}
              value={paymentForm.scheduledDate}
              onChange={(e) => setPaymentForm({ ...paymentForm, scheduledDate: e.target.value })}
            />
            <TextField
              select
              label="Method"
              fullWidth
              value={paymentForm.method}
              onChange={(e) => setPaymentForm({ ...paymentForm, method: e.target.value as PaymentMethod })}
            >
              {PAYMENT_METHODS.map((m) => (
                <MenuItem key={m} value={m}>{m.replaceAll("_", " ")}</MenuItem>
              ))}
            </TextField>
            <TextField
              label="Reference"
              fullWidth
              value={paymentForm.reference}
              onChange={(e) => setPaymentForm({ ...paymentForm, reference: e.target.value })}
            />
            <TextField
              label="Notes"
              fullWidth
              multiline
              minRows={2}
              value={paymentForm.notes}
              onChange={(e) => setPaymentForm({ ...paymentForm, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!paymentForm.amount || Number(paymentForm.amount) <= 0 || schedulePaymentMutation.isPending}
            onClick={() =>
              schedulePaymentMutation.mutate({
                amount: Number(paymentForm.amount),
                scheduledDate: paymentForm.scheduledDate,
                method: paymentForm.method,
                reference: paymentForm.reference || undefined,
                notes: paymentForm.notes || undefined,
              })
            }
          >
            Schedule
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
