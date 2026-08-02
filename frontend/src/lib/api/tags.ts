import { apiClient } from "./client";
import type { TagDto } from "./types";

export function tagsApi(token: string) {
  const client = apiClient(token);

  return {
    list: () => client.get<TagDto[]>("/api/v1/tags"),

    create: (name: string, color?: string) => {
      const params = new URLSearchParams({ name });
      if (color) params.set("color", color);
      return client.post<TagDto>(`/api/v1/tags?${params.toString()}`);
    },

    update: (id: string, name: string, color?: string) => {
      const params = new URLSearchParams({ name });
      if (color) params.set("color", color);
      return client.patch<TagDto>(`/api/v1/tags/${id}?${params.toString()}`);
    },

    delete: (id: string) => client.delete<void>(`/api/v1/tags/${id}`),

    merge: (sourceId: string, targetId: string) =>
      client.post<void>(`/api/v1/tags/${sourceId}/merge?${new URLSearchParams({ targetId }).toString()}`),
  };
}
