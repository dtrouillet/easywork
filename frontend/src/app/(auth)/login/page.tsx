import { signIn } from "@/lib/auth";
import { FileText } from "lucide-react";

export default function LoginPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-sm space-y-8 px-4">
        <div className="text-center space-y-2">
          <div className="flex justify-center">
            <div className="p-3 rounded-2xl bg-primary text-primary-foreground">
              <FileText className="h-8 w-8" />
            </div>
          </div>
          <h1 className="font-[family-name:var(--font-fraunces)] text-3xl font-semibold tracking-tight">
            easywork
          </h1>
          <p className="text-muted-foreground text-sm">
            Sign in to your document workspace
          </p>
        </div>

        <form
          action={async () => {
            "use server";
            await signIn("keycloak", { redirectTo: "/documents" });
          }}
        >
          <button
            type="submit"
            className="w-full flex justify-center py-2.5 px-4 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition-opacity"
          >
            Sign in with SSO
          </button>
        </form>
      </div>
    </div>
  );
}
