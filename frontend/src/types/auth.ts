export type UserRole = "ROLE_ADMIN" | "ROLE_EMPLOYEE";
export type UserStatus = "ACTIVE" | "SUSPENDED";

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface CurrentUserResponse {
  user: UserSummary;
}
