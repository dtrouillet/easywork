import { apiClient, uploadWithProgress } from "./client";
import type { DocumentDto, PageResponse } from "./types";

export function documentsApi(token: string) {
  const client = apiClient(token);

  return {
    list: (page = 0, size = 25) =>
      client.get<PageResponse<DocumentDto>>(
        `/api/v1/documents?page=${page}&size=${size}`
      ),

    get: (id: string) =>
      client.get<DocumentDto>(`/api/v1/documents/${id}`),

    upload: (file: File) =>
      client.upload<DocumentDto>("/api/v1/documents", file),

    uploadWithProgress: (file: File, onProgress: (percent: number) => void, signal?: AbortSignal) =>
      uploadWithProgress<DocumentDto>("/api/v1/documents", token, file, onProgress, signal),

    downloadFile: (id: string) =>
      client.downloadBlob(`/api/v1/documents/${id}/file`),

    trash: (id: string) =>
      client.post<void>(`/api/v1/documents/${id}/trash`),

    archive: (id: string) =>
      client.post<void>(`/api/v1/documents/${id}/archive`),

    restore: (id: string) =>
      client.post<void>(`/api/v1/documents/${id}/restore`),

    delete: (id: string) =>
      client.delete<void>(`/api/v1/documents/${id}`),
  };
}
