import { httpClient } from "@/api/httpClient";
import type { CashFlowForecast, MonthlyProjection } from "@/types/analytics";

export const forecastApi = {
  cashFlow: (weeks = 8) =>
    httpClient.get<CashFlowForecast>("/api/forecast/cash-flow", { params: { weeks } }).then((r) => r.data),

  monthlyProjection: (months = 3) =>
    httpClient.get<MonthlyProjection>("/api/forecast/monthly-projection", { params: { months } }).then((r) => r.data),
};
