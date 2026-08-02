import { apiClient, uploadWithProgress } from "./client";
import type {
  ConfirmSuggestionRequest,
  DocumentClassificationSuggestionDto,
  DocumentDto,
  DocumentSearchParams,
  DocumentUpdateRequest,
  PageResponse,
} from "./types";

export function documentsApi(token: string) {
  const client = apiClient(token);

  return {
    list: (page = 0, size = 25, criteria: DocumentSearchParams = {}) => {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (criteria.status) params.set("status", criteria.status);
      if (criteria.tagId) params.set("tagId", criteria.tagId);
      if (criteria.correspondentId) params.set("correspondentId", criteria.correspondentId);
      if (criteria.documentTypeId) params.set("documentTypeId", criteria.documentTypeId);
      if (criteria.year) params.set("year", String(criteria.year));
      if (criteria.q) params.set("q", criteria.q);
      return client.get<PageResponse<DocumentDto>>(`/api/v1/documents?${params.toString()}`);
    },

    get: (id: string) =>
      client.get<DocumentDto>(`/api/v1/documents/${id}`),

    update: (id: string, request: DocumentUpdateRequest) =>
      client.patch<DocumentDto>(`/api/v1/documents/${id}`, request),

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

    retry: (id: string) =>
      client.post<void>(`/api/v1/documents/${id}/retry`),

    reclassify: (id: string) =>
      client.post<DocumentClassificationSuggestionDto>(`/api/v1/documents/${id}/reclassify`),

    getSuggestion: (id: string) =>
      client.get<DocumentClassificationSuggestionDto>(`/api/v1/documents/${id}/suggestion`),

    confirmSuggestion: (id: string, request: ConfirmSuggestionRequest) =>
      client.post<DocumentClassificationSuggestionDto>(`/api/v1/documents/${id}/suggestion/confirm`, request),

    rejectSuggestion: (id: string) =>
      client.post<DocumentClassificationSuggestionDto>(`/api/v1/documents/${id}/suggestion/reject`),

    delete: (id: string) =>
      client.delete<void>(`/api/v1/documents/${id}`),
  };
}
