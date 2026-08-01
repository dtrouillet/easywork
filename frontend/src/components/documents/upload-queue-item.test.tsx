import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UploadQueueItem } from "./upload-queue-item";
import type { UploadQueueItem as UploadQueueItemType } from "@/store/ui-store";

function makeItem(overrides: Partial<UploadQueueItemType> = {}): UploadQueueItemType {
  return {
    id: "1",
    file: new File(["content"], "invoice.pdf", { type: "application/pdf" }),
    status: "pending",
    progress: 0,
    ...overrides,
  };
}

describe("UploadQueueItem", () => {
  it("renders the file name", () => {
    render(<UploadQueueItem item={makeItem()} onRemove={vi.fn()} onRetry={vi.fn()} />);

    expect(screen.getByText("invoice.pdf")).toBeInTheDocument();
  });

  it("shows a progress bar reflecting the current percentage while uploading", () => {
    render(
      <UploadQueueItem
        item={makeItem({ status: "uploading", progress: 42 })}
        onRemove={vi.fn()}
        onRetry={vi.fn()}
      />
    );

    expect(screen.getByText("42%")).toBeInTheDocument();
  });

  it("shows the error message and a retry button only when status is error", () => {
    render(
      <UploadQueueItem
        item={makeItem({ status: "error", error: "Fichier trop volumineux" })}
        onRemove={vi.fn()}
        onRetry={vi.fn()}
      />
    );

    expect(screen.getByText("Fichier trop volumineux")).toBeInTheDocument();
    expect(screen.getByTitle("Réessayer")).toBeInTheDocument();
  });

  it("does not render a retry button for a pending item", () => {
    render(<UploadQueueItem item={makeItem()} onRemove={vi.fn()} onRetry={vi.fn()} />);

    expect(screen.queryByTitle("Réessayer")).not.toBeInTheDocument();
  });

  it("calls onRemove when the remove button is clicked", async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    render(<UploadQueueItem item={makeItem({ id: "42" })} onRemove={onRemove} onRetry={vi.fn()} />);

    await user.click(screen.getByTitle("Retirer"));

    expect(onRemove).toHaveBeenCalledWith("42");
  });

  it("calls onRetry when the retry button is clicked", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <UploadQueueItem
        item={makeItem({ id: "42", status: "error", error: "oops" })}
        onRemove={vi.fn()}
        onRetry={onRetry}
      />
    );

    await user.click(screen.getByTitle("Réessayer"));

    expect(onRetry).toHaveBeenCalledWith("42");
  });
});
