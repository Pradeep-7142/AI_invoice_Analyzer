import { httpClient } from "@/api/httpClient";
import type { Invoice, InvoiceLineItem, InvoiceStatus, InvoiceSummary, Page } from "@/types/invoice";
import type { PaymentMethod } from "@/types/finance";

export interface SchedulePaymentPayload {
  amount: number;
  scheduledDate: string;
  method: PaymentMethod;
  reference?: string;
  notes?: string;
}

export interface InvoiceLineItemPayload {
  description: string;
  quantity: number;
  unitPrice: number;
  taxAmount?: number;
  discountAmount?: number;
  totalAmount: number;
}

export interface InvoiceUpdatePayload {
  vendorId?: string | null;
  invoiceNumber?: string | null;
  invoiceDate?: string | null;
  dueDate?: string | null;
  currency?: string;
  subtotalAmount?: number | null;
  taxAmount?: number | null;
  discountAmount?: number | null;
  totalAmount?: number | null;
  notes?: string | null;
  lineItems: InvoiceLineItemPayload[];
}

export const invoicesApi = {
  list: (status?: InvoiceStatus | "") =>
    httpClient
      .get<Page<InvoiceSummary>>("/api/invoices", { params: { status: status || undefined, size: 50 } })
      .then((r) => r.data),

  get: (id: string) => httpClient.get<Invoice>(`/api/invoices/${id}`).then((r) => r.data),

  upload: (file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return httpClient
      .post<Invoice>("/api/invoices/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => r.data);
  },

  update: (id: string, payload: InvoiceUpdatePayload) =>
    httpClient.put<Invoice>(`/api/invoices/${id}`, payload).then((r) => r.data),

  verify: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/verify`).then((r) => r.data),

  archive: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/archive`).then((r) => r.data),

  submitForApproval: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/submit-for-approval`).then((r) => r.data),

  approve: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/approve`).then((r) => r.data),

  reject: (id: string, reason: string) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/reject`, { reason }).then((r) => r.data),

  dispute: (id: string, reason: string) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/dispute`, { reason }).then((r) => r.data),

  resolveDispute: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/resolve-dispute`).then((r) => r.data),

  schedulePayment: (id: string, payload: SchedulePaymentPayload) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/payments`, payload).then((r) => r.data),

  completePayment: (id: string, paymentId: string) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/payments/${paymentId}/complete`).then((r) => r.data),

  cancelPayment: (id: string, paymentId: string) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/payments/${paymentId}/cancel`).then((r) => r.data),

  // The document endpoint requires the same Bearer auth as everything else,
  // so it can't be linked to directly (e.g. <a href>) — fetch it as a blob
  // and hand the caller an object URL to embed/download instead.
  downloadDocument: (id: string) =>
    httpClient.get(`/api/invoices/${id}/document`, { responseType: "blob" }).then((r) => r.data as Blob),

  exportCsv: (status?: InvoiceStatus | "") =>
    httpClient
      .get("/api/invoices/export.csv", { params: { status: status || undefined }, responseType: "blob" })
      .then((r) => r.data as Blob),
};

export type { InvoiceLineItem };
