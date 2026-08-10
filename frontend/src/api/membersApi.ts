import { httpClient } from "@/api/httpClient";
import type { OrganizationMember, OrgRole } from "@/types/auth";

export interface CreateMemberPayload {
  fullName: string;
  email: string;
  password: string;
  role: OrgRole;
}

export const membersApi = {
  list: () => httpClient.get<OrganizationMember[]>("/api/organizations/members").then((r) => r.data),

  create: (payload: CreateMemberPayload) =>
    httpClient.post<OrganizationMember>("/api/organizations/members", payload).then((r) => r.data),

  updateRole: (membershipId: string, role: OrgRole) =>
    httpClient
      .patch<OrganizationMember>(`/api/organizations/members/${membershipId}/role`, { role })
      .then((r) => r.data),

  remove: (membershipId: string) => httpClient.delete(`/api/organizations/members/${membershipId}`),
};
