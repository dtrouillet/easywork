"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Check, Pencil, Trash2, X } from "lucide-react";
import { tagsApi } from "@/lib/api/tags";
import { ApiError } from "@/lib/api/client";
import { TAG_COLORS } from "@/lib/tag-colors";
import { cn } from "@/lib/utils";
import type { TagDto } from "@/lib/api/types";

function duplicateOrGenericError(e: unknown, label: string): string {
  return e instanceof ApiError && e.status === 409 ? "This name already exists" : `Failed to save ${label}`;
}

export function TagManager() {
  const { data: session } = useSession();
  const token = session!.accessToken;
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [newColor, setNewColor] = useState<string | null>(null);

  const { data: tags, isLoading } = useQuery({ queryKey: ["tags"], queryFn: () => tagsApi(token).list() });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["tags"] });
    queryClient.invalidateQueries({ queryKey: ["documents"] });
  }

  const create = useMutation({
    mutationFn: () => tagsApi(token).create(newName.trim(), newColor ?? undefined),
    onSuccess: () => {
      setNewName("");
      setNewColor(null);
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "tag")),
  });

  const update = useMutation({
    mutationFn: ({ id, name, color }: { id: string; name: string; color: string | null }) =>
      tagsApi(token).update(id, name, color ?? undefined),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "tag")),
  });

  const remove = useMutation({
    mutationFn: (id: string) => tagsApi(token).delete(id),
    onSuccess: invalidate,
  });

  const merge = useMutation({
    mutationFn: ({ sourceId, targetId }: { sourceId: string; targetId: string }) =>
      tagsApi(token).merge(sourceId, targetId),
    onSuccess: invalidate,
  });

  if (isLoading) {
    return <p className="text-center py-8 text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-4">
      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex items-center gap-2">
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="New tag name"
          className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
        />
        <div className="flex gap-1">
          {TAG_COLORS.map((color) => (
            <button
              key={color}
              onClick={() => setNewColor(color === newColor ? null : color)}
              aria-label={`Color ${color}`}
              className={cn(
                "h-5 w-5 rounded-full border-2",
                newColor === color ? "border-foreground" : "border-transparent"
              )}
              style={{ backgroundColor: color }}
            />
          ))}
        </div>
        <button
          onClick={() => newName.trim() && create.mutate()}
          disabled={create.isPending || !newName.trim()}
          className="text-sm px-3 py-1.5 rounded-md bg-primary text-primary-foreground disabled:opacity-50"
        >
          Add
        </button>
      </div>

      {(tags ?? []).length === 0 ? (
        <p className="text-center py-8 text-sm text-muted-foreground">No tags yet.</p>
      ) : (
        <div className="rounded-lg border border-border divide-y divide-border">
          {(tags ?? []).map((tag) => (
            <TagRow
              key={tag.id}
              tag={tag}
              others={(tags ?? []).filter((t) => t.id !== tag.id)}
              onSave={(name, color) => update.mutate({ id: tag.id, name, color })}
              onDelete={() => {
                if (confirm(`Delete tag "${tag.name}"? This cannot be undone.`)) remove.mutate(tag.id);
              }}
              onMerge={(targetId, targetName) => {
                if (
                  confirm(
                    `Merge "${tag.name}" into "${targetName}"? Its documents will be reassigned and "${tag.name}" will be deleted. This cannot be undone.`
                  )
                ) {
                  merge.mutate({ sourceId: tag.id, targetId });
                }
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface TagRowProps {
  tag: TagDto;
  others: TagDto[];
  onSave: (name: string, color: string | null) => void;
  onDelete: () => void;
  onMerge: (targetId: string, targetName: string) => void;
}

function TagRow({ tag, others, onSave, onDelete, onMerge }: TagRowProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(tag.name);
  const [color, setColor] = useState<string | null>(tag.color);
  const [mergeTarget, setMergeTarget] = useState("");

  function save() {
    if (!name.trim()) return;
    onSave(name.trim(), color);
    setEditing(false);
  }

  function cancel() {
    setName(tag.name);
    setColor(tag.color);
    setEditing(false);
  }

  if (editing) {
    return (
      <div className="flex items-center gap-2 px-4 py-2.5">
        <input
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="flex-1 text-sm rounded-md border border-border px-2 py-1 bg-background"
        />
        <div className="flex gap-1">
          {TAG_COLORS.map((c) => (
            <button
              key={c}
              onClick={() => setColor(c === color ? null : c)}
              aria-label={`Color ${c}`}
              className={cn("h-5 w-5 rounded-full border-2", color === c ? "border-foreground" : "border-transparent")}
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
        <button onClick={save} aria-label="Save" className="p-1 text-primary">
          <Check className="h-4 w-4" />
        </button>
        <button onClick={cancel} aria-label="Cancel" className="p-1 text-muted-foreground">
          <X className="h-4 w-4" />
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3 px-4 py-2.5">
      <span className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: tag.color ?? "#999" }} />
      <span className="flex-1 text-sm font-medium truncate">{tag.name}</span>

      {others.length > 0 && (
        <div className="flex items-center gap-1">
          <select
            value={mergeTarget}
            onChange={(e) => setMergeTarget(e.target.value)}
            aria-label={`Merge ${tag.name} into…`}
            className="text-xs rounded-md border border-border px-1.5 py-1 bg-background"
          >
            <option value="">Merge into…</option>
            {others.map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
          <button
            onClick={() => {
              const target = others.find((o) => o.id === mergeTarget);
              if (target) {
                onMerge(target.id, target.name);
                setMergeTarget("");
              }
            }}
            disabled={!mergeTarget}
            className="text-xs px-2 py-1 rounded-md border border-border text-muted-foreground disabled:opacity-40"
          >
            Merge
          </button>
        </div>
      )}

      <button onClick={() => setEditing(true)} aria-label={`Edit ${tag.name}`} className="p-1 text-muted-foreground hover:text-foreground">
        <Pencil className="h-4 w-4" />
      </button>
      <button onClick={onDelete} aria-label={`Delete ${tag.name}`} className="p-1 text-muted-foreground hover:text-destructive">
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}
