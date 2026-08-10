export type OrgRole =
  | "ORGANIZATION_ADMIN"
  | "FINANCE_MANAGER"
  | "ACCOUNTANT"
  | "EMPLOYEE"
  | "VIEWER";

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  role: OrgRole;
  user: UserSummary;
  organization: OrganizationSummary;
}

export interface CurrentUserResponse {
  user: UserSummary;
  organization: OrganizationSummary;
  role: OrgRole;
}

export interface OrganizationMember {
  membershipId: string;
  userId: string;
  email: string;
  fullName: string;
  role: OrgRole;
  status: "ACTIVE" | "REMOVED";
  createdAt: string;
}
