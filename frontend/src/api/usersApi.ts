import { httpClient } from "@/api/httpClient";
import type { UserRole, UserSummary } from "@/types/auth";

export const usersApi = {
  list: () => httpClient.get<UserSummary[]>("/api/users").then((r) => r.data),

  updateRole: (userId: string, role: UserRole) =>
    httpClient.post<UserSummary>(`/api/users/${userId}/role`, null, { params: { role } }).then((r) => r.data),

  toggleStatus: (userId: string) =>
    httpClient.post<UserSummary>(`/api/users/${userId}/toggle-status`).then((r) => r.data),
};
