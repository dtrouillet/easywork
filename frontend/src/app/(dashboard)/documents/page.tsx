"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Loader2, FileX } from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import { DocumentCard } from "@/components/documents/document-card";
import { UploadDialog } from "@/components/documents/upload-dialog";

const PAGE_SIZE = 25;

export default function DocumentsPage() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["documents", page],
    queryFn: () =>
      documentsApi(session!.accessToken).list(page, PAGE_SIZE),
    enabled: !!session,
  });

  const trash = useMutation({
    mutationFn: (id: string) =>
      documentsApi(session!.accessToken).trash(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["documents"] }),
  });

  const archive = useMutation({
    mutationFn: (id: string) =>
      documentsApi(session!.accessToken).archive(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["documents"] }),
  });

  const restore = useMutation({
    mutationFn: (id: string) =>
      documentsApi(session!.accessToken).restore(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["documents"] }),
  });

  return (
    <>
      <UploadDialog />

      <div className="max-w-4xl mx-auto space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold">
            Documents
          </h1>
          {data && (
            <span className="text-sm text-muted-foreground">
              {data.page.totalElements} documents
            </span>
          )}
        </div>

        {isLoading && (
          <div className="flex justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        )}

        {isError && (
          <p className="text-center py-16 text-destructive text-sm">
            Failed to load documents.
          </p>
        )}

        {data?.content.length === 0 && (
          <div className="flex flex-col items-center py-16 gap-3 text-muted-foreground">
            <FileX className="h-10 w-10" />
            <p className="text-sm">No documents yet. Upload one to get started.</p>
          </div>
        )}

        <div className="space-y-2">
          {data?.content.map((doc) => (
            <DocumentCard
              key={doc.id}
              doc={doc}
              onTrash={(id) => trash.mutate(id)}
              onArchive={(id) => archive.mutate(id)}
              onRestore={(id) => restore.mutate(id)}
            />
          ))}
        </div>

        {data && data.page.totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 pt-4">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              className="px-3 py-1.5 text-sm rounded-md border border-border disabled:opacity-40 hover:bg-accent transition-colors"
            >
              Previous
            </button>
            <span className="text-sm text-muted-foreground">
              {page + 1} / {data.page.totalPages}
            </span>
            <button
              disabled={page >= data.page.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1.5 text-sm rounded-md border border-border disabled:opacity-40 hover:bg-accent transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </>
  );
}
