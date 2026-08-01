"use client";

import { useQuery } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { tagsApi } from "@/lib/api/tags";
import { correspondentsApi } from "@/lib/api/correspondents";
import { cn } from "@/lib/utils";

interface TagViewProps {
  activeTagId: string | null;
  onTagChange: (id: string | null) => void;
  activeCorrespondentId: string | null;
  onCorrespondentChange: (id: string | null) => void;
}

export function TagView({
  activeTagId,
  onTagChange,
  activeCorrespondentId,
  onCorrespondentChange,
}: TagViewProps) {
  const { data: session } = useSession();

  const { data: tags } = useQuery({
    queryKey: ["tags"],
    queryFn: () => tagsApi(session!.accessToken).list(),
    enabled: !!session,
  });

  const { data: correspondents } = useQuery({
    queryKey: ["correspondents"],
    queryFn: () => correspondentsApi(session!.accessToken).list(),
    enabled: !!session,
  });

  return (
    <div className="space-y-6">
      <div>
        <p className="px-2 text-xs font-medium uppercase tracking-wide mb-2 text-muted-foreground">
          Tags
        </p>
        <ul className="space-y-0.5">
          {(tags ?? []).map((tag) => (
            <li key={tag.id}>
              <button
                onClick={() => onTagChange(tag.id === activeTagId ? null : tag.id)}
                className={cn(
                  "w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-left transition-colors",
                  activeTagId === tag.id ? "bg-accent font-semibold" : "hover:bg-accent/50"
                )}
              >
                <span
                  className="h-2.5 w-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: tag.color ?? "#999" }}
                />
                <span className="truncate">{tag.name}</span>
              </button>
            </li>
          ))}
          {tags?.length === 0 && (
            <li className="px-2 text-sm text-muted-foreground">No tags yet.</li>
          )}
        </ul>
      </div>

      <div>
        <p className="px-2 text-xs font-medium uppercase tracking-wide mb-2 text-muted-foreground">
          Correspondents
        </p>
        <ul className="space-y-0.5">
          {(correspondents ?? []).map((correspondent) => (
            <li key={correspondent.id}>
              <button
                onClick={() =>
                  onCorrespondentChange(
                    correspondent.id === activeCorrespondentId ? null : correspondent.id
                  )
                }
                className={cn(
                  "w-full px-2 py-1.5 rounded-md text-sm text-left transition-colors truncate",
                  activeCorrespondentId === correspondent.id
                    ? "bg-accent font-semibold"
                    : "hover:bg-accent/50"
                )}
              >
                {correspondent.name}
              </button>
            </li>
          ))}
          {correspondents?.length === 0 && (
            <li className="px-2 text-sm text-muted-foreground">No correspondents yet.</li>
          )}
        </ul>
      </div>
    </div>
  );
}
