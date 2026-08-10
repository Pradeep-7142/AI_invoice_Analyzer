import type { ApprovalDecision, ApprovalRole, Payment, RecurringExpense } from "@/types/finance";

export type InvoiceStatus =
  | "UPLOADED"
  | "PROCESSING"
  | "NEEDS_REVIEW"
  | "VERIFIED"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "PAYMENT_SCHEDULED"
  | "PARTIALLY_PAID"
  | "PAID"
  | "OVERDUE"
  | "DISPUTED"
  | "ARCHIVED";

export interface VendorSummary {
  id: string;
  name: string;
}

export interface Vendor {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  gstin: string | null;
  taxId: string | null;
  category: string | null;
  notes: string | null;
  status: "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
}

export interface InvoiceLineItem {
  id: string;
  lineOrder: number;
  description: string;
  quantity: number;
  unitPrice: number;
  taxAmount: number;
  discountAmount: number;
  totalAmount: number;
}

export type DocumentType =
  | "INVOICE"
  | "RECEIPT"
  | "CREDIT_NOTE"
  | "DEBIT_NOTE"
  | "PURCHASE_ORDER"
  | "STATEMENT"
  | "OTHER"
  | "UNKNOWN";

export interface InvoiceDocument {
  id: string;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  processingStatus: string;
  documentType: DocumentType | null;
  ocrConfidence: number | null;
  rejectionReason: string | null;
  createdAt: string;
}

export interface InvoiceSummary {
  id: string;
  vendor: VendorSummary | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  dueDate: string | null;
  currency: string;
  totalAmount: number | null;
  status: InvoiceStatus;
  submittedByName: string;
  createdAt: string;
}

export type ValidationStatus = "PASS" | "WARNING" | "ERROR";

export interface ValidationResult {
  rule: string;
  status: ValidationStatus;
  message: string;
}

export interface DuplicateWarning {
  invoiceId: string;
  invoiceNumber: string | null;
  probability: number;
  reason: string;
}

export interface Anomaly {
  severity: "MEDIUM" | "HIGH";
  explanation: string;
}

export interface Invoice {
  id: string;
  vendor: VendorSummary | null;
  vendorNameRaw: string | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  dueDate: string | null;
  currency: string;
  subtotalAmount: number | null;
  taxAmount: number | null;
  discountAmount: number | null;
  totalAmount: number | null;
  status: InvoiceStatus;
  notes: string | null;
  disputeReason: string | null;
  fieldConfidence: Record<string, number> | null;
  validationResults: ValidationResult[];
  duplicateWarnings: DuplicateWarning[];
  anomaly: Anomaly | null;
  recurringExpense: RecurringExpense | null;
  riskScore: number;
  riskReasons: string[];
  requiredApprovalRole: ApprovalRole | null;
  approvalHistory: ApprovalDecision[];
  payments: Payment[];
  paidAmount: number | null;
  outstandingAmount: number | null;
  submittedBy: { id: string; email: string; fullName: string };
  lineItems: InvoiceLineItem[];
  documents: InvoiceDocument[];
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
