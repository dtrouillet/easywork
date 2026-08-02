"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { Check, Pencil, Trash2, X } from "lucide-react";
import { correspondentsApi } from "@/lib/api/correspondents";
import { ApiError } from "@/lib/api/client";
import type { CorrespondentDto } from "@/lib/api/types";

function duplicateOrGenericError(e: unknown, label: string): string {
  return e instanceof ApiError && e.status === 409 ? "This name already exists" : `Failed to save ${label}`;
}

export function CorrespondentManager() {
  const { data: session } = useSession();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");

  const { data: correspondents, isLoading } = useQuery({
    queryKey: ["correspondents"],
    queryFn: () => correspondentsApi(session!.accessToken).list(),
    enabled: !!session,
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["correspondents"] });
    queryClient.invalidateQueries({ queryKey: ["documents"] });
  }

  const create = useMutation({
    mutationFn: () => correspondentsApi(session!.accessToken).create(newName.trim()),
    onSuccess: () => {
      setNewName("");
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "correspondent")),
  });

  const update = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      correspondentsApi(session!.accessToken).update(id, name),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e) => setError(duplicateOrGenericError(e, "correspondent")),
  });

  const remove = useMutation({
    mutationFn: (id: string) => correspondentsApi(session!.accessToken).delete(id),
    onSuccess: invalidate,
  });

  const merge = useMutation({
    mutationFn: ({ sourceId, targetId }: { sourceId: string; targetId: string }) =>
      correspondentsApi(session!.accessToken).merge(sourceId, targetId),
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
          placeholder="New correspondent name"
          className="flex-1 text-sm rounded-md border border-border px-2 py-1.5 bg-background"
        />
        <button
          onClick={() => newName.trim() && create.mutate()}
          disabled={create.isPending || !newName.trim()}
          className="text-sm px-3 py-1.5 rounded-md bg-primary text-primary-foreground disabled:opacity-50"
        >
          Add
        </button>
      </div>

      {(correspondents ?? []).length === 0 ? (
        <p className="text-center py-8 text-sm text-muted-foreground">No correspondents yet.</p>
      ) : (
        <div className="rounded-lg border border-border divide-y divide-border">
          {(correspondents ?? []).map((correspondent) => (
            <CorrespondentRow
              key={correspondent.id}
              correspondent={correspondent}
              others={(correspondents ?? []).filter((c) => c.id !== correspondent.id)}
              onSave={(name) => update.mutate({ id: correspondent.id, name })}
              onDelete={() => {
                if (confirm(`Delete correspondent "${correspondent.name}"? This cannot be undone.`)) {
                  remove.mutate(correspondent.id);
                }
              }}
              onMerge={(targetId, targetName) => {
                if (
                  confirm(
                    `Merge "${correspondent.name}" into "${targetName}"? Its documents will be reassigned and "${correspondent.name}" will be deleted. This cannot be undone.`
                  )
                ) {
                  merge.mutate({ sourceId: correspondent.id, targetId });
                }
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface CorrespondentRowProps {
  correspondent: CorrespondentDto;
  others: CorrespondentDto[];
  onSave: (name: string) => void;
  onDelete: () => void;
  onMerge: (targetId: string, targetName: string) => void;
}

function CorrespondentRow({ correspondent, others, onSave, onDelete, onMerge }: CorrespondentRowProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(correspondent.name);
  const [mergeTarget, setMergeTarget] = useState("");

  function save() {
    if (!name.trim()) return;
    onSave(name.trim());
    setEditing(false);
  }

  function cancel() {
    setName(correspondent.name);
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
      <span className="flex-1 text-sm font-medium truncate">{correspondent.name}</span>

      {others.length > 0 && (
        <div className="flex items-center gap-1">
          <select
            value={mergeTarget}
            onChange={(e) => setMergeTarget(e.target.value)}
            aria-label={`Merge ${correspondent.name} into…`}
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
        aria-label={`Edit ${correspondent.name}`}
        className="p-1 text-muted-foreground hover:text-foreground"
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button
        onClick={onDelete}
        aria-label={`Delete ${correspondent.name}`}
        className="p-1 text-muted-foreground hover:text-destructive"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}
