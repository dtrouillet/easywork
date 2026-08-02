import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { ProcessingMonitor } from "./processing-monitor";
import type { DocumentDto, DocumentStatus } from "@/lib/api/types";

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

function makeDoc(overrides: Partial<DocumentDto> = {}): DocumentDto {
  return {
    id: "doc-1",
    title: "Doc",
    status: "RECEIVED",
    originalFilename: "doc.pdf",
    mimeType: "application/pdf",
    fileSize: 100,
    pageCount: null,
    ocrApplied: false,
    lastIngestError: null,
    extractedText: null,
    documentDate: null,
    tags: [],
    correspondent: null,
    documentType: null,
    createdAt: "2026-03-15T00:00:00.000Z",
    updatedAt: "2026-03-15T00:00:00.000Z",
    ...overrides,
  };
}

const FIXTURE_DOCS: DocumentDto[] = [
  makeDoc({ id: "doc-received", title: "Received doc", status: "RECEIVED" }),
  makeDoc({ id: "doc-failed-1", title: "Failed doc 1", status: "FAILED", lastIngestError: "OCR crashed" }),
  makeDoc({ id: "doc-failed-2", title: "Failed doc 2", status: "FAILED", lastIngestError: "Timeout" }),
  makeDoc({ id: "doc-ready-1", title: "Old invoice", status: "READY", ocrApplied: true, pageCount: 2 }),
];

const retryMock = vi.fn().mockResolvedValue(undefined);

vi.mock("@/lib/api/documents", () => ({
  documentsApi: () => ({
    list: (_page: number, _size: number, criteria: { status?: DocumentStatus }) =>
      Promise.resolve({
        content: FIXTURE_DOCS.filter((d) => d.status === criteria.status),
        page: { number: 0, size: 100, totalElements: 0, totalPages: 1 },
      }),
    retry: retryMock,
  }),
}));

beforeEach(() => {
  retryMock.mockClear();
});

describe("ProcessingMonitor", () => {
  it("renders in-progress and failed documents, with a count", async () => {
    renderWithProviders(<ProcessingMonitor />);

    expect(await screen.findByText("Received doc")).toBeInTheDocument();
    expect(screen.getByText("Failed doc 1")).toBeInTheDocument();
    expect(screen.getByText("Failed doc 2")).toBeInTheDocument();
    expect(screen.getByText("3 documents")).toBeInTheDocument();
  });

  it("shows the ingest error for failed documents", async () => {
    renderWithProviders(<ProcessingMonitor />);

    expect(await screen.findByText("OCR crashed")).toBeInTheDocument();
  });

  it("only offers a checkbox on FAILED rows", async () => {
    renderWithProviders(<ProcessingMonitor />);

    await screen.findByText("Received doc");

    // header select-all + one per FAILED doc = 3
    expect(screen.getAllByRole("checkbox")).toHaveLength(3);
    expect(screen.getByLabelText("Select Failed doc 1")).toBeInTheDocument();
    expect(screen.queryByLabelText("Select Received doc")).not.toBeInTheDocument();
  });

  it("retries a single failed document", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProcessingMonitor />);

    await screen.findByText("Failed doc 1");
    await user.click(screen.getByLabelText("Retry Failed doc 1"));

    expect(retryMock).toHaveBeenCalledWith("doc-failed-1");
    expect(retryMock).toHaveBeenCalledTimes(1);
  });

  it("selects all failed documents and retries them in bulk", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProcessingMonitor />);

    await screen.findByText("Failed doc 1");
    await user.click(screen.getByLabelText("Select all failed documents"));

    const bulkButton = await screen.findByText("Retry 2 documents");
    await user.click(bulkButton);

    await waitFor(() => expect(retryMock).toHaveBeenCalledTimes(2));
    expect(retryMock).toHaveBeenCalledWith("doc-failed-1");
    expect(retryMock).toHaveBeenCalledWith("doc-failed-2");
  });

  it("shows an empty state when nothing is processing", async () => {
    FIXTURE_DOCS.length = 0;
    renderWithProviders(<ProcessingMonitor />);

    expect(await screen.findByText("Nothing is currently processing.")).toBeInTheDocument();

    // restore for subsequent tests
    FIXTURE_DOCS.push(
      makeDoc({ id: "doc-received", title: "Received doc", status: "RECEIVED" }),
      makeDoc({ id: "doc-failed-1", title: "Failed doc 1", status: "FAILED", lastIngestError: "OCR crashed" }),
      makeDoc({ id: "doc-failed-2", title: "Failed doc 2", status: "FAILED", lastIngestError: "Timeout" }),
      makeDoc({ id: "doc-ready-1", title: "Old invoice", status: "READY", ocrApplied: true, pageCount: 2 })
    );
  });

  it("shows already-processed documents in the history section", async () => {
    renderWithProviders(<ProcessingMonitor />);

    expect(await screen.findByText("Old invoice")).toBeInTheDocument();
    expect(screen.getByText("READY")).toBeInTheDocument();
    expect(screen.getByText("2p · OCR")).toBeInTheDocument();
  });

  it("shows an empty history state when nothing has been processed yet", async () => {
    const readyDoc = FIXTURE_DOCS.pop();
    renderWithProviders(<ProcessingMonitor />);

    expect(await screen.findByText("Nothing has been processed yet.")).toBeInTheDocument();

    if (readyDoc) FIXTURE_DOCS.push(readyDoc);
  });
});
