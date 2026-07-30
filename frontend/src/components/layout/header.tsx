"use client";

import { signOut } from "next-auth/react";
import { Upload, LogOut, User } from "lucide-react";
import { useUiStore } from "@/store/ui-store";

interface HeaderProps {
  userName?: string | null;
}

export function Header({ userName }: HeaderProps) {
  const setUploadDialogOpen = useUiStore((s) => s.setUploadDialogOpen);

  return (
    <header className="h-14 flex items-center justify-between px-4 border-b border-border bg-background shrink-0">
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">Documents</span>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={() => setUploadDialogOpen(true)}
          className="flex items-center gap-2 rounded-md bg-primary text-primary-foreground px-3 py-1.5 text-sm font-medium hover:opacity-90 transition-opacity"
        >
          <Upload className="h-4 w-4" />
          Upload
        </button>

        <div className="flex items-center gap-1 text-sm text-muted-foreground">
          <User className="h-4 w-4" />
          <span className="hidden sm:inline">{userName ?? "User"}</span>
        </div>

        <button
          onClick={() => signOut({ callbackUrl: "/login" })}
          className="rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          title="Sign out"
        >
          <LogOut className="h-4 w-4" />
        </button>
      </div>
    </header>
  );
}
