import type { DocumentDto } from "@/lib/api/types";

export interface TreeNode {
  key: string;
  label: string;
  count: number;
  children: TreeNode[];
}

const NO_TYPE = "No type";
const NO_CORRESPONDENT = "No correspondent";
const NO_DATE = "No date";

function typeLabel(doc: DocumentDto): string {
  return doc.documentType?.name ?? NO_TYPE;
}

function correspondentLabel(doc: DocumentDto): string {
  return doc.correspondent?.name ?? NO_CORRESPONDENT;
}

function yearLabel(doc: DocumentDto): string {
  return doc.documentDate ? String(new Date(doc.documentDate).getFullYear()) : NO_DATE;
}

export function buildDocumentTree(docs: DocumentDto[]): TreeNode[] {
  const byType = new Map<string, Map<string, Map<string, DocumentDto[]>>>();

  for (const doc of docs) {
    const type = typeLabel(doc);
    const correspondent = correspondentLabel(doc);
    const year = yearLabel(doc);

    const byCorrespondent = byType.get(type) ?? new Map();
    byType.set(type, byCorrespondent);

    const byYear = byCorrespondent.get(correspondent) ?? new Map();
    byCorrespondent.set(correspondent, byYear);

    const docsForYear = byYear.get(year) ?? [];
    docsForYear.push(doc);
    byYear.set(year, docsForYear);
  }

  return [...byType.entries()].map(([type, byCorrespondent]) => {
    const correspondentNodes: TreeNode[] = [...byCorrespondent.entries()].map(
      ([correspondent, byYear]) => {
        const yearNodes: TreeNode[] = [...byYear.entries()].map(([year, docsForYear]) => ({
          key: `${type}|${correspondent}|${year}`,
          label: year,
          count: docsForYear.length,
          children: [],
        }));
        return {
          key: `${type}|${correspondent}`,
          label: correspondent,
          count: yearNodes.reduce((sum, n) => sum + n.count, 0),
          children: yearNodes,
        };
      }
    );
    return {
      key: type,
      label: type,
      count: correspondentNodes.reduce((sum, n) => sum + n.count, 0),
      children: correspondentNodes,
    };
  });
}

export function filterDocsByPath(docs: DocumentDto[], path: string[]): DocumentDto[] {
  return docs.filter((doc) => {
    if (path.length === 0) return true;
    if (typeLabel(doc) !== path[0]) return false;
    if (path.length === 1) return true;
    if (correspondentLabel(doc) !== path[1]) return false;
    if (path.length === 2) return true;
    return yearLabel(doc) === path[2];
  });
}

export function pathToBreadcrumb(path: string[]): string {
  return ["All documents", ...path].join(" > ");
}

export function formatLocation(doc: DocumentDto): string {
  return `/${typeLabel(doc)}/${correspondentLabel(doc)}/${yearLabel(doc)}`;
}
