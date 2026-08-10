import { httpClient } from "@/api/httpClient";
import type { Budget, BudgetStatus } from "@/types/finance";
import type { BudgetHistory } from "@/types/analytics";

export interface BudgetPayload {
  category: string;
  monthlyLimit: number;
  currency?: string;
}

export const budgetsApi = {
  list: () => httpClient.get<Budget[]>("/api/budgets").then((r) => r.data),

  status: (month?: string) =>
    httpClient.get<BudgetStatus[]>("/api/budgets/status", { params: { month } }).then((r) => r.data),

  history: (months = 6) =>
    httpClient.get<BudgetHistory[]>("/api/budgets/history", { params: { months } }).then((r) => r.data),

  create: (payload: BudgetPayload) => httpClient.post<Budget>("/api/budgets", payload).then((r) => r.data),

  update: (id: string, payload: BudgetPayload) =>
    httpClient.put<Budget>(`/api/budgets/${id}`, payload).then((r) => r.data),

  remove: (id: string) => httpClient.delete(`/api/budgets/${id}`),
};
