"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Plus } from "lucide-react";
import { tagsApi } from "@/lib/api/tags";
import { correspondentsApi } from "@/lib/api/correspondents";
import { documentTypesApi } from "@/lib/api/document-types";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type { DocumentDto, DocumentUpdateRequest } from "@/lib/api/types";

const TAG_COLORS = ["#2F6F5E", "#C98A2C", "#5B6EAE", "#A34B4B", "#6B7280", "#0EA5E9"];
const NEW_OPTION = "__new__";

interface ClassificationEditorProps {
  doc: DocumentDto;
  onSave: (request: DocumentUpdateRequest) => void;
  saving: boolean;
}

export function ClassificationEditor({ doc, onSave, saving }: ClassificationEditorProps) {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const token = session!.accessToken;

  const { data: tags } = useQuery({ queryKey: ["tags"], queryFn: () => tagsApi(token).list() });
  const { data: correspondents } = useQuery({
    queryKey: ["correspondents"],
    queryFn: () => correspondentsApi(token).list(),
  });
  const { data: documentTypes } = useQuery({
    queryKey: ["document-types"],
    queryFn: () => documentTypesApi(token).list(),
  });

  const [correspondentId, setCorrespondentId] = useState(doc.correspondent?.id ?? "");
  const [documentTypeId, setDocumentTypeId] = useState(doc.documentType?.id ?? "");
  const [documentDate, setDocumentDate] = useState(doc.documentDate ?? "");
  const [tagIds, setTagIds] = useState<Set<string>>(new Set(doc.tags.map((t) => t.id)));

  const [addingCorrespondent, setAddingCorrespondent] = useState(false);
  const [addingType, setAddingType] = useState(false);
  const [addingTag, setAddingTag] = useState(false);
  const [newCorrespondentName, setNewCorrespondentName] = useState("");
  const [newTypeName, setNewTypeName] = useState("");
  const [newTagName, setNewTagName] = useState("");
  const [newTagColor, setNewTagColor] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function toggleTag(id: string) {
    setTagIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function duplicateOrGenericError(e: unknown, label: string): string {
    return e instanceof ApiError && e.status === 409 ? "This name already exists" : `Failed to create ${label}`;
  }

  async function handleCreateCorrespondent() {
    if (!newCorrespondentName.trim()) return;
    try {
      const created = await correspondentsApi(token).create(newCorrespondentName.trim());
      await queryClient.invalidateQueries({ queryKey: ["correspondents"] });
      setCorrespondentId(created.id);
      setNewCorrespondentName("");
      setAddingCorrespondent(false);
      setError(null);
    } catch (e) {
      setError(duplicateOrGenericError(e, "correspondent"));
    }
  }

  async function handleCreateType() {
    if (!newTypeName.trim()) return;
    try {
      const created = await documentTypesApi(token).create(newTypeName.trim());
      await queryClient.invalidateQueries({ queryKey: ["document-types"] });
      setDocumentTypeId(created.id);
      setNewTypeName("");
      setAddingType(false);
      setError(null);
    } catch (e) {
      setError(duplicateOrGenericError(e, "document type"));
    }
  }

  async function handleCreateTag() {
    if (!newTagName.trim()) return;
    try {
      const created = await tagsApi(token).create(newTagName.trim(), newTagColor ?? undefined);
      await queryClient.invalidateQueries({ queryKey: ["tags"] });
      toggleTag(created.id);
      setNewTagName("");
      setNewTagColor(null);
      setAddingTag(false);
      setError(null);
    } catch (e) {
      setError(duplicateOrGenericError(e, "tag"));
    }
  }

  function handleSave() {
    onSave({
      correspondentId: correspondentId || null,
      documentTypeId: documentTypeId || null,
      documentDate: documentDate || null,
      tagIds: [...tagIds],
    });
  }

  return (
    <div className="space-y-4">
      {error && <p className="text-sm text-destructive">{error}</p>}

      <div>
        <label htmlFor="document-date" className="text-xs text-muted-foreground block mb-1">
          Document date
        </label>
        <input
          id="document-date"
          type="date"
          value={documentDate}
          onChange={(e) => setDocumentDate(e.target.value)}
          className="w-full text-sm rounded-md border border-border px-2 py-1.5 bg-background"
        />
      </div>

      <div>
        <label className="text-xs text-muted-foreground block mb-1">Correspondent</label>
        {addingCorrespondent ? (
          <div className="flex gap-2">
            <input
              autoFocus
              value={newCorrespondentName}
              onChange={(e) => setNewCorrespondentName(e.target.value)}
              placeholder="New correspondent name"
              className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
            />
            <button
              onClick={handleCreateCorrespondent}
              className="text-sm px-2 rounded-md bg-primary text-primary-foreground"
            >
              Add
            </button>
            <button
              onClick={() => setAddingCorrespondent(false)}
              className="text-sm px-2 text-muted-foreground"
            >
              Cancel
            </button>
          </div>
        ) : (
          <select
            value={correspondentId}
            onChange={(e) => {
              if (e.target.value === NEW_OPTION) {
                setAddingCorrespondent(true);
                return;
              }
              setCorrespondentId(e.target.value);
            }}
            className="w-full text-sm rounded-md border border-border px-2 py-1.5 bg-background"
          >
            <option value="">—</option>
            {(correspondents ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
            <option value={NEW_OPTION}>+ New correspondent…</option>
          </select>
        )}
      </div>

      <div>
        <label className="text-xs text-muted-foreground block mb-1">Type</label>
        {addingType ? (
          <div className="flex gap-2">
            <input
              autoFocus
              value={newTypeName}
              onChange={(e) => setNewTypeName(e.target.value)}
              placeholder="New type name"
              className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
            />
            <button
              onClick={handleCreateType}
              className="text-sm px-2 rounded-md bg-primary text-primary-foreground"
            >
              Add
            </button>
            <button onClick={() => setAddingType(false)} className="text-sm px-2 text-muted-foreground">
              Cancel
            </button>
          </div>
        ) : (
          <select
            value={documentTypeId}
            onChange={(e) => {
              if (e.target.value === NEW_OPTION) {
                setAddingType(true);
                return;
              }
              setDocumentTypeId(e.target.value);
            }}
            className="w-full text-sm rounded-md border border-border px-2 py-1.5 bg-background"
          >
            <option value="">—</option>
            {(documentTypes ?? []).map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
            <option value={NEW_OPTION}>+ New type…</option>
          </select>
        )}
      </div>

      <div>
        <label className="text-xs text-muted-foreground block mb-1">Tags</label>
        <div className="flex flex-wrap items-center gap-1.5 mb-2">
          {(tags ?? []).map((tag) => {
            const selected = tagIds.has(tag.id);
            return (
              <button
                key={tag.id}
                onClick={() => toggleTag(tag.id)}
                className={cn(
                  "text-xs px-2.5 py-1 rounded-full font-medium border transition-colors",
                  selected ? "border-transparent" : "border-border text-muted-foreground"
                )}
                style={
                  selected
                    ? { backgroundColor: `${tag.color ?? "#999"}22`, color: tag.color ?? "#666" }
                    : undefined
                }
              >
                {tag.name}
              </button>
            );
          })}
          {!addingTag && (
            <button
              onClick={() => setAddingTag(true)}
              className="text-xs px-2.5 py-1 rounded-full border border-dashed border-border text-muted-foreground flex items-center gap-1"
            >
              <Plus className="h-3 w-3" /> New tag
            </button>
          )}
        </div>
        {addingTag && (
          <div className="flex items-center gap-2">
            <input
              autoFocus
              value={newTagName}
              onChange={(e) => setNewTagName(e.target.value)}
              placeholder="New tag name"
              className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
            />
            <div className="flex gap-1">
              {TAG_COLORS.map((color) => (
                <button
                  key={color}
                  onClick={() => setNewTagColor(color === newTagColor ? null : color)}
                  aria-label={`Color ${color}`}
                  className={cn(
                    "h-5 w-5 rounded-full border-2",
                    newTagColor === color ? "border-foreground" : "border-transparent"
                  )}
                  style={{ backgroundColor: color }}
                />
              ))}
            </div>
            <button
              onClick={handleCreateTag}
              className="text-sm px-2 rounded-md bg-primary text-primary-foreground"
            >
              Add
            </button>
            <button onClick={() => setAddingTag(false)} className="text-sm px-2 text-muted-foreground">
              Cancel
            </button>
          </div>
        )}
      </div>

      <button
        onClick={handleSave}
        disabled={saving}
        className="text-sm px-3 py-1.5 rounded-md bg-primary text-primary-foreground disabled:opacity-50"
      >
        {saving ? "Saving…" : "Save classification"}
      </button>
    </div>
  );
}
