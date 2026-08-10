export interface AnalyticsSummary {
  periodMonths: number;
  totalSpend: number;
  invoiceCount: number;
  averageInvoiceAmount: number;
  totalOutstanding: number;
  statusCounts: Record<string, number>;
}

export interface MonthlySpendPoint {
  month: string;
  totalSpend: number;
  invoiceCount: number;
}

export interface VendorSpend {
  vendorId: string;
  vendorName: string;
  category: string | null;
  totalSpend: number;
  invoiceCount: number;
  averageAmount: number;
}

export interface CategorySpend {
  category: string;
  totalSpend: number;
  invoiceCount: number;
  budgetLimit: number | null;
}

export interface BudgetHistoryPoint {
  month: string;
  actualSpend: number;
  monthlyLimit: number;
  overBudget: boolean;
}

export interface BudgetHistory {
  budgetId: string;
  category: string;
  currency: string;
  points: BudgetHistoryPoint[];
}

export interface CashFlowWeekPoint {
  weekStart: string;
  scheduledAmount: number;
  dueUnscheduledAmount: number;
}

export interface CashFlowForecast {
  weeks: CashFlowWeekPoint[];
  totalScheduled: number;
  totalDueUnscheduled: number;
}

export interface MonthlyProjection {
  averageMonthlySpend: number;
  basedOnMonths: number;
  explanation: string;
}
