import { httpClient } from "@/api/httpClient";
import type { ActionCenter } from "@/types/dashboard";

export const dashboardApi = {
  actionCenter: () => httpClient.get<ActionCenter>("/api/dashboard/action-center").then((r) => r.data),
};
