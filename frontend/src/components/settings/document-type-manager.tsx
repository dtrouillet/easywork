"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Check, Pencil, Trash2, X } from "lucide-react";
import { documentTypesApi } from "@/lib/api/document-types";
import { ApiError } from "@/lib/api/client";
import type { DocumentTypeDto } from "@/lib/api/types";

function duplicateOrGenericError(e: unknown, label: string): string {
  return e instanceof ApiError && e.status === 409 ? "This name already exists" : `Failed to save ${label}`;
}

function parseRetentionDays(value: string): number | undefined {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function DocumentTypeManager() {
  const { data: session } = useSession();
  const token = session!.accessToken;
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [newRetentionDays, setNewRetentionDays] = useState("");

  const { data: types, isLoading } = useQuery({
    queryKey: ["document-types"],
    queryFn: () => documentTypesApi(token).list(),
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["document-types"] });
    queryClient.invalidateQueries({ queryKey: ["documents"] });
  }

  const create = useMutation({
    mutationFn: () => documentTypesApi(token).create(newName.trim(), parseRetentionDays(newRetentionDays)),
    onSuccess: () => {
      setNewName("");
      setNewRetentionDays("");
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "document type")),
  });

  const update = useMutation({
    mutationFn: ({ id, name, retentionDays }: { id: string; name: string; retentionDays?: number }) =>
      documentTypesApi(token).update(id, name, retentionDays),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "document type")),
  });

  const remove = useMutation({
    mutationFn: (id: string) => documentTypesApi(token).delete(id),
    onSuccess: invalidate,
  });

  const merge = useMutation({
    mutationFn: ({ sourceId, targetId }: { sourceId: string; targetId: string }) =>
      documentTypesApi(token).merge(sourceId, targetId),
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
          placeholder="New type name"
          className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
        />
        <input
          value={newRetentionDays}
          onChange={(e) => setNewRetentionDays(e.target.value)}
          type="number"
          min={0}
          placeholder="Retention (days)"
          className="w-36 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
        />
        <button
          onClick={() => newName.trim() && create.mutate()}
          disabled={create.isPending || !newName.trim()}
          className="text-sm px-3 py-1.5 rounded-md bg-primary text-primary-foreground disabled:opacity-50"
        >
          Add
        </button>
      </div>

      {(types ?? []).length === 0 ? (
        <p className="text-center py-8 text-sm text-muted-foreground">No document types yet.</p>
      ) : (
        <div className="rounded-lg border border-border divide-y divide-border">
          {(types ?? []).map((type) => (
            <DocumentTypeRow
              key={type.id}
              type={type}
              others={(types ?? []).filter((t) => t.id !== type.id)}
              onSave={(name, retentionDays) => update.mutate({ id: type.id, name, retentionDays })}
              onDelete={() => {
                if (confirm(`Delete document type "${type.name}"? This cannot be undone.`)) {
                  remove.mutate(type.id);
                }
              }}
              onMerge={(targetId, targetName) => {
                if (
                  confirm(
                    `Merge "${type.name}" into "${targetName}"? Its documents will be reassigned and "${type.name}" will be deleted. This cannot be undone.`
                  )
                ) {
                  merge.mutate({ sourceId: type.id, targetId });
                }
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface DocumentTypeRowProps {
  type: DocumentTypeDto;
  others: DocumentTypeDto[];
  onSave: (name: string, retentionDays?: number) => void;
  onDelete: () => void;
  onMerge: (targetId: string, targetName: string) => void;
}

function DocumentTypeRow({ type, others, onSave, onDelete, onMerge }: DocumentTypeRowProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(type.name);
  const [retentionDays, setRetentionDays] = useState(type.retentionDays != null ? String(type.retentionDays) : "");
  const [mergeTarget, setMergeTarget] = useState("");

  function save() {
    if (!name.trim()) return;
    onSave(name.trim(), parseRetentionDays(retentionDays));
    setEditing(false);
  }

  function cancel() {
    setName(type.name);
    setRetentionDays(type.retentionDays != null ? String(type.retentionDays) : "");
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
        <input
          value={retentionDays}
          onChange={(e) => setRetentionDays(e.target.value)}
          type="number"
          min={0}
          placeholder="Retention (days)"
          className="w-36 text-sm rounded-md border border-border px-2 py-1 bg-background"
        />
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
      <span className="flex-1 text-sm font-medium truncate">{type.name}</span>
      <span className="text-xs text-muted-foreground w-28 shrink-0">
        {type.retentionDays != null ? `${type.retentionDays} days` : "Indefinite"}
      </span>

      {others.length > 0 && (
        <div className="flex items-center gap-1">
          <select
            value={mergeTarget}
            onChange={(e) => setMergeTarget(e.target.value)}
            aria-label={`Merge ${type.name} into…`}
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

      <button
        onClick={() => setEditing(true)}
        aria-label={`Edit ${type.name}`}
        className="p-1 text-muted-foreground hover:text-foreground"
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button onClick={onDelete} aria-label={`Delete ${type.name}`} className="p-1 text-muted-foreground hover:text-destructive">
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}
