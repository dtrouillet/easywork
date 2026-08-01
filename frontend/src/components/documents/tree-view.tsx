"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, Folder, FolderOpen } from "lucide-react";
import { cn } from "@/lib/utils";
import { buildDocumentTree, type TreeNode } from "@/lib/document-tree";
import type { DocumentDto } from "@/lib/api/types";

interface TreeViewProps {
  docs: DocumentDto[];
  activePath: string[];
  onPathChange: (path: string[]) => void;
}

export function TreeView({ docs, activePath, onPathChange }: TreeViewProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const tree = buildDocumentTree(docs);

  function toggle(key: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  return (
    <div>
      <div className="flex items-center justify-between px-2 mb-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Folders
        </p>
        {activePath.length > 0 && (
          <button
            onClick={() => onPathChange([])}
            className="text-xs text-primary hover:underline"
          >
            Root
          </button>
        )}
      </div>
      <ul className="space-y-0.5">
        {tree.map((node) => (
          <TreeNodeRow
            key={node.key}
            node={node}
            depth={0}
            path={[node.label]}
            expanded={expanded}
            toggle={toggle}
            activePath={activePath}
            onSelect={onPathChange}
          />
        ))}
        {tree.length === 0 && (
          <li className="px-2 text-sm text-muted-foreground">No documents yet.</li>
        )}
      </ul>
    </div>
  );
}

interface TreeNodeRowProps {
  node: TreeNode;
  depth: number;
  path: string[];
  expanded: Set<string>;
  toggle: (key: string) => void;
  activePath: string[];
  onSelect: (path: string[]) => void;
}

function TreeNodeRow({ node, depth, path, expanded, toggle, activePath, onSelect }: TreeNodeRowProps) {
  const isOpen = expanded.has(node.key);
  const isLeaf = node.children.length === 0;
  const isActive = activePath.join("/") === path.join("/");

  if (isLeaf) {
    return (
      <li>
        <button
          onClick={() => onSelect(path)}
          className={cn(
            "w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-left transition-colors",
            isActive ? "bg-accent font-semibold" : "hover:bg-accent/50"
          )}
          style={{ paddingLeft: `${8 + depth * 14}px` }}
        >
          <span className="inline-block w-1.5 h-1.5 rounded-full shrink-0 bg-muted-foreground/40" />
          <span className="truncate flex-1">{node.label}</span>
          <span className="text-xs shrink-0 text-muted-foreground">{node.count}</span>
        </button>
      </li>
    );
  }

  return (
    <li>
      <button
        onClick={() => {
          toggle(node.key);
          onSelect(path);
        }}
        className={cn(
          "w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-left transition-colors",
          isActive ? "bg-accent font-semibold" : "hover:bg-accent/50"
        )}
        style={{ paddingLeft: `${8 + depth * 14}px` }}
      >
        {isOpen ? (
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        )}
        {isOpen ? (
          <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" />
        ) : (
          <Folder className="h-4 w-4 shrink-0 text-muted-foreground" />
        )}
        <span className="truncate flex-1">{node.label}</span>
        <span className="text-xs shrink-0 text-muted-foreground">{node.count}</span>
      </button>
      {isOpen && (
        <ul>
          {node.children.map((child) => (
            <TreeNodeRow
              key={child.key}
              node={child}
              depth={depth + 1}
              path={[...path, child.label]}
              expanded={expanded}
              toggle={toggle}
              activePath={activePath}
              onSelect={onSelect}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
