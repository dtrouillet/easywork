"use client";

import { useRef, useState } from "react";
import { Upload, X } from "lucide-react";
import { useUiStore } from "@/store/ui-store";
import { useDocumentUpload } from "@/hooks/use-document-upload";
import { UploadQueueItem } from "./upload-queue-item";

export function UploadDialog() {
  const open = useUiStore((s) => s.uploadDialogOpen);
  const setOpen = useUiStore((s) => s.setUploadDialogOpen);
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const { queue, addFiles, removeItem, retryItem, clearQueue } = useDocumentUpload();

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragging(false);
    if (e.dataTransfer.files.length > 0) {
      addFiles([...e.dataTransfer.files]);
    }
  }

  function handleClose() {
    setOpen(false);
    clearQueue();
  }

  if (!open) return null;

  const doneCount = queue.filter((i) => i.status === "done").length;
  const errorCount = queue.filter((i) => i.status === "error").length;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="w-full max-w-md rounded-xl border border-border bg-background shadow-lg">
        <div className="flex items-center justify-between p-4 border-b border-border">
          <h2 className="font-[family-name:var(--font-fraunces)] text-lg font-semibold">
            Upload documents
          </h2>
          <button onClick={handleClose} className="text-muted-foreground hover:text-foreground">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <div
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => inputRef.current?.click()}
            className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
              dragging ? "border-primary bg-accent" : "border-border hover:border-muted-foreground"
            }`}
          >
            <input
              ref={inputRef}
              type="file"
              multiple
              data-testid="file-input"
              className="hidden"
              accept="application/pdf,image/*,text/*"
              onChange={(e) => {
                if (e.target.files && e.target.files.length > 0) {
                  addFiles([...e.target.files]);
                  e.target.value = "";
                }
              }}
            />
            <Upload className="h-8 w-8 mx-auto mb-2 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              Drop files here or <span className="text-foreground font-medium">browse</span>
            </p>
          </div>

          {queue.length > 0 && (
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {queue.map((item) => (
                <UploadQueueItem
                  key={item.id}
                  item={item}
                  onRemove={removeItem}
                  onRetry={retryItem}
                />
              ))}
            </div>
          )}

          {queue.length > 0 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                {doneCount} uploaded{errorCount > 0 ? `, ${errorCount} failed` : ""}
              </p>
              <button onClick={handleClose} className="text-sm underline text-muted-foreground">
                Close
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
