import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { BrowsePanel, type BrowseView } from "./browse-panel";
import type { DocumentDto } from "@/lib/api/types";

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

vi.mock("@/lib/api/tags", () => ({
  tagsApi: () => ({
    list: () =>
      Promise.resolve([
        { id: "tag-1", name: "Energy", color: "#2F6F5E" },
        { id: "tag-2", name: "Health", color: null },
      ]),
  }),
}));

vi.mock("@/lib/api/correspondents", () => ({
  correspondentsApi: () => ({
    list: () => Promise.resolve([{ id: "corr-1", name: "EDF" }]),
  }),
}));

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
    documentDate: "2026-03-15",
    tags: [],
    correspondent: { id: "corr-1", name: "EDF" },
    documentType: { id: "type-1", name: "Invoice", retentionDays: null },
    createdAt: "2026-03-15T00:00:00.000Z",
    updatedAt: "2026-03-15T00:00:00.000Z",
    ...overrides,
  };
}

function Harness({ docs }: { docs: DocumentDto[] }) {
  const [view, setView] = useState<BrowseView>("tags");
  const [activeTagId, setActiveTagId] = useState<string | null>(null);
  const [activeCorrespondentId, setActiveCorrespondentId] = useState<string | null>(null);
  const [activePath, setActivePath] = useState<string[]>([]);

  return (
    <div>
      <BrowsePanel
        view={view}
        onViewChange={setView}
        activeTagId={activeTagId}
        onTagChange={setActiveTagId}
        activeCorrespondentId={activeCorrespondentId}
        onCorrespondentChange={setActiveCorrespondentId}
        activePath={activePath}
        onPathChange={setActivePath}
        docs={docs}
      />
      <p data-testid="state">
        {view}|{activeTagId ?? "none"}|{activeCorrespondentId ?? "none"}|{activePath.join("/")}
      </p>
    </div>
  );
}

describe("BrowsePanel", () => {
  it("shows the tags view by default with tags and correspondents", async () => {
    renderWithProviders(<Harness docs={[]} />);

    expect(await screen.findByText("Energy")).toBeInTheDocument();
    expect(screen.getByText("Health")).toBeInTheDocument();
    expect(screen.getByText("EDF")).toBeInTheDocument();
  });

  it("toggles a tag filter on and off", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness docs={[]} />);

    await user.click(await screen.findByText("Energy"));
    expect(screen.getByTestId("state")).toHaveTextContent("tags|tag-1|none|");

    await user.click(screen.getByText("Energy"));
    expect(screen.getByTestId("state")).toHaveTextContent("tags|none|none|");
  });

  it("switches to the tree view and selects a node", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness docs={[makeDoc()]} />);

    await user.click(screen.getByText("Folders"));
    expect(await screen.findByText("Invoice")).toBeInTheDocument();

    await user.click(screen.getByText("Invoice"));
    expect(screen.getByTestId("state")).toHaveTextContent("tree|none|none|Invoice");
  });
});
