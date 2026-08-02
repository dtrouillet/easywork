import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/test-utils";
import { UploadDialog } from "./upload-dialog";
import { useUiStore } from "@/store/ui-store";
import { ApiError } from "@/lib/api/client";

const uploadWithProgress = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => ({
    data: { accessToken: "test-access-token", user: { name: "Test User" }, expires: "2099-01-01T00:00:00.000Z" },
  }),
}));

vi.mock("@/lib/api/documents", () => ({
  documentsApi: () => ({ uploadWithProgress }),
}));

function pdfFile(name: string) {
  return new File(["content"], name, { type: "application/pdf" });
}

beforeEach(() => {
  useUiStore.setState({ uploadDialogOpen: true, uploadQueue: [] });
  uploadWithProgress.mockReset();
  uploadWithProgress.mockResolvedValue({ id: "doc-1" });
});

afterEach(() => {
  useUiStore.setState({ uploadDialogOpen: false, uploadQueue: [] });
});

describe("UploadDialog", () => {
  it("renders nothing when closed", () => {
    useUiStore.setState({ uploadDialogOpen: false });

    renderWithProviders(<UploadDialog />);

    expect(screen.queryByText("Upload documents")).not.toBeInTheDocument();
  });

  it("uploads every selected file and shows them as done", async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadDialog />);

    const input = screen.getByTestId("file-input");
    await user.upload(input, [pdfFile("a.pdf"), pdfFile("b.pdf")]);

    expect(screen.getByText("a.pdf")).toBeInTheDocument();
    expect(screen.getByText("b.pdf")).toBeInTheDocument();
    expect(uploadWithProgress).toHaveBeenCalledTimes(2);

    await waitFor(() => expect(screen.getByText("2 uploaded")).toBeInTheDocument());
  });

  it("populates the queue when files are dropped", async () => {
    renderWithProviders(<UploadDialog />);

    const dropzone = screen.getByText(/Drop files here/).closest("div")!;
    fireEvent.drop(dropzone, {
      dataTransfer: { files: [pdfFile("dropped.pdf")] },
    });

    await waitFor(() => expect(screen.getByText("dropped.pdf")).toBeInTheDocument());
  });

  it("shows an inline error and a retry button when an upload fails, and retry re-invokes the upload", async () => {
    const user = userEvent.setup();
    uploadWithProgress.mockRejectedValueOnce(new ApiError(415, "Unsupported file type"));
    renderWithProviders(<UploadDialog />);

    const input = screen.getByTestId("file-input");
    await user.upload(input, [pdfFile("bad.pdf")]);

    await waitFor(() => expect(screen.getByText("Unsupported file type")).toBeInTheDocument());
    expect(uploadWithProgress).toHaveBeenCalledTimes(1);

    uploadWithProgress.mockResolvedValueOnce({ id: "doc-2" });
    await user.click(screen.getByTitle("Retry"));

    await waitFor(() => expect(uploadWithProgress).toHaveBeenCalledTimes(2));
  });

  it("removes a queued item without affecting the others", async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadDialog />);

    const input = screen.getByTestId("file-input");
    await user.upload(input, [pdfFile("keep.pdf"), pdfFile("remove.pdf")]);

    await waitFor(() => expect(screen.getByText("remove.pdf")).toBeInTheDocument());
    const removeButtons = screen.getAllByTitle("Remove");
    await user.click(removeButtons[1]);

    expect(screen.queryByText("remove.pdf")).not.toBeInTheDocument();
    expect(screen.getByText("keep.pdf")).toBeInTheDocument();
  });

  it("clears the whole queue when the dialog is closed", async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadDialog />);

    const input = screen.getByTestId("file-input");
    await user.upload(input, [pdfFile("a.pdf")]);
    await waitFor(() => expect(screen.getByText("a.pdf")).toBeInTheDocument());

    await user.click(screen.getByText("Close"));

    expect(useUiStore.getState().uploadQueue).toHaveLength(0);
  });
});
