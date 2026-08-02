"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FileText, PanelLeftClose, PanelLeftOpen, Settings } from "lucide-react";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/store/ui-store";

const nav = [
  { href: "/documents", label: "Documents", icon: FileText },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  const sidebarOpen = useUiStore((s) => s.sidebarOpen);
  const toggleSidebar = useUiStore((s) => s.toggleSidebar);

  return (
    <aside
      className={cn(
        "shrink-0 flex flex-col border-r border-border bg-background transition-[width] duration-150",
        sidebarOpen ? "w-56" : "w-14"
      )}
    >
      <div
        className={cn(
          "h-14 flex items-center border-b border-border",
          sidebarOpen ? "justify-between px-4" : "justify-center px-2"
        )}
      >
        {sidebarOpen && (
          <span className="font-[family-name:var(--font-fraunces)] text-lg font-semibold truncate">
            easywork
          </span>
        )}
        <button
          onClick={toggleSidebar}
          className="rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors shrink-0"
          title={sidebarOpen ? "Collapse sidebar" : "Expand sidebar"}
        >
          {sidebarOpen ? (
            <PanelLeftClose className="h-4 w-4" />
          ) : (
            <PanelLeftOpen className="h-4 w-4" />
          )}
        </button>
      </div>
      <nav className="flex-1 p-2 space-y-0.5">
        {nav.map(({ href, label, icon: Icon }) => {
          const active = pathname === href;
          return (
            <Link
              key={href}
              href={href}
              title={sidebarOpen ? undefined : label}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                !sidebarOpen && "justify-center px-2",
                active
                  ? "bg-accent text-accent-foreground font-medium"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {sidebarOpen && label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
