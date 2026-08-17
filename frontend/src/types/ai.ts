export interface AiQuestionRequest {
  question: string;
}

export interface AiAnswerResponse {
  answer: string;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
}
