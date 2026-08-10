import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { AiAssistantPage } from "../AiAssistantPage";
import { aiApi } from "@/api/aiApi";

vi.mock("@/features/auth/AuthContext", () => ({
  useAuth: () => ({
    user: { id: "u-1", email: "admin@test.com", fullName: "Admin User", role: "ORGANIZATION_ADMIN" },
    organization: { id: "o-1", name: "Acme Corp", currency: "INR" },
    role: "ORGANIZATION_ADMIN",
    isAuthenticated: true,
  }),
}));

vi.mock("@/api/aiApi", () => ({
  aiApi: {
    chat: vi.fn().mockResolvedValue({
      answer: "Spend in last 6 months is ₹1,200,000 across 15 invoices.",
      intent: "SPEND_BREAKDOWN",
      suggestedFollowUps: ["Which vendors have the highest spend?"],
      metrics: { totalSpend: 1200000 },
      timestamp: new Date().toISOString(),
    }),
    getQuickInsights: vi.fn().mockResolvedValue([
      {
        title: "Overdue Invoices Alert",
        summary: "3 invoices overdue totaling ₹45,000",
        category: "PAYMENTS",
        severity: "CRITICAL",
      },
    ]),
    getCostSavings: vi.fn().mockResolvedValue([]),
    naturalSearch: vi.fn().mockResolvedValue({
      query: "test",
      criteria: { interpretation: "Test interp" },
      results: [],
      totalMatches: 0,
    }),
  },
}));

describe("AiAssistantPage", () => {
  it("renders the Finance Copilot heading and prompt pills", async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AiAssistantPage />
        </BrowserRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText("Finance Copilot")).toBeDefined();
    expect(screen.getByText(/Grounded in Live Data/i)).toBeDefined();

    await waitFor(() => {
      expect(screen.getByText("Overdue Invoices Alert")).toBeDefined();
    });
  });

  it("sends a message when clicking a suggested prompt chip", async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AiAssistantPage />
        </BrowserRouter>
      </QueryClientProvider>
    );

    const chip = screen.getByText("Do we have any overdue invoices?");
    fireEvent.click(chip);

    await waitFor(() => {
      expect(aiApi.chat).toHaveBeenCalledWith("Do we have any overdue invoices?", expect.any(Array));
    });

    await waitFor(() => {
      expect(screen.getByText(/1,200,000/i)).toBeDefined();
    });
  });
});
