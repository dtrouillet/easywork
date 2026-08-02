"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { cn } from "@/lib/utils";
import { TagManager } from "@/components/settings/tag-manager";
import { CorrespondentManager } from "@/components/settings/correspondent-manager";
import { DocumentTypeManager } from "@/components/settings/document-type-manager";

const TABS = [
  { id: "tags", label: "Tags" },
  { id: "correspondents", label: "Correspondents" },
  { id: "types", label: "Types" },
] as const;

type TabId = (typeof TABS)[number]["id"];

export default function TaxonomyPage() {
  const [tab, setTab] = useState<TabId>("tags");

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <Link
          href="/settings"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground mb-2"
        >
          <ArrowLeft className="h-3.5 w-3.5" /> Settings
        </Link>
        <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold">
          Tags, correspondents & types
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Rename, delete, or merge duplicates into a single entry.
        </p>
      </div>

      <div className="flex gap-1 border-b border-border">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={cn(
              "px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors",
              tab === id
                ? "border-primary text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "tags" && <TagManager />}
      {tab === "correspondents" && <CorrespondentManager />}
      {tab === "types" && <DocumentTypeManager />}
    </div>
  );
}
