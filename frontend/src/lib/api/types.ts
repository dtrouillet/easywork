export type DocumentStatus =
  | "RECEIVED"
  | "EXTRACTING"
  | "OCR"
  | "CLASSIFYING"
  | "READY"
  | "ARCHIVED"
  | "TRASH"
  | "DELETED";

export interface TagDto {
  id: string;
  name: string;
  color: string | null;
}

export interface CorrespondentDto {
  id: string;
  name: string;
}

export interface DocumentTypeDto {
  id: string;
  name: string;
  retentionDays: number | null;
}

export interface DocumentDto {
  id: string;
  title: string;
  status: DocumentStatus;
  originalFilename: string;
  mimeType: string;
  fileSize: number;
  pageCount: number | null;
  ocrApplied: boolean;
  documentDate: string | null;
  tags: TagDto[];
  correspondent: CorrespondentDto | null;
  documentType: DocumentTypeDto | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentUpdateRequest {
  title?: string;
  documentDate?: string | null;
  correspondentId?: string | null;
  documentTypeId?: string | null;
  tagIds?: string[];
}

export interface DocumentSearchParams {
  status?: DocumentStatus;
  tagId?: string;
  correspondentId?: string;
  documentTypeId?: string;
  year?: number;
  q?: string;
}

export interface PageMetadata {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageResponse<T> {
  content: T[];
  page: PageMetadata;
}

export interface SearchResult {
  hits: DocumentDto[];
  totalHits: number;
}
