import { httpClient } from "@/api/httpClient";
import type { Page } from "@/types/invoice";
import type { PaymentStatus, PaymentSummary } from "@/types/finance";

export const paymentsApi = {
  list: (status?: PaymentStatus | "") =>
    httpClient
      .get<Page<PaymentSummary>>("/api/payments", { params: { status: status || undefined, size: 50 } })
      .then((r) => r.data),

  exportCsv: (status?: PaymentStatus | "") =>
    httpClient
      .get("/api/payments/export.csv", { params: { status: status || undefined }, responseType: "blob" })
      .then((r) => r.data as Blob),
};
