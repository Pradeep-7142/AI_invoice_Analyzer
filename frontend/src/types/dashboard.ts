import type { InvoiceSummary } from "@/types/invoice";

export interface VendorSpend {
  vendorName: string;
  totalAmount: number;
  invoiceCount: number;
}

export interface MonthlyTrend {
  month: string;
  totalAmount: number;
  count: number;
}

export interface DashboardSummary {
  totalInvoices: number;
  needsReviewCount: number;
  verifiedCount: number;
  approvedCount: number;
  rejectedCount: number;
  totalSpend: number;
  pendingSpend: number;
  recentInvoices: InvoiceSummary[];
  topVendors: VendorSpend[];
  monthlyTrends: MonthlyTrend[];
  statusBreakdown: Record<string, number>;
}
