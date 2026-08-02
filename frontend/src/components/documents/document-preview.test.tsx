import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DocumentPreview } from "./document-preview";

beforeEach(() => {
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:mock-url"),
    revokeObjectURL: vi.fn(),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const noop = () => {};

describe("DocumentPreview", () => {
  it("shows a loading state", () => {
    const { container } = render(
      <DocumentPreview
        blob={undefined}
        isLoading
        isError={false}
        mimeType="application/pdf"
        originalFilename="doc.pdf"
        onDownload={noop}
      />
    );

    expect(container.querySelector(".animate-spin")).toBeInTheDocument();
  });

  it("shows an error state when the file failed to load", () => {
    render(
      <DocumentPreview
        blob={undefined}
        isLoading={false}
        isError
        mimeType="application/pdf"
        originalFilename="doc.pdf"
        onDownload={noop}
      />
    );

    expect(screen.getByText("Couldn't load a preview.")).toBeInTheDocument();
  });

  it("renders an iframe for a PDF", () => {
    const blob = new Blob(["%PDF-1.4"], { type: "application/pdf" });
    render(
      <DocumentPreview
        blob={blob}
        isLoading={false}
        isError={false}
        mimeType="application/pdf"
        originalFilename="invoice.pdf"
        onDownload={noop}
      />
    );

    const iframe = screen.getByTitle("invoice.pdf");
    expect(iframe.tagName).toBe("IFRAME");
    expect(iframe).toHaveAttribute("src", "blob:mock-url");
  });

  it("renders an img for an image", () => {
    const blob = new Blob(["fake"], { type: "image/png" });
    render(
      <DocumentPreview
        blob={blob}
        isLoading={false}
        isError={false}
        mimeType="image/png"
        originalFilename="photo.png"
        onDownload={noop}
      />
    );

    const img = screen.getByAltText("photo.png");
    expect(img).toHaveAttribute("src", "blob:mock-url");
  });

  it("renders the extracted text content for a plain text file", async () => {
    const blob = new Blob(["hello world"], { type: "text/plain" });
    render(
      <DocumentPreview
        blob={blob}
        isLoading={false}
        isError={false}
        mimeType="text/plain"
        originalFilename="notes.txt"
        onDownload={noop}
      />
    );

    expect(await screen.findByText("hello world")).toBeInTheDocument();
  });

  it("falls back to a download prompt for a non-previewable type", async () => {
    const user = userEvent.setup();
    const onDownload = vi.fn();
    const blob = new Blob(["fake"], {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });
    render(
      <DocumentPreview
        blob={blob}
        isLoading={false}
        isError={false}
        mimeType="application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        originalFilename="report.docx"
        onDownload={onDownload}
      />
    );

    expect(screen.getByText("Preview isn't available for this file type.")).toBeInTheDocument();
    await user.click(screen.getByText("Download to view"));
    expect(onDownload).toHaveBeenCalledTimes(1);
  });
});
