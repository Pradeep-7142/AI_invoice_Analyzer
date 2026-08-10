import { useState, useRef, useEffect } from "react";
import {
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
  Card,
  CardContent,
} from "@mui/material";
import SendIcon from "@mui/icons-material/Send";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import LightbulbOutlinedIcon from "@mui/icons-material/LightbulbOutlined";
import RefreshOutlinedIcon from "@mui/icons-material/RefreshOutlined";
import { useMutation, useQuery } from "@tanstack/react-query";
import { aiApi } from "@/api/aiApi";
import type { ChatMessage, QuickInsight } from "@/types/ai";
import { useAuth } from "@/features/auth/AuthContext";

const SUGGESTED_PROMPTS = [
  "How much did we spend in total over the last 6 months?",
  "Which vendors have the highest spend?",
  "Are any category budgets exceeded this month?",
  "What are our expected cash obligations for the next 30 days?",
  "Do we have any overdue invoices?",
];

export function AiAssistantPage() {
  const { user, organization } = useAuth();
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content: `Hello ${user?.fullName ?? "there"}! I am your **InvoiceIQ Finance Copilot**.\n\nI can analyze your company's live financial data, explain spend trends, forecast cash outflows, track budgets, and inspect vendor risks. Ask me anything or select a suggestion below.`,
    },
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const quickInsightsQuery = useQuery({
    queryKey: ["ai", "quick-insights"],
    queryFn: aiApi.getQuickInsights,
  });

  const chatMutation = useMutation({
    mutationFn: (userMessage: string) => {
      const history = messages.slice(1); // omit initial greeting
      return aiApi.chat(userMessage, history);
    },
    onSuccess: (data) => {
      setMessages((prev) => [...prev, { role: "assistant", content: data.answer }]);
    },
    onError: () => {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: "⚠️ *Unable to reach AI copilot service. Please verify your connection or try again.*",
        },
      ]);
    },
  });

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages, chatMutation.isPending]);

  const handleSend = (textToSend?: string) => {
    const text = (textToSend ?? input).trim();
    if (!text || chatMutation.isPending) return;

    setMessages((prev) => [...prev, { role: "user", content: text }]);
    setInput("");
    chatMutation.mutate(text);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const renderFormattedContent = (content: string) => {
    // Basic Markdown converter for clean display
    const lines = content.split("\n");
    return lines.map((line, idx) => {
      if (line.startsWith("### ")) {
        return (
          <Typography key={idx} variant="h6" fontWeight={700} sx={{ mt: 1.5, mb: 0.5 }}>
            {line.replace("### ", "")}
          </Typography>
        );
      }
      if (line.startsWith("- ")) {
        return (
          <Box key={idx} sx={{ display: "flex", alignItems: "flex-start", ml: 1, my: 0.3 }}>
            <Typography variant="body2" sx={{ mr: 1, color: "primary.main" }}>•</Typography>
            <Typography variant="body2">{formatInline(line.substring(2))}</Typography>
          </Box>
        );
      }
      if (line.trim() === "") {
        return <Box key={idx} sx={{ height: 6 }} />;
      }
      return (
        <Typography key={idx} variant="body2" sx={{ my: 0.3 }}>
          {formatInline(line)}
        </Typography>
      );
    });
  };

  const formatInline = (text: string) => {
    const parts = text.split(/(\*\*.*?\*\*|\*.*?\*)/g);
    return parts.map((part, i) => {
      if (part.startsWith("**") && part.endsWith("**")) {
        return <strong key={i}>{part.slice(2, -2)}</strong>;
      }
      if (part.startsWith("*") && part.endsWith("*")) {
        return <em key={i}>{part.slice(1, -1)}</em>;
      }
      return part;
    });
  };

  return (
    <Stack spacing={3} sx={{ height: "calc(100vh - 120px)" }}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AutoAwesomeIcon color="primary" sx={{ fontSize: 28 }} />
            <Typography variant="h4" fontWeight={700}>
              Finance Copilot
            </Typography>
            <Chip
              label="Grounded in Live Data"
              size="small"
              color="success"
              variant="outlined"
              sx={{ fontWeight: 600 }}
            />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Grounded financial intelligence for {organization?.name}
          </Typography>
        </Box>
        <Button
          size="small"
          startIcon={<RefreshOutlinedIcon />}
          onClick={() =>
            setMessages([
              {
                role: "assistant",
                content: `Chat session reset. What financial insights can I help you analyze today?`,
              },
            ])
          }
        >
          Clear Chat
        </Button>
      </Stack>

      {/* Quick Insights Banner */}
      {quickInsightsQuery.data && quickInsightsQuery.data.length > 0 && (
        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          {quickInsightsQuery.data.slice(0, 3).map((insight: QuickInsight, idx: number) => (
            <Card key={idx} variant="outlined" sx={{ flex: 1, backgroundColor: "background.paper" }}>
              <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                  <LightbulbOutlinedIcon
                    fontSize="small"
                    color={insight.severity === "CRITICAL" ? "error" : insight.severity === "WARNING" ? "warning" : "primary"}
                  />
                  <Typography variant="subtitle2" fontWeight={700}>
                    {insight.title}
                  </Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  {insight.summary}
                </Typography>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      {/* Chat Area */}
      <Paper
        variant="outlined"
        sx={{
          flexGrow: 1,
          display: "flex",
          flexDirection: "column",
          p: 2,
          overflow: "hidden",
          backgroundColor: "background.default",
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
                    <Paper
                      elevation={0}
                      sx={{
                        width: 34,
                        height: 34,
                        borderRadius: "50%",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        backgroundColor: "primary.main",
                        color: "primary.contrastText",
                        flexShrink: 0,
                      }}
                    >
                      <SmartToyOutlinedIcon fontSize="small" />
                    </Paper>
                  )}

                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      maxWidth: "78%",
                      borderRadius: 2,
                      backgroundColor: isAssistant ? "background.paper" : "primary.light",
                      color: isAssistant ? "text.primary" : "primary.contrastText",
                      borderColor: isAssistant ? "divider" : "transparent",
                    }}
                  >
                    {renderFormattedContent(msg.content)}
                  </Paper>

                  {!isAssistant && (
                    <Paper
                      elevation={0}
                      sx={{
                        width: 34,
                        height: 34,
                        borderRadius: "50%",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        backgroundColor: "secondary.main",
                        color: "secondary.contrastText",
                        flexShrink: 0,
                      }}
                    >
                      <PersonOutlineIcon fontSize="small" />
                    </Paper>
                  )}
                </Stack>
              );
            })}

            {chatMutation.isPending && (
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Paper
                  elevation={0}
                  sx={{
                    width: 34,
                    height: 34,
                    borderRadius: "50%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: "primary.main",
                    color: "primary.contrastText",
                  }}
                >
                  <SmartToyOutlinedIcon fontSize="small" />
                </Paper>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, backgroundColor: "background.paper" }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CircularProgress size={16} />
                    <Typography variant="body2" color="text.secondary">
                      Analyzing financial database and computing metrics...
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
              disabled={chatMutation.isPending}
              clickable
              variant="outlined"
              sx={{ fontSize: "0.75rem" }}
            />
          ))}
        </Box>

        {/* Input Bar */}
        <TextField
          fullWidth
          size="medium"
          placeholder="Ask a financial question (e.g., 'Why did spend increase this month?')"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={chatMutation.isPending}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  color="primary"
                  onClick={() => handleSend()}
                  disabled={!input.trim() || chatMutation.isPending}
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
