import type { InvoiceStatus, InvoiceSummary } from "@/types/invoice";

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface ChatResponse {
  answer: string;
  intent: string;
  suggestedFollowUps: string[];
  metrics: Record<string, any>;
  timestamp: string;
}

export interface QuickInsight {
  title: string;
  summary: string;
  category: string;
  severity: "INFO" | "WARNING" | "SAVINGS" | "CRITICAL";
  actionUrl?: string;
}

export interface CostSavingRecommendation {
  id: string;
  title: string;
  category: string;
  evidence: string;
  estimatedAnnualSaving: number;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  recommendedAction: string;
  type: "PRICE_CREEP" | "BUDGET_OVERRUN" | "CONCENTRATION_RISK" | "DUPLICATE_SUBSCRIPTION";
}

export interface NaturalSearchCriteria {
  vendorName?: string;
  category?: string;
  status?: InvoiceStatus;
  minAmount?: number;
  maxAmount?: number;
  fromDate?: string;
  toDate?: string;
  interpretation: string;
}

export interface NaturalSearchResponse {
  query: string;
  criteria: NaturalSearchCriteria;
  results: InvoiceSummary[];
  totalMatches: number;
}
