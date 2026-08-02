"use client";

import Link from "next/link";
import { Archive, Trash2, RotateCcw, RefreshCw } from "lucide-react";
import { cn, formatBytes, formatDate } from "@/lib/utils";
import { statusColors } from "@/lib/status-colors";
import type { DocumentDto } from "@/lib/api/types";
import { MimeIcon } from "./mime-icon";

interface DocumentCardProps {
  doc: DocumentDto;
  onTrash?: (id: string) => void;
  onArchive?: (id: string) => void;
  onRestore?: (id: string) => void;
  onRetry?: (id: string) => void;
}

export function DocumentCard({
  doc,
  onTrash,
  onArchive,
  onRestore,
  onRetry,
}: DocumentCardProps) {
  return (
    <div className="group flex items-start gap-3 rounded-lg border border-border p-4 bg-background hover:bg-accent/50 transition-colors">
      <div className="mt-0.5 shrink-0 text-muted-foreground">
        <MimeIcon mime={doc.mimeType} />
      </div>

      <div className="flex-1 min-w-0">
        <Link
          href={`/documents/${doc.id}`}
          className="block font-medium text-sm truncate hover:underline"
        >
          {doc.title || doc.originalFilename}
        </Link>

        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground font-[family-name:var(--font-mono)]">
          <span>{formatBytes(doc.fileSize)}</span>
          {doc.pageCount && <span>· {doc.pageCount}p</span>}
          <span>· {formatDate(doc.createdAt)}</span>
          {doc.correspondent && (
            <span>· {doc.correspondent.name}</span>
          )}
        </div>

        {doc.status === "FAILED" && doc.lastIngestError && (
          <p className="mt-1 text-xs text-destructive truncate" title={doc.lastIngestError}>
            {doc.lastIngestError}
          </p>
        )}

        {doc.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
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
        )}
      </div>

      <div className="flex items-center gap-1 shrink-0">
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-xs font-medium",
            statusColors[doc.status] ?? "text-gray-600 bg-gray-100"
          )}
        >
          {doc.status}
        </span>

        <div className="hidden group-hover:flex items-center gap-1 ml-1">
          {doc.status === "READY" && onArchive && (
            <button
              onClick={() => onArchive(doc.id)}
              className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors"
              title="Archive"
            >
              <Archive className="h-4 w-4" />
            </button>
          )}
          {(doc.status === "READY" || doc.status === "ARCHIVED") && onTrash && (
            <button
              onClick={() => onTrash(doc.id)}
              className="p-1 rounded text-muted-foreground hover:text-destructive transition-colors"
              title="Move to trash"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
          {doc.status === "TRASH" && onRestore && (
            <button
              onClick={() => onRestore(doc.id)}
              className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors"
              title="Restore"
            >
              <RotateCcw className="h-4 w-4" />
            </button>
          )}
          {doc.status === "FAILED" && onRetry && (
            <button
              onClick={() => onRetry(doc.id)}
              className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors"
              title="Retry processing"
            >
              <RefreshCw className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
