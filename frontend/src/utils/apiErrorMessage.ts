import { isAxiosError } from "axios";
import type { ApiError } from "@/api/httpClient";

export function apiErrorMessage(error: unknown, fallback = "Something went wrong. Please try again."): string {
  if (isAxiosError<ApiError>(error) && error.response?.data?.message) {
    return error.response.data.message;
  }
  return fallback;
}
