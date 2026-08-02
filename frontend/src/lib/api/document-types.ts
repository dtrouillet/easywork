import { apiClient } from "./client";
import type { DocumentTypeDto } from "./types";

export function documentTypesApi(token: string) {
  const client = apiClient(token);

  return {
    list: () => client.get<DocumentTypeDto[]>("/api/v1/document-types"),

    create: (name: string, retentionDays?: number) => {
      const params = new URLSearchParams({ name });
      if (retentionDays !== undefined) params.set("retentionDays", String(retentionDays));
      return client.post<DocumentTypeDto>(`/api/v1/document-types?${params.toString()}`);
    },

    update: (id: string, name: string, retentionDays?: number) => {
      const params = new URLSearchParams({ name });
      if (retentionDays !== undefined) params.set("retentionDays", String(retentionDays));
      return client.patch<DocumentTypeDto>(`/api/v1/document-types/${id}?${params.toString()}`);
    },

    delete: (id: string) => client.delete<void>(`/api/v1/document-types/${id}`),

    merge: (sourceId: string, targetId: string) =>
      client.post<void>(`/api/v1/document-types/${sourceId}/merge?${new URLSearchParams({ targetId }).toString()}`),
  };
}
