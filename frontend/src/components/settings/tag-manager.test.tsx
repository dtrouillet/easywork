import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { TagManager } from "./tag-manager";
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

vi.mock("@/lib/api/tags", () => ({
  tagsApi: () => ({ list, create, update, delete: remove, merge }),
}));

function setup() {
  vi.clearAllMocks();
  list.mockResolvedValue([
    { id: "tag-1", name: "Energy", color: "#2F6F5E" },
    { id: "tag-2", name: "Urgent", color: "#A34B4B" },
  ]);
  return renderWithProviders(<TagManager />);
}

describe("TagManager", () => {
  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("renders existing tags", async () => {
    setup();

    expect(await screen.findByLabelText("Edit Energy")).toBeInTheDocument();
    expect(screen.getByLabelText("Edit Urgent")).toBeInTheDocument();
  });

  it("creates a new tag", async () => {
    const user = userEvent.setup();
    create.mockResolvedValue({ id: "tag-new", name: "Receipts", color: null });
    setup();

    await screen.findByLabelText("Edit Energy");
    await user.type(screen.getByPlaceholderText("New tag name"), "Receipts");
    await user.click(screen.getByText("Add"));

    await waitFor(() => expect(create).toHaveBeenCalledWith("Receipts", undefined));
  });

  it("renames a tag inline", async () => {
    const user = userEvent.setup();
    update.mockResolvedValue({ id: "tag-1", name: "Renamed", color: "#2F6F5E" });
    setup();

    await screen.findByLabelText("Edit Energy");
    await user.click(screen.getByLabelText("Edit Energy"));
    const input = screen.getByDisplayValue("Energy");
    await user.clear(input);
    await user.type(input, "Renamed");
    await user.click(screen.getByLabelText("Save"));

    await waitFor(() => expect(update).toHaveBeenCalledWith("tag-1", "Renamed", "#2F6F5E"));
  });

  it("deletes a tag after confirmation", async () => {
    const user = userEvent.setup();
    remove.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit Energy");
    await user.click(screen.getByLabelText("Delete Energy"));

    await waitFor(() => expect(remove).toHaveBeenCalledWith("tag-1"));
  });

  it("does not delete when the confirmation is dismissed", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    setup();

    await screen.findByLabelText("Edit Energy");
    await user.click(screen.getByLabelText("Delete Energy"));

    expect(remove).not.toHaveBeenCalled();
  });

  it("merges a tag into another after confirmation", async () => {
    const user = userEvent.setup();
    merge.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit Energy");
    const mergeSelect = screen.getByLabelText("Merge Energy into…");
    await user.selectOptions(mergeSelect, "tag-2");
    await user.click(within(mergeSelect.parentElement as HTMLElement).getByRole("button", { name: "Merge" }));

    await waitFor(() => expect(merge).toHaveBeenCalledWith("tag-1", "tag-2"));
  });

  it("shows an inline error on duplicate name without crashing", async () => {
    const user = userEvent.setup();
    create.mockRejectedValue(new ApiError(409, "Name already exists"));
    setup();

    await screen.findByLabelText("Edit Energy");
    await user.type(screen.getByPlaceholderText("New tag name"), "Energy");
    await user.click(screen.getByText("Add"));

    expect(await screen.findByText("This name already exists")).toBeInTheDocument();
  });
});
