import { httpClient } from "@/api/httpClient";
import type { AuthResponse, CurrentUserResponse } from "@/types/auth";

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    httpClient.post<AuthResponse>("/api/auth/register", payload).then((r) => r.data),

  login: (payload: LoginPayload) =>
    httpClient.post<AuthResponse>("/api/auth/login", payload).then((r) => r.data),

  logout: (refreshToken: string) => httpClient.post("/api/auth/logout", { refreshToken }),

  me: () => httpClient.get<CurrentUserResponse>("/api/users/me").then((r) => r.data),
};
