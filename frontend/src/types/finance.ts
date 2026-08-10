export type PaymentMethod = "BANK_TRANSFER" | "CARD" | "CHEQUE" | "CASH" | "OTHER";
export type PaymentStatus = "SCHEDULED" | "COMPLETED" | "FAILED" | "CANCELLED";
export type ApprovalDecisionType = "APPROVED" | "REJECTED";
export type ApprovalRole = "FINANCE_MANAGER" | "ORGANIZATION_ADMIN";

export interface Payment {
  id: string;
  amount: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  scheduledDate: string;
  completedAt: string | null;
  reference: string | null;
  notes: string | null;
  recordedByName: string;
  createdAt: string;
}

export interface PaymentSummary {
  id: string;
  invoiceId: string;
  invoiceNumber: string | null;
  vendorName: string | null;
  amount: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  scheduledDate: string;
  completedAt: string | null;
  createdAt: string;
}

export interface ApprovalDecision {
  id: string;
  decision: ApprovalDecisionType;
  requiredRole: ApprovalRole;
  thresholdAmount: number | null;
  reason: string | null;
  decidedByName: string;
  createdAt: string;
}

export interface RecurringExpense {
  frequency: string;
  occurrences: number;
  averageAmount: number;
  expectedNextDate: string;
  explanation: string;
}

export interface Budget {
  id: string;
  category: string;
  monthlyLimit: number;
  currency: string;
  createdAt: string;
  updatedAt: string;
}

export interface BudgetStatus {
  budgetId: string;
  category: string;
  monthlyLimit: number;
  currency: string;
  actualSpend: number;
  remaining: number;
  percentUsed: number;
  overBudget: boolean;
  invoiceCount: number;
}

export interface FinanceSettings {
  managerApprovalThreshold: number | null;
  adminApprovalThreshold: number | null;
}
