"use client";

import { FolderTree, Tag as TagIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { DocumentDto } from "@/lib/api/types";
import { TagView } from "./tag-view";
import { TreeView } from "./tree-view";

export type BrowseView = "tags" | "tree";

interface BrowsePanelProps {
  view: BrowseView;
  onViewChange: (view: BrowseView) => void;
  activeTagId: string | null;
  onTagChange: (id: string | null) => void;
  activeCorrespondentId: string | null;
  onCorrespondentChange: (id: string | null) => void;
  activePath: string[];
  onPathChange: (path: string[]) => void;
  docs: DocumentDto[];
}

export function BrowsePanel({
  view,
  onViewChange,
  activeTagId,
  onTagChange,
  activeCorrespondentId,
  onCorrespondentChange,
  activePath,
  onPathChange,
  docs,
}: BrowsePanelProps) {
  return (
    <aside className="w-64 shrink-0 space-y-4">
      <div className="flex rounded-md p-0.5 text-sm bg-muted border border-border">
        <button
          onClick={() => onViewChange("tags")}
          className={cn(
            "flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-[5px] transition-colors",
            view === "tags" ? "bg-background font-semibold shadow-sm" : "text-muted-foreground"
          )}
        >
          <TagIcon className="h-3.5 w-3.5" />
          Tags
        </button>
        <button
          onClick={() => onViewChange("tree")}
          className={cn(
            "flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-[5px] transition-colors",
            view === "tree" ? "bg-background font-semibold shadow-sm" : "text-muted-foreground"
          )}
        >
          <FolderTree className="h-3.5 w-3.5" />
          Folders
        </button>
      </div>

      {view === "tags" ? (
        <TagView
          activeTagId={activeTagId}
          onTagChange={onTagChange}
          activeCorrespondentId={activeCorrespondentId}
          onCorrespondentChange={onCorrespondentChange}
        />
      ) : (
        <TreeView docs={docs} activePath={activePath} onPathChange={onPathChange} />
      )}
    </aside>
  );
}
