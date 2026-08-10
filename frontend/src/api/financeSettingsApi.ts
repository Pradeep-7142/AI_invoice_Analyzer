import { httpClient } from "@/api/httpClient";
import type { FinanceSettings } from "@/types/finance";

export interface FinanceSettingsPayload {
  managerApprovalThreshold: number | null;
  adminApprovalThreshold: number | null;
}

export const financeSettingsApi = {
  get: () => httpClient.get<FinanceSettings>("/api/organizations/finance-settings").then((r) => r.data),

  update: (payload: FinanceSettingsPayload) =>
    httpClient.put<FinanceSettings>("/api/organizations/finance-settings", payload).then((r) => r.data),
};
