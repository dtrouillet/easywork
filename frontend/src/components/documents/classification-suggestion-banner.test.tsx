import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { ClassificationSuggestionBanner } from "./classification-suggestion-banner";
import type { DocumentClassificationSuggestionDto } from "@/lib/api/types";

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

const getSuggestion = vi.fn();
const confirmSuggestion = vi.fn();
const rejectSuggestion = vi.fn();

vi.mock("@/lib/api/documents", () => ({
  documentsApi: () => ({ getSuggestion, confirmSuggestion, rejectSuggestion }),
}));

function pendingSuggestion(
  overrides: Partial<DocumentClassificationSuggestionDto> = {}
): DocumentClassificationSuggestionDto {
  return {
    documentId: "doc-1",
    suggestedCorrespondent: { id: "corr-1", name: "EDF" },
    suggestedDocumentType: { id: "type-1", name: "invoice", retentionDays: null },
    suggestedDocumentDate: null,
    suggestedTags: [
      { id: "tag-1", name: "Energy", color: null },
      { id: "tag-2", name: "Invoices", color: null },
    ],
    source: "HEURISTIC",
    status: "PENDING",
    createdAt: "2026-01-01T00:00:00Z",
    confirmedAt: null,
    rejectedAt: null,
    ...overrides,
  };
}

function setup() {
  vi.clearAllMocks();
  return renderWithProviders(<ClassificationSuggestionBanner documentId="doc-1" />);
}

describe("ClassificationSuggestionBanner", () => {
  it("shows the correspondent/type sentence and suggested tags", async () => {
    getSuggestion.mockResolvedValue(pendingSuggestion());
    setup();

    expect(await screen.findByText("This looks like a invoice from EDF.")).toBeInTheDocument();
    expect(screen.getByText("Suggested tags: Energy, Invoices")).toBeInTheDocument();
  });

  it("renders nothing while there is no suggestion to show", async () => {
    getSuggestion.mockRejectedValue(new Error("404"));
    setup();

    await waitFor(() => expect(getSuggestion).toHaveBeenCalled());
    expect(screen.queryByText(/This looks like/)).not.toBeInTheDocument();
  });

  it("renders nothing once the suggestion has already been resolved", async () => {
    getSuggestion.mockResolvedValue(pendingSuggestion({ status: "CONFIRMED" }));
    setup();

    await waitFor(() => expect(getSuggestion).toHaveBeenCalled());
    expect(screen.queryByText(/This looks like/)).not.toBeInTheDocument();
  });

  it("renders nothing when the suggestion has no fields to offer", async () => {
    getSuggestion.mockResolvedValue(
      pendingSuggestion({ suggestedCorrespondent: null, suggestedDocumentType: null, suggestedTags: [] })
    );
    setup();

    await waitFor(() => expect(getSuggestion).toHaveBeenCalled());
    expect(screen.queryByText(/This looks like/)).not.toBeInTheDocument();
  });

  it("confirms with every suggested field and tag id", async () => {
    const user = userEvent.setup();
    getSuggestion.mockResolvedValue(pendingSuggestion());
    confirmSuggestion.mockResolvedValue(pendingSuggestion({ status: "CONFIRMED" }));
    setup();

    await user.click(await screen.findByText("Confirm"));

    await waitFor(() =>
      expect(confirmSuggestion).toHaveBeenCalledWith("doc-1", {
        acceptCorrespondent: true,
        acceptDocumentType: true,
        acceptDocumentDate: false,
        acceptTagIds: ["tag-1", "tag-2"],
      })
    );
  });

  it("dismisses the suggestion", async () => {
    const user = userEvent.setup();
    getSuggestion.mockResolvedValue(pendingSuggestion());
    rejectSuggestion.mockResolvedValue(pendingSuggestion({ status: "REJECTED" }));
    setup();

    await user.click(await screen.findByText("Dismiss"));

    await waitFor(() => expect(rejectSuggestion).toHaveBeenCalledWith("doc-1"));
  });
});
