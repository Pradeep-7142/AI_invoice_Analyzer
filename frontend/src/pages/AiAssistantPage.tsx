import { useState, useRef, useEffect } from "react";
import {
  Autocomplete,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import SendIcon from "@mui/icons-material/Send";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import RefreshOutlinedIcon from "@mui/icons-material/RefreshOutlined";
import { useQuery } from "@tanstack/react-query";
import { invoicesApi } from "@/api/invoicesApi";
import { useAuth } from "@/features/auth/AuthContext";
import type { InvoiceSummary } from "@/types/invoice";

const SUGGESTED_PROMPTS = [
  "Summarize this invoice details and line items",
  "Is the tax amount calculated accurately?",
  "What is the payment due date and status?",
  "List vendor information and registration",
];

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export function AiAssistantPage() {
  const { user } = useAuth();
  const [selectedInvoice, setSelectedInvoice] = useState<InvoiceSummary | null>(null);
  const [input, setInput] = useState("");
  const [isAsking, setIsAsking] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content: `Hello ${user?.fullName ?? "there"}! I am your **InvoiceIQ AI Assistant**.\n\nSelect an invoice from your system above and ask any question about line items, tax breakdowns, vendor verification, or payment due dates.`,
    },
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const invoicesQuery = useQuery({
    queryKey: ["invoices"],
    queryFn: () => invoicesApi.list(),
  });

  const invoices = invoicesQuery.data?.content ?? [];

  useEffect(() => {
    if (invoices.length > 0 && !selectedInvoice) {
      setSelectedInvoice(invoices[0] ?? null);
    }
  }, [invoices, selectedInvoice]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages, isAsking]);

  const handleSend = async (customPrompt?: string) => {
    const text = (customPrompt ?? input).trim();
    if (!text || isAsking) return;

    if (!selectedInvoice) {
      setMessages((prev) => [
        ...prev,
        { role: "user", content: text },
        { role: "assistant", content: "Please select an invoice first from the dropdown above." },
      ]);
      setInput("");
      return;
    }

    setMessages((prev) => [...prev, { role: "user", content: text }]);
    setInput("");
    setIsAsking(true);

    try {
      const response = await invoicesApi.askQuestion(selectedInvoice.id, text);
      setMessages((prev) => [...prev, { role: "assistant", content: response.answer }]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: "⚠️ *Unable to reach AI assistant service. Please check your connection or try again.*",
        },
      ]);
    } finally {
      setIsAsking(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const renderFormattedContent = (content: string) => {
    const lines = content.split("\n");
    return lines.map((line, idx) => {
      if (line.startsWith("### ")) {
        return (
          <Typography key={idx} variant="h6" fontWeight={700} sx={{ mt: 1, mb: 0.5 }}>
            {line.replace("### ", "")}
          </Typography>
        );
      }
      if (line.startsWith("- ")) {
        return (
          <Box key={idx} sx={{ display: "flex", alignItems: "flex-start", ml: 1, my: 0.3 }}>
            <Typography variant="body2" sx={{ mr: 1, color: "primary.main" }}>
              •
            </Typography>
            <Typography variant="body2">{line.substring(2)}</Typography>
          </Box>
        );
      }
      if (line.trim() === "") {
        return <Box key={idx} sx={{ height: 6 }} />;
      }
      return (
        <Typography key={idx} variant="body2" sx={{ my: 0.3 }}>
          {line}
        </Typography>
      );
    });
  };

  return (
    <Stack spacing={3} sx={{ height: "calc(100vh - 120px)" }}>
      {/* Header */}
      <Stack
        direction={{ xs: "column", sm: "row" }}
        justifyContent="space-between"
        alignItems={{ sm: "center" }}
        spacing={2}
      >
        <Box>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AutoAwesomeIcon color="primary" sx={{ fontSize: 28 }} />
            <Typography variant="h4" fontWeight={700} color="#0F172A">
              AI Invoice Assistant
            </Typography>
            <Chip
              label="Natural Language Q&A"
              size="small"
              color="primary"
              variant="outlined"
              sx={{ fontWeight: 600 }}
            />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Query invoice details, verify tax structures, and analyze line items with AI assistance.
          </Typography>
        </Box>

        <Button
          size="small"
          variant="outlined"
          startIcon={<RefreshOutlinedIcon />}
          onClick={() =>
            setMessages([
              {
                role: "assistant",
                content: `Chat session reset. What invoice questions can I help you with?`,
              },
            ])
          }
        >
          Clear Chat
        </Button>
      </Stack>

      {/* Invoice Selector Banner */}
      <Paper variant="outlined" sx={{ p: 2, borderRadius: "12px", bgcolor: "#FFFFFF" }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ sm: "center" }}>
          <Typography variant="subtitle2" fontWeight={700} sx={{ whiteSpace: "nowrap" }}>
            Target Invoice:
          </Typography>
          <Autocomplete
            options={invoices}
            getOptionLabel={(option) =>
              `${option.invoiceNumber || "Draft"} — ${option.vendor?.name || "Unassigned"} (${
                option.totalAmount ? `₹${option.totalAmount}` : "₹0"
              })`
            }
            value={selectedInvoice}
            onChange={(_, val) => setSelectedInvoice(val)}
            sx={{ flexGrow: 1 }}
            renderInput={(params) => (
              <TextField {...params} size="small" placeholder="Select an invoice to analyze..." />
            )}
          />
          {selectedInvoice && (
            <Chip
              label={`Status: ${selectedInvoice.status.replace("_", " ")}`}
              size="small"
              color="primary"
              sx={{ fontWeight: 600 }}
            />
          )}
        </Stack>
      </Paper>

      {/* Chat Area */}
      <Paper
        variant="outlined"
        sx={{
          flexGrow: 1,
          display: "flex",
          flexDirection: "column",
          p: 2.5,
          borderRadius: "14px",
          overflow: "hidden",
          backgroundColor: "#FFFFFF",
        }}
      >
        {/* Messages List */}
        <Box sx={{ flexGrow: 1, overflowY: "auto", pr: 1 }}>
          <Stack spacing={2}>
            {messages.map((msg, idx) => {
              const isAssistant = msg.role === "assistant";
              return (
                <Stack
                  key={idx}
                  direction="row"
                  spacing={1.5}
                  justifyContent={isAssistant ? "flex-start" : "flex-end"}
                  alignItems="flex-start"
                >
                  {isAssistant && (
                    <Avatar
                      sx={{
                        width: 34,
                        height: 34,
                        bgcolor: "#3B82F6",
                        flexShrink: 0,
                      }}
                    >
                      <SmartToyOutlinedIcon fontSize="small" />
                    </Avatar>
                  )}

                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      maxWidth: "80%",
                      borderRadius: "12px",
                      backgroundColor: isAssistant ? "#F8FAFC" : "#EFF6FF",
                      color: "#0F172A",
                      borderColor: isAssistant ? "#E2E8F0" : "#BFDBFE",
                    }}
                  >
                    {renderFormattedContent(msg.content)}
                  </Paper>

                  {!isAssistant && (
                    <Avatar
                      sx={{
                        width: 34,
                        height: 34,
                        bgcolor: "#10B981",
                        flexShrink: 0,
                      }}
                    >
                      <PersonOutlineIcon fontSize="small" />
                    </Avatar>
                  )}
                </Stack>
              );
            })}

            {isAsking && (
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Avatar sx={{ width: 34, height: 34, bgcolor: "#3B82F6" }}>
                  <SmartToyOutlinedIcon fontSize="small" />
                </Avatar>
                <Paper
                  variant="outlined"
                  sx={{ p: 2, borderRadius: "12px", bgcolor: "#F8FAFC", borderColor: "#E2E8F0" }}
                >
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CircularProgress size={16} />
                    <Typography variant="body2" color="text.secondary">
                      Analyzing invoice data and formulating response...
                    </Typography>
                  </Stack>
                </Paper>
              </Stack>
            )}

            <div ref={messagesEndRef} />
          </Stack>
        </Box>

        <Divider sx={{ my: 1.5 }} />

        {/* Suggested Prompts */}
        <Box sx={{ mb: 1.5, display: "flex", gap: 1, flexWrap: "wrap" }}>
          {SUGGESTED_PROMPTS.map((prompt, idx) => (
            <Chip
              key={idx}
              label={prompt}
              size="small"
              onClick={() => handleSend(prompt)}
              disabled={isAsking || !selectedInvoice}
              clickable
              variant="outlined"
              sx={{ fontSize: "0.78rem" }}
            />
          ))}
        </Box>

        {/* Input Bar */}
        <TextField
          fullWidth
          size="medium"
          placeholder="Ask anything about the selected invoice..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isAsking}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  color="primary"
                  onClick={() => handleSend()}
                  disabled={!input.trim() || isAsking}
                >
                  <SendIcon />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Paper>
    </Stack>
  );
}
