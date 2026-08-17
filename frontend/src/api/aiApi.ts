import { httpClient } from "@/api/httpClient";
import type { AiAnswerResponse } from "@/types/ai";

export const aiApi = {
  askInvoice: (invoiceId: string, question: string) =>
    httpClient
      .post<AiAnswerResponse>(`/api/invoices/${invoiceId}/ask`, { question })
      .then((r) => r.data),
};
