import { httpClient } from "@/api/httpClient";
import type { Invoice, InvoiceLineItem, InvoiceStatus, InvoiceSummary, Page } from "@/types/invoice";
import type { AiAnswerResponse } from "@/types/ai";

export interface InvoiceLineItemPayload {
  lineOrder?: number;
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

  approve: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/approve`).then((r) => r.data),

  reject: (id: string, reason: string) =>
    httpClient.post<Invoice>(`/api/invoices/${id}/reject`, { reason }).then((r) => r.data),

  archive: (id: string) => httpClient.post<Invoice>(`/api/invoices/${id}/archive`).then((r) => r.data),

  askQuestion: (id: string, question: string) =>
    httpClient.post<AiAnswerResponse>(`/api/invoices/${id}/ask`, { question }).then((r) => r.data),

  downloadDocument: (id: string) =>
    httpClient.get(`/api/invoices/${id}/document`, { responseType: "blob" }).then((r) => r.data as Blob),

  exportCsv: (status?: InvoiceStatus | "") =>
    httpClient
      .get("/api/invoices/export.csv", { params: { status: status || undefined }, responseType: "blob" })
      .then((r) => r.data as Blob),
};

export type { InvoiceLineItem };
