import { httpClient } from "@/api/httpClient";
import type { Vendor } from "@/types/invoice";

export interface VendorPayload {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  gstin?: string;
  taxId?: string;
  category?: string;
  notes?: string;
}

export const vendorsApi = {
  list: (search?: string): Promise<Vendor[]> =>
    httpClient
      .get<any>("/api/vendors", { params: { search, size: 100 } })
      .then((r) => (Array.isArray(r.data) ? r.data : (r.data?.content ?? []))),

  get: (id: string) => httpClient.get<Vendor>(`/api/vendors/${id}`).then((r) => r.data),

  create: (payload: VendorPayload) => httpClient.post<Vendor>("/api/vendors", payload).then((r) => r.data),

  update: (id: string, payload: VendorPayload) =>
    httpClient.put<Vendor>(`/api/vendors/${id}`, payload).then((r) => r.data),

  archive: (id: string) => httpClient.delete(`/api/vendors/${id}`),
};
