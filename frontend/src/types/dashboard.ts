import type { InvoiceSummary } from "@/types/invoice";
import type { BudgetStatus } from "@/types/finance";

export interface ActionCenter {
  pendingMyApproval: InvoiceSummary[];
  needsMyAttention: InvoiceSummary[];
  overdueInvoices: InvoiceSummary[];
  overBudgetCategories: BudgetStatus[];
}
