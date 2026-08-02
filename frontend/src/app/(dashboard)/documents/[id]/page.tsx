"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { useParams, useRouter } from "next/navigation";
import {
  Loader2,
  ArrowLeft,
  Download,
  FileText,
  Archive,
  Trash2,
  RotateCcw,
  Trash,
  RefreshCw,
} from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import { formatBytes, formatDate } from "@/lib/utils";
import { formatLocation } from "@/lib/document-tree";
import { ClassificationEditor } from "@/components/documents/classification-editor";
import { DocumentPreview } from "@/components/documents/document-preview";
import type { DocumentUpdateRequest } from "@/lib/api/types";

export default function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session } = useSession();
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: doc, isLoading } = useQuery({
    queryKey: ["document", id],
    queryFn: () => documentsApi(session!.accessToken).get(id),
    enabled: !!session,
  });

  const trash = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).trash(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      queryClient.invalidateQueries({ queryKey: ["document", id] });
    },
  });

  const archive = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).archive(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["document", id] }),
  });

  const restore = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).restore(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["document", id] }),
  });

  const retry = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).retry(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      queryClient.invalidateQueries({ queryKey: ["document", id] });
    },
  });

  const permanentDelete = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      router.push("/documents");
    },
  });

  // Fetched once and shared between the inline preview and the Download button,
  // rather than each triggering its own fetch of the same (potentially large) file.
  const { data: fileBlob, isLoading: fileLoading, isError: fileError } = useQuery({
    queryKey: ["document", id, "file"],
    queryFn: () => documentsApi(session!.accessToken).downloadFile(id),
    enabled: !!session,
  });

  function handleDownload() {
    if (!fileBlob) return;
    const url = URL.createObjectURL(fileBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = doc?.originalFilename ?? "document";
    a.click();
    URL.revokeObjectURL(url);
  }

  const classify = useMutation({
    mutationFn: (request: DocumentUpdateRequest) =>
      documentsApi(session!.accessToken).update(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      queryClient.invalidateQueries({ queryKey: ["document", id] });
    },
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!doc) return null;

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => router.back()}
          className="text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold truncate">
          {doc.title || doc.originalFilename}
        </h1>
      </div>

      <div className="flex flex-col lg:flex-row gap-6 items-start">
        <div className="flex-1 min-w-0 w-full max-w-[700px]">
          <DocumentPreview
            blob={fileBlob}
            isLoading={fileLoading}
            isError={fileError}
            mimeType={doc.mimeType}
            originalFilename={doc.originalFilename}
            onDownload={handleDownload}
          />
        </div>

        <div className="w-full lg:w-96 shrink-0 space-y-4">
          <div className="flex gap-2 flex-wrap">
            <button
              onClick={handleDownload}
              disabled={fileLoading || !fileBlob}
              className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors disabled:opacity-50"
            >
              {fileLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Download className="h-4 w-4" />
              )}
              Download
            </button>
            {doc.status === "READY" && (
              <>
                <button
                  onClick={() => archive.mutate()}
                  disabled={archive.isPending}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors"
                >
                  <Archive className="h-4 w-4" /> Archive
                </button>
                <button
                  onClick={() => trash.mutate()}
                  disabled={trash.isPending}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors"
                >
                  <Trash2 className="h-4 w-4" /> Move to trash
                </button>
              </>
            )}
            {doc.status === "ARCHIVED" && (
              <button
                onClick={() => restore.mutate()}
                disabled={restore.isPending}
                className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors"
              >
                <RotateCcw className="h-4 w-4" /> Restore
              </button>
            )}
            {doc.status === "FAILED" && (
              <button
                onClick={() => retry.mutate()}
                disabled={retry.isPending}
                className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors"
              >
                <RefreshCw className="h-4 w-4" /> Retry processing
              </button>
            )}
            {doc.status === "TRASH" && (
              <>
                <button
                  onClick={() => restore.mutate()}
                  disabled={restore.isPending}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors"
                >
                  <RotateCcw className="h-4 w-4" /> Restore
                </button>
                <button
                  onClick={() => {
                    if (confirm("Permanently delete this document? This cannot be undone.")) {
                      permanentDelete.mutate();
                    }
                  }}
                  disabled={permanentDelete.isPending}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border text-destructive hover:bg-destructive/10 transition-colors"
                >
                  <Trash className="h-4 w-4" /> Delete permanently
                </button>
              </>
            )}
          </div>

          <div className="rounded-lg border border-border divide-y divide-border">
            <div className="flex items-center gap-3 p-4">
              <FileText className="h-8 w-8 text-muted-foreground shrink-0" />
              <div className="min-w-0">
                <p className="font-medium text-sm truncate">{doc.originalFilename}</p>
                <p className="text-xs text-muted-foreground font-[family-name:var(--font-mono)] mt-0.5">
                  {doc.mimeType} · {formatBytes(doc.fileSize)}
                  {doc.pageCount ? ` · ${doc.pageCount} pages` : ""}
                  {doc.ocrApplied ? " · OCR" : ""}
                </p>
              </div>
            </div>

            {[
              { label: "Status", value: doc.status },
              { label: "Created", value: formatDate(doc.createdAt) },
              { label: "Updated", value: formatDate(doc.updatedAt) },
              { label: "Location", value: formatLocation(doc) },
            ].map(({ label, value }) => (
              <div key={label} className="flex items-center px-4 py-3 gap-4">
                <span className="text-sm text-muted-foreground w-36 shrink-0">{label}</span>
                <span className="text-sm font-[family-name:var(--font-mono)] truncate">{value}</span>
              </div>
            ))}
          </div>

          {doc.status === "FAILED" && doc.lastIngestError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
              <p className="text-xs font-medium uppercase tracking-wide mb-1 text-destructive">
                Processing failed
              </p>
              <p className="text-sm text-destructive/90">{doc.lastIngestError}</p>
            </div>
          )}

          <div className="rounded-lg border border-border p-4">
            <p className="text-xs font-medium uppercase tracking-wide mb-3 text-muted-foreground">
              Classification
            </p>
            <ClassificationEditor
              doc={doc}
              saving={classify.isPending}
              onSave={(request) => classify.mutate(request)}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
