import { Bell, Palette, Shield, User } from "lucide-react";

const sections = [
  {
    icon: User,
    title: "Profile",
    description: "Name, email, and language preferences.",
  },
  {
    icon: Bell,
    title: "Notifications",
    description: "Choose when to be notified about processed documents.",
  },
  {
    icon: Shield,
    title: "Security & access",
    description: "Manage sign-in methods and connected sessions.",
  },
  {
    icon: Palette,
    title: "Appearance",
    description: "Theme and display density.",
  },
];

export default function SettingsPage() {
  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="font-[family-name:var(--font-fraunces)] text-2xl font-semibold">
          Settings
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          This page is a preview — these settings aren&apos;t available yet.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {sections.map(({ icon: Icon, title, description }) => (
          <div
            key={title}
            className="rounded-lg border border-border p-4 opacity-60"
          >
            <div className="flex items-center gap-2 mb-1.5">
              <Icon className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">{title}</span>
              <span className="ml-auto text-xs text-muted-foreground rounded-full border border-border px-2 py-0.5">
                Coming soon
              </span>
            </div>
            <p className="text-sm text-muted-foreground">{description}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
