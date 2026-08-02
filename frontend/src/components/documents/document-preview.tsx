"use client";

import { useEffect, useMemo, useState } from "react";
import { Download, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { MimeIcon } from "./mime-icon";

interface DocumentPreviewProps {
  blob: Blob | undefined;
  isLoading: boolean;
  isError: boolean;
  mimeType: string;
  originalFilename: string;
  onDownload: () => void;
}

// A4 page proportions (210×297mm) so the preview reads as a document page
// rather than a wide, flat rectangle — same shape regardless of file type
// or loading state, so the layout doesn't jump around.
const PAGE_CLASS = "aspect-[210/297] w-full rounded-lg border border-border bg-muted/30";

export function DocumentPreview({
  blob,
  isLoading,
  isError,
  mimeType,
  originalFilename,
  onDownload,
}: DocumentPreviewProps) {
  const isRenderableAsUrl = !!blob && mimeType !== "text/plain";
  const objectUrl = useMemo(
    () => (isRenderableAsUrl ? URL.createObjectURL(blob) : null),
    [blob, isRenderableAsUrl]
  );

  useEffect(() => {
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [objectUrl]);

  const [textContent, setTextContent] = useState<string | null>(null);

  useEffect(() => {
    if (!blob || mimeType !== "text/plain") return;
    let cancelled = false;
    blob.text().then((text) => {
      if (!cancelled) setTextContent(text);
    });
    return () => {
      cancelled = true;
    };
  }, [blob, mimeType]);

  if (isLoading) {
    return (
      <div className={cn(PAGE_CLASS, "flex items-center justify-center")}>
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !blob) {
    return (
      <div className={cn(PAGE_CLASS, "flex flex-col items-center justify-center gap-2 text-muted-foreground")}>
        <MimeIcon mime={mimeType} className="h-8 w-8" />
        <p className="text-sm">Couldn&apos;t load a preview.</p>
      </div>
    );
  }

  if (mimeType === "application/pdf" && objectUrl) {
    return (
      <div className={cn(PAGE_CLASS, "overflow-hidden")}>
        <iframe src={objectUrl} title={originalFilename} className="w-full h-full" />
      </div>
    );
  }

  if (mimeType.startsWith("image/") && objectUrl) {
    return (
      <div className={cn(PAGE_CLASS, "flex items-center justify-center p-4")}>
        {/* eslint-disable-next-line @next/next/no-img-element -- previewing a user-uploaded blob URL, not an optimizable static asset */}
        <img src={objectUrl} alt={originalFilename} className="max-h-full max-w-full object-contain" />
      </div>
    );
  }

  if (mimeType === "text/plain") {
    if (textContent === null) {
      return (
        <div className={cn(PAGE_CLASS, "flex items-center justify-center")}>
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      );
    }
    return (
      <div className={cn(PAGE_CLASS, "overflow-auto")}>
        <pre className="whitespace-pre-wrap text-sm p-4 font-[family-name:var(--font-mono)]">
          {textContent}
        </pre>
      </div>
    );
  }

  return (
    <div className={cn(PAGE_CLASS, "flex flex-col items-center justify-center gap-3 text-muted-foreground")}>
      <MimeIcon mime={mimeType} className="h-8 w-8" />
      <p className="text-sm">Preview isn&apos;t available for this file type.</p>
      <button
        onClick={onDownload}
        className="flex items-center gap-2 px-3 py-1.5 text-sm rounded-md border border-border hover:bg-accent transition-colors text-foreground"
      >
        <Download className="h-4 w-4" /> Download to view
      </button>
    </div>
  );
}
