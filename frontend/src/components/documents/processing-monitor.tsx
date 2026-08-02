"use client";

import { useState } from "react";
import { useQuery, useQueries, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import Link from "next/link";
import { Loader2, RefreshCw } from "lucide-react";
import { documentsApi } from "@/lib/api/documents";
import { cn, formatDate } from "@/lib/utils";
import { statusColors } from "@/lib/status-colors";
import type { DocumentDto, DocumentStatus } from "@/lib/api/types";

const MONITORED_STATUSES: DocumentStatus[] = ["RECEIVED", "EXTRACTING", "OCR", "CLASSIFYING", "FAILED"];
const REFRESH_INTERVAL_MS = 3000;
const HISTORY_REFRESH_INTERVAL_MS = 10000;
const HISTORY_SIZE = 50;

export function ProcessingMonitor() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const queries = useQueries({
    queries: MONITORED_STATUSES.map((status) => ({
      queryKey: ["processing", status],
      queryFn: () => documentsApi(session!.accessToken).list(0, 100, { status }),
      enabled: !!session,
      refetchInterval: REFRESH_INTERVAL_MS,
    })),
  });

  // Processing is often fast enough (well under the refresh interval above) that a
  // document can go from "just uploaded" straight to READY between two polls,
  // never once being caught in an in-progress status — this history is the only
  // reliable record that it was actually processed.
  const history = useQuery({
    queryKey: ["processing", "history"],
    queryFn: () => documentsApi(session!.accessToken).list(0, HISTORY_SIZE, { status: "READY" }),
    enabled: !!session,
    refetchInterval: HISTORY_REFRESH_INTERVAL_MS,
  });

  const isLoading = queries.some((q) => q.isLoading);
  const docs = queries
    .flatMap((q) => q.data?.content ?? [])
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  const failedDocs = docs.filter((d) => d.status === "FAILED");
  const historyDocs = history.data?.content ?? [];

  function invalidateAll() {
    MONITORED_STATUSES.forEach((status) =>
      queryClient.invalidateQueries({ queryKey: ["processing", status] })
    );
    queryClient.invalidateQueries({ queryKey: ["processing", "history"] });
    queryClient.invalidateQueries({ queryKey: ["documents"] });
  }

  const retry = useMutation({
    mutationFn: (id: string) => documentsApi(session!.accessToken).retry(id),
    onSuccess: invalidateAll,
  });

  const bulkRetry = useMutation({
    mutationFn: (ids: string[]) =>
      Promise.allSettled(ids.map((id) => documentsApi(session!.accessToken).retry(id))),
    onSuccess: () => {
      setSelected(new Set());
      invalidateAll();
    },
  });

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAll() {
    setSelected((prev) =>
      prev.size === failedDocs.length ? new Set() : new Set(failedDocs.map((d) => d.id))
    );
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold">
            Processing
          </h1>
          <span className="text-sm text-muted-foreground">
            {docs.length} document{docs.length === 1 ? "" : "s"}
          </span>
        </div>

        {selected.size > 0 && (
          <div className="flex items-center justify-between rounded-md border border-border bg-accent/50 px-4 py-2">
            <span className="text-sm">{selected.size} selected</span>
            <button
              onClick={() => bulkRetry.mutate([...selected])}
              disabled={bulkRetry.isPending}
              className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md bg-primary text-primary-foreground disabled:opacity-50"
            >
              <RefreshCw className="h-4 w-4" /> Retry {selected.size} document{selected.size === 1 ? "" : "s"}
            </button>
          </div>
        )}

        {docs.length === 0 ? (
          <p className="text-center py-16 text-sm text-muted-foreground">
            Nothing is currently processing.
          </p>
        ) : (
          <div className="rounded-lg border border-border divide-y divide-border">
            <div className="flex items-center gap-3 px-4 py-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              <input
                type="checkbox"
                aria-label="Select all failed documents"
                checked={failedDocs.length > 0 && selected.size === failedDocs.length}
                onChange={toggleAll}
                disabled={failedDocs.length === 0}
                className="h-4 w-4"
              />
              <span className="flex-1">Document</span>
              <span className="w-28 shrink-0">Status</span>
              <span className="w-32 shrink-0">Updated</span>
              <span className="w-8 shrink-0" />
            </div>
            {docs.map((doc) => (
              <ProcessingRow
                key={doc.id}
                doc={doc}
                selected={selected.has(doc.id)}
                onToggle={() => toggle(doc.id)}
                onRetry={() => retry.mutate(doc.id)}
                retrying={retry.isPending && retry.variables === doc.id}
              />
            ))}
          </div>
        )}
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="font-[family-name:var(--font-fraunces)] text-lg font-semibold">
            History
          </h2>
          <span className="text-sm text-muted-foreground">
            Last {historyDocs.length} processed
          </span>
        </div>

        {history.isLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : historyDocs.length === 0 ? (
          <p className="text-center py-8 text-sm text-muted-foreground">
            Nothing has been processed yet.
          </p>
        ) : (
          <div className="rounded-lg border border-border divide-y divide-border">
            {historyDocs.map((doc) => (
              <HistoryRow key={doc.id} doc={doc} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

interface ProcessingRowProps {
  doc: DocumentDto;
  selected: boolean;
  onToggle: () => void;
  onRetry: () => void;
  retrying: boolean;
}

function ProcessingRow({ doc, selected, onToggle, onRetry, retrying }: ProcessingRowProps) {
  const isFailed = doc.status === "FAILED";

  return (
    <div className="flex items-center gap-3 px-4 py-3">
      {isFailed ? (
        <input
          type="checkbox"
          aria-label={`Select ${doc.title || doc.originalFilename}`}
          checked={selected}
          onChange={onToggle}
          className="h-4 w-4"
        />
      ) : (
        <span className="h-4 w-4 shrink-0" />
      )}

      <div className="flex-1 min-w-0">
        <Link href={`/documents/${doc.id}`} className="block text-sm font-medium truncate hover:underline">
          {doc.title || doc.originalFilename}
        </Link>
        {isFailed && doc.lastIngestError && (
          <p className="text-xs text-destructive truncate" title={doc.lastIngestError}>
            {doc.lastIngestError}
          </p>
        )}
      </div>

      <span
        className={cn(
          "w-28 shrink-0 rounded-full px-2 py-0.5 text-xs font-medium text-center",
          statusColors[doc.status] ?? "text-gray-600 bg-gray-100"
        )}
      >
        {doc.status}
      </span>

      <span className="w-32 shrink-0 text-xs text-muted-foreground font-[family-name:var(--font-mono)]">
        {formatDate(doc.updatedAt)}
      </span>

      <span className="w-8 shrink-0">
        {isFailed && (
          <button
            onClick={onRetry}
            disabled={retrying}
            aria-label={`Retry ${doc.title || doc.originalFilename}`}
            title="Retry processing"
            className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors disabled:opacity-50"
          >
            <RefreshCw className={cn("h-4 w-4", retrying && "animate-spin")} />
          </button>
        )}
      </span>
    </div>
  );
}

function HistoryRow({ doc }: { doc: DocumentDto }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="flex-1 min-w-0">
        <Link href={`/documents/${doc.id}`} className="block text-sm font-medium truncate hover:underline">
          {doc.title || doc.originalFilename}
        </Link>
        <p className="text-xs text-muted-foreground font-[family-name:var(--font-mono)]">
          {doc.pageCount ? `${doc.pageCount}p` : ""}
          {doc.pageCount && doc.ocrApplied ? " · " : ""}
          {doc.ocrApplied ? "OCR" : ""}
        </p>
      </div>

      <span
        className={cn(
          "w-28 shrink-0 rounded-full px-2 py-0.5 text-xs font-medium text-center",
          statusColors[doc.status] ?? "text-gray-600 bg-gray-100"
        )}
      >
        {doc.status}
      </span>

      <span className="w-32 shrink-0 text-xs text-muted-foreground font-[family-name:var(--font-mono)]">
        {formatDate(doc.updatedAt)}
      </span>
    </div>
  );
}
