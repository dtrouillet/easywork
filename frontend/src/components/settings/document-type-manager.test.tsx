import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { DocumentTypeManager } from "./document-type-manager";
import { ApiError } from "@/lib/api/client";

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

const list = vi.fn();
const create = vi.fn();
const update = vi.fn();
const remove = vi.fn();
const merge = vi.fn();

vi.mock("@/lib/api/document-types", () => ({
  documentTypesApi: () => ({ list, create, update, delete: remove, merge }),
}));

function setup() {
  vi.clearAllMocks();
  list.mockResolvedValue([
    { id: "type-1", name: "Invoice", retentionDays: 365 },
    { id: "type-2", name: "Bill", retentionDays: null },
  ]);
  return renderWithProviders(<DocumentTypeManager />);
}

describe("DocumentTypeManager", () => {
  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("renders existing types with their retention", async () => {
    setup();

    expect(await screen.findByLabelText("Edit Invoice")).toBeInTheDocument();
    expect(screen.getByText("365 days")).toBeInTheDocument();
    expect(screen.getByLabelText("Edit Bill")).toBeInTheDocument();
    expect(screen.getByText("Indefinite")).toBeInTheDocument();
  });

  it("creates a new type with a retention period", async () => {
    const user = userEvent.setup();
    create.mockResolvedValue({ id: "type-new", name: "Receipt", retentionDays: 90 });
    setup();

    await screen.findByLabelText("Edit Invoice");
    await user.type(screen.getByPlaceholderText("New type name"), "Receipt");
    await user.type(screen.getByPlaceholderText("Retention (days)"), "90");
    await user.click(screen.getByText("Add"));

    await waitFor(() => expect(create).toHaveBeenCalledWith("Receipt", 90));
  });

  it("renames a type and updates its retention inline", async () => {
    const user = userEvent.setup();
    update.mockResolvedValue({ id: "type-1", name: "Renamed", retentionDays: 30 });
    setup();

    await screen.findByLabelText("Edit Invoice");
    await user.click(screen.getByLabelText("Edit Invoice"));
    const nameInput = screen.getByDisplayValue("Invoice");
    await user.clear(nameInput);
    await user.type(nameInput, "Renamed");
    const retentionInput = screen.getByDisplayValue("365");
    await user.clear(retentionInput);
    await user.type(retentionInput, "30");
    await user.click(screen.getByLabelText("Save"));

    await waitFor(() => expect(update).toHaveBeenCalledWith("type-1", "Renamed", 30));
  });

  it("deletes a type after confirmation", async () => {
    const user = userEvent.setup();
    remove.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit Invoice");
    await user.click(screen.getByLabelText("Delete Invoice"));

    await waitFor(() => expect(remove).toHaveBeenCalledWith("type-1"));
  });

  it("merges a type into another after confirmation", async () => {
    const user = userEvent.setup();
    merge.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit Invoice");
    const mergeSelect = screen.getByLabelText("Merge Invoice into…");
    await user.selectOptions(mergeSelect, "type-2");
    await user.click(within(mergeSelect.parentElement as HTMLElement).getByRole("button", { name: "Merge" }));

    await waitFor(() => expect(merge).toHaveBeenCalledWith("type-1", "type-2"));
  });

  it("shows an inline error on duplicate name without crashing", async () => {
    const user = userEvent.setup();
    create.mockRejectedValue(new ApiError(409, "Name already exists"));
    setup();

    await screen.findByLabelText("Edit Invoice");
    await user.type(screen.getByPlaceholderText("New type name"), "Invoice");
    await user.click(screen.getByText("Add"));

    expect(await screen.findByText("This name already exists")).toBeInTheDocument();
  });
});
