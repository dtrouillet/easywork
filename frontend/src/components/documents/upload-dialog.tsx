"use client";

import { useRef, useState } from "react";
import { Upload, X, FileText, Loader2 } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { documentsApi } from "@/lib/api/documents";
import { useUiStore } from "@/store/ui-store";

export function UploadDialog() {
  const { data: session } = useSession();
  const open = useUiStore((s) => s.uploadDialogOpen);
  const setOpen = useUiStore((s) => s.setUploadDialogOpen);
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [file, setFile] = useState<File | null>(null);

  const { mutate, isPending, isSuccess, reset } = useMutation({
    mutationFn: async (f: File) => {
      const api = documentsApi(session!.accessToken);
      return api.upload(f);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["documents"] });
      setFile(null);
    },
  });

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragging(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped) setFile(dropped);
  }

  function handleClose() {
    setOpen(false);
    setFile(null);
    reset();
  }

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="w-full max-w-md rounded-xl border border-border bg-background shadow-lg">
        <div className="flex items-center justify-between p-4 border-b border-border">
          <h2 className="font-[family-name:var(--font-fraunces)] text-lg font-semibold">
            Upload document
          </h2>
          <button onClick={handleClose} className="text-muted-foreground hover:text-foreground">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          {!isSuccess ? (
            <>
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
                  className="hidden"
                  accept="application/pdf,image/*,text/*"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
                <Upload className="h-8 w-8 mx-auto mb-2 text-muted-foreground" />
                {file ? (
                  <div className="flex items-center justify-center gap-2 text-sm">
                    <FileText className="h-4 w-4" />
                    <span className="font-medium">{file.name}</span>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    Drop a file here or <span className="text-foreground font-medium">browse</span>
                  </p>
                )}
              </div>

              <button
                disabled={!file || isPending}
                onClick={() => file && mutate(file)}
                className="w-full flex items-center justify-center gap-2 rounded-md bg-primary text-primary-foreground py-2 text-sm font-medium disabled:opacity-50 hover:opacity-90 transition-opacity"
              >
                {isPending ? (
                  <><Loader2 className="h-4 w-4 animate-spin" /> Uploading…</>
                ) : (
                  <><Upload className="h-4 w-4" /> Upload</>
                )}
              </button>
            </>
          ) : (
            <div className="text-center py-6 space-y-2">
              <p className="font-medium text-green-600">Document uploaded!</p>
              <p className="text-sm text-muted-foreground">
                It will be processed shortly.
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
