import { httpClient } from "@/api/httpClient";
import type { AnalyticsSummary, CategorySpend, MonthlySpendPoint, VendorSpend } from "@/types/analytics";

export const analyticsApi = {
  summary: (months = 6) =>
    httpClient.get<AnalyticsSummary>("/api/analytics/summary", { params: { months } }).then((r) => r.data),

  spendTrend: (months = 6) =>
    httpClient.get<MonthlySpendPoint[]>("/api/analytics/spend-trend", { params: { months } }).then((r) => r.data),

  topVendors: (months = 6, limit = 10) =>
    httpClient.get<VendorSpend[]>("/api/analytics/vendors/top", { params: { months, limit } }).then((r) => r.data),

  categories: (months = 6) =>
    httpClient.get<CategorySpend[]>("/api/analytics/categories", { params: { months } }).then((r) => r.data),
};
