import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { tokenStorage } from "@/api/tokenStorage";
import type { AuthResponse } from "@/types/auth";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const httpClient = axios.create({
  baseURL,
  headers: {
    "Content-Type": "application/json",
  },
});

httpClient.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

let onSessionExpired: (() => void) | null = null;
export function registerSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    throw new Error("No refresh token available.");
  }
  const { data } = await axios.post<AuthResponse>(`${baseURL}/api/auth/refresh`, { refreshToken });
  tokenStorage.setTokens(data.accessToken, data.refreshToken);
  return data.accessToken;
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableConfig | undefined;
    const isAuthEndpoint = originalRequest?.url?.includes("/api/auth/");

    if (error.response?.status === 401 && originalRequest && !originalRequest._retried && !isAuthEndpoint) {
      originalRequest._retried = true;
      try {
        refreshPromise = refreshPromise ?? refreshAccessToken();
        const newAccessToken = await refreshPromise;
        refreshPromise = null;
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return httpClient(originalRequest);
      } catch (refreshError) {
        refreshPromise = null;
        tokenStorage.clear();
        onSessionExpired?.();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);
