import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { ClassificationEditor } from "./classification-editor";
import { ApiError } from "@/lib/api/client";
import type { DocumentDto } from "@/lib/api/types";

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

const tagsList = vi.fn();
const tagsCreate = vi.fn();
const correspondentsList = vi.fn();
const correspondentsCreate = vi.fn();
const documentTypesList = vi.fn();

vi.mock("@/lib/api/tags", () => ({
  tagsApi: () => ({ list: tagsList, create: tagsCreate }),
}));
vi.mock("@/lib/api/correspondents", () => ({
  correspondentsApi: () => ({ list: correspondentsList, create: correspondentsCreate }),
}));
vi.mock("@/lib/api/document-types", () => ({
  documentTypesApi: () => ({ list: documentTypesList, create: vi.fn() }),
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

function setup(docOverrides: Partial<DocumentDto> = {}) {
  vi.clearAllMocks();
  tagsList.mockResolvedValue([{ id: "tag-1", name: "Energy", color: "#2F6F5E" }]);
  correspondentsList.mockResolvedValue([{ id: "corr-1", name: "EDF" }]);
  documentTypesList.mockResolvedValue([{ id: "type-1", name: "Invoice", retentionDays: null }]);

  const onSave = vi.fn();
  renderWithProviders(
    <ClassificationEditor doc={makeDoc(docOverrides)} onSave={onSave} saving={false} />
  );
  return { onSave };
}

describe("ClassificationEditor", () => {
  it("saves the selected correspondent and type", async () => {
    const user = userEvent.setup();
    const { onSave } = setup();

    await screen.findByText("EDF");
    const selects = screen.getAllByRole("combobox");
    await user.selectOptions(selects[0], "corr-1");
    await user.click(screen.getByText("Save classification"));

    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ correspondentId: "corr-1", tagIds: [] })
    );
  });

  it("saves an edited document date", async () => {
    const user = userEvent.setup();
    const { onSave } = setup();

    await screen.findByText("EDF");
    const dateInput = screen.getByLabelText("Document date");
    await user.type(dateInput, "2026-03-15");
    await user.click(screen.getByText("Save classification"));

    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ documentDate: "2026-03-15" }));
  });

  it("clears the document date when emptied", async () => {
    const user = userEvent.setup();
    const { onSave } = setup({ documentDate: "2026-03-15" });

    await screen.findByText("EDF");
    const dateInput = screen.getByLabelText("Document date");
    await user.clear(dateInput);
    await user.click(screen.getByText("Save classification"));

    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ documentDate: null }));
  });

  it("toggles tags and includes them on save", async () => {
    const user = userEvent.setup();
    const { onSave } = setup();

    await user.click(await screen.findByText("Energy"));
    await user.click(screen.getByText("Save classification"));

    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ tagIds: ["tag-1"] }));
  });

  it("creates a new correspondent inline and auto-selects it", async () => {
    const user = userEvent.setup();
    correspondentsCreate.mockResolvedValue({ id: "corr-new", name: "Allianz" });
    const { onSave } = setup();

    const selects = await screen.findAllByRole("combobox");
    await user.selectOptions(selects[0], "__new__");
    await user.type(screen.getByPlaceholderText("New correspondent name"), "Allianz");
    await user.click(screen.getByText("Add"));

    await waitFor(() => expect(correspondentsCreate).toHaveBeenCalledWith("Allianz"));
    await user.click(screen.getByText("Save classification"));
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ correspondentId: "corr-new" }));
  });

  it("shows an inline error on duplicate name without crashing", async () => {
    const user = userEvent.setup();
    correspondentsCreate.mockRejectedValue(new ApiError(409, "Name already exists"));
    setup();

    const selects = await screen.findAllByRole("combobox");
    await user.selectOptions(selects[0], "__new__");
    await user.type(screen.getByPlaceholderText("New correspondent name"), "EDF");
    await user.click(screen.getByText("Add"));

    expect(await screen.findByText("This name already exists")).toBeInTheDocument();
  });
});
