import { apiClient } from "./client";
import type { CorrespondentDto } from "./types";

export function correspondentsApi(token: string) {
  const client = apiClient(token);

  return {
    list: () => client.get<CorrespondentDto[]>("/api/v1/correspondents"),

    create: (name: string) =>
      client.post<CorrespondentDto>(`/api/v1/correspondents?${new URLSearchParams({ name }).toString()}`),
  };
}
