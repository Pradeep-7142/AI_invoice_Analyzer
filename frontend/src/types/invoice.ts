export type InvoiceStatus =
  | "UPLOADED"
  | "PROCESSING"
  | "NEEDS_REVIEW"
  | "VERIFIED"
  | "APPROVED"
  | "REJECTED"
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
  id?: string;
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
  ruleCode: string;
  status: ValidationStatus;
  message: string;
}

export interface DuplicateWarning {
  invoiceId: string;
  invoiceNumber: string | null;
  probability: number;
  reason: string;
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
  rejectionReason: string | null;
  fieldConfidence: Record<string, number> | null;
  validationResults: ValidationResult[];
  duplicateWarnings: DuplicateWarning[];
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
