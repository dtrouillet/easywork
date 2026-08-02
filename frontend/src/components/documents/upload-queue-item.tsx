"use client";

import { CheckCircle2, Loader2, RotateCcw, X } from "lucide-react";
import { cn } from "@/lib/utils";
import type { UploadQueueItem as UploadQueueItemType } from "@/store/ui-store";
import { MimeIcon } from "./mime-icon";

const statusColors: Record<UploadQueueItemType["status"], string> = {
  pending: "text-muted-foreground",
  uploading: "text-blue-600",
  done: "text-green-600",
  error: "text-red-600",
};

interface UploadQueueItemProps {
  item: UploadQueueItemType;
  onRemove: (id: string) => void;
  onRetry: (id: string) => void;
}

export function UploadQueueItem({ item, onRemove, onRetry }: UploadQueueItemProps) {
  return (
    <div className="flex items-center gap-3 rounded-md border border-border p-2">
      <div className="shrink-0 text-muted-foreground">
        <MimeIcon mime={item.file.type} className="h-4 w-4" />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate text-sm font-medium">{item.file.name}</span>
          {item.status === "uploading" && (
            <span className="shrink-0 text-xs text-muted-foreground font-[family-name:var(--font-mono)]">
              {item.progress}%
            </span>
          )}
        </div>

        {item.status === "uploading" && (
          <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${item.progress}%` }}
            />
          </div>
        )}

        {item.status === "error" && item.error && (
          <p className="mt-0.5 text-xs text-red-600">{item.error}</p>
        )}
      </div>

      <div className={cn("flex shrink-0 items-center gap-1", statusColors[item.status])}>
        {item.status === "uploading" && <Loader2 className="h-4 w-4 animate-spin" />}
        {item.status === "done" && <CheckCircle2 className="h-4 w-4" />}
        {item.status === "error" && (
          <button
            onClick={() => onRetry(item.id)}
            className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors"
            title="Retry"
          >
            <RotateCcw className="h-4 w-4" />
          </button>
        )}
        <button
          onClick={() => onRemove(item.id)}
          className="p-1 rounded text-muted-foreground hover:text-foreground transition-colors"
          title="Remove"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
