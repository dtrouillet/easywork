import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DocumentCard } from "./document-card";
import type { DocumentDto } from "@/lib/api/types";

function makeDoc(overrides: Partial<DocumentDto> = {}): DocumentDto {
  return {
    id: "doc-1",
    title: "Doc",
    status: "READY",
    originalFilename: "doc.pdf",
    mimeType: "application/pdf",
    fileSize: 100,
    pageCount: null,
    ocrApplied: false,
    lastIngestError: null,
    extractedText: null,
    documentDate: "2026-03-15",
    tags: [],
    correspondent: null,
    documentType: null,
    createdAt: "2026-03-15T00:00:00.000Z",
    updatedAt: "2026-03-15T00:00:00.000Z",
    ...overrides,
  };
}

describe("DocumentCard", () => {
  it("shows the FAILED status badge and the ingest error message", () => {
    const doc = makeDoc({ status: "FAILED", lastIngestError: "Tesseract crashed" });

    render(<DocumentCard doc={doc} />);

    expect(screen.getByText("FAILED")).toBeInTheDocument();
    expect(screen.getByText("Tesseract crashed")).toBeInTheDocument();
  });

  it("shows a retry button for a FAILED document and invokes onRetry", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const doc = makeDoc({ status: "FAILED", lastIngestError: "boom" });

    render(<DocumentCard doc={doc} onRetry={onRetry} />);

    await user.click(screen.getByTitle("Retry processing"));

    expect(onRetry).toHaveBeenCalledWith("doc-1");
  });

  it("does not show a retry button for a READY document", () => {
    const onRetry = vi.fn();
    const doc = makeDoc({ status: "READY" });

    render(<DocumentCard doc={doc} onRetry={onRetry} />);

    expect(screen.queryByTitle("Retry processing")).not.toBeInTheDocument();
  });
});
