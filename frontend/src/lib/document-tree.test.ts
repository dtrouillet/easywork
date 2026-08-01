import { describe, expect, it } from "vitest";
import { buildDocumentTree, filterDocsByPath, formatLocation, pathToBreadcrumb } from "./document-tree";
import type { DocumentDto } from "@/lib/api/types";

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

describe("buildDocumentTree", () => {
  it("groups documents by type, then correspondent, then year", () => {
    const tree = buildDocumentTree([makeDoc(), makeDoc({ id: "doc-2" })]);

    expect(tree).toHaveLength(1);
    expect(tree[0].label).toBe("Invoice");
    expect(tree[0].count).toBe(2);
    expect(tree[0].children[0].label).toBe("EDF");
    expect(tree[0].children[0].count).toBe(2);
    expect(tree[0].children[0].children[0].label).toBe("2026");
    expect(tree[0].children[0].children[0].count).toBe(2);
  });

  it("splits documents into separate branches by correspondent and year", () => {
    const tree = buildDocumentTree([
      makeDoc({ id: "doc-1", correspondent: { id: "c1", name: "EDF" }, documentDate: "2026-01-01" }),
      makeDoc({ id: "doc-2", correspondent: { id: "c2", name: "MGEN" }, documentDate: "2025-01-01" }),
    ]);

    expect(tree[0].children).toHaveLength(2);
    const edf = tree[0].children.find((n) => n.label === "EDF")!;
    const mgen = tree[0].children.find((n) => n.label === "MGEN")!;
    expect(edf.children[0].label).toBe("2026");
    expect(mgen.children[0].label).toBe("2025");
  });

  it("falls back to placeholder buckets for missing type/correspondent/date", () => {
    const tree = buildDocumentTree([
      makeDoc({ documentType: null, correspondent: null, documentDate: null }),
    ]);

    expect(tree[0].label).toBe("No type");
    expect(tree[0].children[0].label).toBe("No correspondent");
    expect(tree[0].children[0].children[0].label).toBe("No date");
  });

  it("returns an empty array for no documents", () => {
    expect(buildDocumentTree([])).toEqual([]);
  });
});

describe("filterDocsByPath", () => {
  const docs = [
    makeDoc({ id: "a", documentType: { id: "t1", name: "Invoice", retentionDays: null } }),
    makeDoc({ id: "b", documentType: { id: "t2", name: "Statement", retentionDays: null } }),
  ];

  it("returns all documents for an empty path", () => {
    expect(filterDocsByPath(docs, [])).toEqual(docs);
  });

  it("filters by type at depth 1", () => {
    expect(filterDocsByPath(docs, ["Invoice"]).map((d) => d.id)).toEqual(["a"]);
  });

  it("filters by type and correspondent at depth 2", () => {
    const withDifferentCorrespondents = [
      makeDoc({ id: "a", correspondent: { id: "c1", name: "EDF" } }),
      makeDoc({ id: "b", correspondent: { id: "c2", name: "MGEN" } }),
    ];
    expect(
      filterDocsByPath(withDifferentCorrespondents, ["Invoice", "EDF"]).map((d) => d.id)
    ).toEqual(["a"]);
  });

  it("filters by type, correspondent and year at depth 3", () => {
    const withDifferentYears = [
      makeDoc({ id: "a", documentDate: "2026-01-01" }),
      makeDoc({ id: "b", documentDate: "2025-01-01" }),
    ];
    expect(
      filterDocsByPath(withDifferentYears, ["Invoice", "EDF", "2026"]).map((d) => d.id)
    ).toEqual(["a"]);
  });
});

describe("pathToBreadcrumb", () => {
  it("prefixes with 'All documents'", () => {
    expect(pathToBreadcrumb([])).toBe("All documents");
  });

  it("joins segments with a separator", () => {
    expect(pathToBreadcrumb(["Invoice", "EDF", "2026"])).toBe("All documents > Invoice > EDF > 2026");
  });
});

describe("formatLocation", () => {
  it("builds a path from type/correspondent/year", () => {
    expect(formatLocation(makeDoc())).toBe("/Invoice/EDF/2026");
  });

  it("uses placeholder labels for missing fields", () => {
    expect(formatLocation(makeDoc({ documentType: null, correspondent: null, documentDate: null }))).toBe(
      "/No type/No correspondent/No date"
    );
  });
});
