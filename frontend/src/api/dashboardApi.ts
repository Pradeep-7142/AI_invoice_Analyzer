import { httpClient } from "@/api/httpClient";
import type { DashboardSummary } from "@/types/dashboard";

export const dashboardApi = {
  getSummary: () => httpClient.get<DashboardSummary>("/api/dashboard/summary").then((r) => r.data),
};
