"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { ChevronRight, Loader2, FileX } from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import { DocumentCard } from "@/components/documents/document-card";
import { BrowsePanel, type BrowseView } from "@/components/documents/browse-panel";
import { filterDocsByPath } from "@/lib/document-tree";
import { cn } from "@/lib/utils";
import type { DocumentStatus } from "@/lib/api/types";

const PAGE_SIZE = 25;
const TREE_FETCH_SIZE = 1000;

const LIFECYCLE_FILTERS: { status: DocumentStatus; label: string }[] = [
  { status: "READY", label: "Active" },
  { status: "ARCHIVED", label: "Archived" },
  { status: "TRASH", label: "Trash" },
];

export default function DocumentsPage() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [view, setView] = useState<BrowseView>("tags");
  const [lifecycle, setLifecycle] = useState<DocumentStatus>("READY");
  const [activeTagId, setActiveTagId] = useState<string | null>(null);
  const [activeCorrespondentId, setActiveCorrespondentId] = useState<string | null>(null);
  const [activePath, setActivePath] = useState<string[]>([]);

  function changeLifecycle(status: DocumentStatus) {
    setLifecycle(status);
    setPage(0);
  }

  // Tags view: server-paginated, filtered by tag/correspondent/lifecycle status.
  const { data, isLoading, isError } = useQuery({
    queryKey: ["documents", page, lifecycle, activeTagId, activeCorrespondentId],
    queryFn: () =>
      documentsApi(session!.accessToken).list(page, PAGE_SIZE, {
        status: lifecycle,
        tagId: activeTagId ?? undefined,
        correspondentId: activeCorrespondentId ?? undefined,
      }),
    enabled: !!session && view === "tags",
  });

  // Tree view: fetches a large page once to build the tree and filters
  // client-side by the selected path — a proper aggregation endpoint would
  // replace this once document volumes outgrow a single-page fetch.
  const treeQuery = useQuery({
    queryKey: ["documents", "tree", lifecycle],
    queryFn: () => documentsApi(session!.accessToken).list(0, TREE_FETCH_SIZE, { status: lifecycle }),
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

  const retry = useMutation({
    mutationFn: (id: string) =>
      documentsApi(session!.accessToken).retry(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["documents"] }),
  });

  const treeDocs = treeQuery.data?.content ?? [];
  const visibleDocs = view === "tags" ? (data?.content ?? []) : filterDocsByPath(treeDocs, activePath);
  const totalCount = view === "tags" ? data?.page.totalElements : visibleDocs.length;
  const loading = view === "tags" ? isLoading : treeQuery.isLoading;
  const failed = view === "tags" ? isError : treeQuery.isError;

  return (
    <>
      <div className="flex gap-6">
        <BrowsePanel
          view={view}
          onViewChange={setView}
          activeTagId={activeTagId}
          onTagChange={setActiveTagId}
          activeCorrespondentId={activeCorrespondentId}
          onCorrespondentChange={setActiveCorrespondentId}
          activePath={activePath}
          onPathChange={setActivePath}
          docs={treeDocs}
        />

        <div className="flex-1 min-w-0 space-y-4">
          <div className="flex items-center justify-between">
            <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold">
              Documents
            </h1>
            {totalCount !== undefined && (
              <span className="text-sm text-muted-foreground">{totalCount} documents</span>
            )}
          </div>

          <div className="flex rounded-md p-0.5 text-sm bg-muted border border-border w-fit">
            {LIFECYCLE_FILTERS.map(({ status, label }) => (
              <button
                key={status}
                onClick={() => changeLifecycle(status)}
                className={cn(
                  "px-3 py-1.5 rounded-[5px] transition-colors",
                  lifecycle === status
                    ? "bg-background font-semibold shadow-sm"
                    : "text-muted-foreground"
                )}
              >
                {label}
              </button>
            ))}
          </div>

          {view === "tree" && activePath.length > 0 && (
            <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <button onClick={() => setActivePath([])} className="hover:underline">
                All documents
              </button>
              {activePath.map((segment, i) => (
                <span key={i} className="flex items-center gap-1.5">
                  <ChevronRight className="h-3.5 w-3.5" />
                  <button
                    onClick={() => setActivePath(activePath.slice(0, i + 1))}
                    className={i === activePath.length - 1 ? "font-semibold text-foreground" : "hover:underline"}
                  >
                    {segment}
                  </button>
                </span>
              ))}
            </div>
          )}

          {loading && (
            <div className="flex justify-center py-16">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          )}

          {failed && (
            <p className="text-center py-16 text-destructive text-sm">
              Failed to load documents.
            </p>
          )}

          {!loading && visibleDocs.length === 0 && (
            <div className="flex flex-col items-center py-16 gap-3 text-muted-foreground">
              <FileX className="h-10 w-10" />
              <p className="text-sm">No documents match this filter.</p>
            </div>
          )}

          <div className="space-y-2">
            {visibleDocs.map((doc) => (
              <DocumentCard
                key={doc.id}
                doc={doc}
                onTrash={(id) => trash.mutate(id)}
                onArchive={(id) => archive.mutate(id)}
                onRestore={(id) => restore.mutate(id)}
                onRetry={(id) => retry.mutate(id)}
              />
            ))}
          </div>

          {view === "tags" && data && data.page.totalPages > 1 && (
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
      </div>
    </>
  );
}
