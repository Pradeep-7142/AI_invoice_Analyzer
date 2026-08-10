import { httpClient } from "@/api/httpClient";
import type {
  ChatMessage,
  ChatResponse,
  CostSavingRecommendation,
  NaturalSearchResponse,
  QuickInsight,
} from "@/types/ai";

export const aiApi = {
  chat: (message: string, history?: ChatMessage[]) =>
    httpClient
      .post<ChatResponse>("/api/ai/copilot/chat", { message, history })
      .then((r) => r.data),

  getQuickInsights: () =>
    httpClient
      .get<QuickInsight[]>("/api/ai/copilot/quick-insights")
      .then((r) => r.data),

  getCostSavings: () =>
    httpClient
      .get<CostSavingRecommendation[]>("/api/ai/insights/cost-savings")
      .then((r) => r.data),

  naturalSearch: (query: string) =>
    httpClient
      .post<NaturalSearchResponse>("/api/invoices/natural-search", { query })
      .then((r) => r.data),
};
