"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { useParams, useRouter } from "next/navigation";
import {
  Loader2,
  ArrowLeft,
  FileText,
  Archive,
  Trash2,
  RotateCcw,
  Trash,
} from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import { formatBytes, formatDate } from "@/lib/utils";

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

  const permanentDelete = useMutation({
    mutationFn: () => documentsApi(session!.accessToken).delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      router.push("/documents");
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
    <div className="max-w-3xl mx-auto space-y-6">
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

      <div className="flex gap-2 flex-wrap">
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
          { label: "Document date", value: formatDate(doc.documentDate) },
          { label: "Correspondent", value: doc.correspondent?.name ?? "—" },
          { label: "Type", value: doc.documentType?.name ?? "—" },
        ].map(({ label, value }) => (
          <div key={label} className="flex items-center px-4 py-3 gap-4">
            <span className="text-sm text-muted-foreground w-36 shrink-0">{label}</span>
            <span className="text-sm font-[family-name:var(--font-mono)]">{value}</span>
          </div>
        ))}

        {doc.tags.length > 0 && (
          <div className="flex items-start px-4 py-3 gap-4">
            <span className="text-sm text-muted-foreground w-36 shrink-0 pt-0.5">Tags</span>
            <div className="flex flex-wrap gap-1">
              {doc.tags.map((t) => (
                <span
                  key={t.id}
                  className="inline-flex items-center rounded-full px-2 py-0.5 text-xs bg-muted text-muted-foreground"
                  style={t.color ? { backgroundColor: t.color + "22", color: t.color } : undefined}
                >
                  {t.name}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
