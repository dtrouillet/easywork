import { apiClient } from "./client";
import type { CorrespondentDto } from "./types";

export function correspondentsApi(token: string) {
  const client = apiClient(token);

  return {
    list: () => client.get<CorrespondentDto[]>("/api/v1/correspondents"),

    create: (name: string) =>
      client.post<CorrespondentDto>(`/api/v1/correspondents?${new URLSearchParams({ name }).toString()}`),

    update: (id: string, name: string) =>
      client.patch<CorrespondentDto>(`/api/v1/correspondents/${id}?${new URLSearchParams({ name }).toString()}`),

    delete: (id: string) => client.delete<void>(`/api/v1/correspondents/${id}`),

    merge: (sourceId: string, targetId: string) =>
      client.post<void>(`/api/v1/correspondents/${sourceId}/merge?${new URLSearchParams({ targetId }).toString()}`),
  };
}
