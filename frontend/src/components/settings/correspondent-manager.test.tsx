import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { CorrespondentManager } from "./correspondent-manager";
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

vi.mock("@/lib/api/correspondents", () => ({
  correspondentsApi: () => ({ list, create, update, delete: remove, merge }),
}));

function setup() {
  vi.clearAllMocks();
  list.mockResolvedValue([
    { id: "corr-1", name: "EDF" },
    { id: "corr-2", name: "E.D.F" },
  ]);
  return renderWithProviders(<CorrespondentManager />);
}

describe("CorrespondentManager", () => {
  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("renders existing correspondents", async () => {
    setup();

    expect(await screen.findByLabelText("Edit EDF")).toBeInTheDocument();
    expect(screen.getByLabelText("Edit E.D.F")).toBeInTheDocument();
  });

  it("creates a new correspondent", async () => {
    const user = userEvent.setup();
    create.mockResolvedValue({ id: "corr-new", name: "Allianz" });
    setup();

    await screen.findByLabelText("Edit EDF");
    await user.type(screen.getByPlaceholderText("New correspondent name"), "Allianz");
    await user.click(screen.getByText("Add"));

    await waitFor(() => expect(create).toHaveBeenCalledWith("Allianz"));
  });

  it("renames a correspondent inline", async () => {
    const user = userEvent.setup();
    update.mockResolvedValue({ id: "corr-1", name: "Renamed" });
    setup();

    await screen.findByLabelText("Edit EDF");
    await user.click(screen.getByLabelText("Edit EDF"));
    const input = screen.getByDisplayValue("EDF");
    await user.clear(input);
    await user.type(input, "Renamed");
    await user.click(screen.getByLabelText("Save"));

    await waitFor(() => expect(update).toHaveBeenCalledWith("corr-1", "Renamed"));
  });

  it("deletes a correspondent after confirmation", async () => {
    const user = userEvent.setup();
    remove.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit EDF");
    await user.click(screen.getByLabelText("Delete EDF"));

    await waitFor(() => expect(remove).toHaveBeenCalledWith("corr-1"));
  });

  it("merges a correspondent into another after confirmation", async () => {
    const user = userEvent.setup();
    merge.mockResolvedValue(undefined);
    setup();

    await screen.findByLabelText("Edit EDF");
    const mergeSelect = screen.getByLabelText("Merge EDF into…");
    await user.selectOptions(mergeSelect, "corr-2");
    await user.click(within(mergeSelect.parentElement as HTMLElement).getByRole("button", { name: "Merge" }));

    await waitFor(() => expect(merge).toHaveBeenCalledWith("corr-1", "corr-2"));
  });

  it("shows an inline error on duplicate name without crashing", async () => {
    const user = userEvent.setup();
    create.mockRejectedValue(new ApiError(409, "Name already exists"));
    setup();

    await screen.findByLabelText("Edit EDF");
    await user.type(screen.getByPlaceholderText("New correspondent name"), "EDF");
    await user.click(screen.getByText("Add"));

    expect(await screen.findByText("This name already exists")).toBeInTheDocument();
  });
});
