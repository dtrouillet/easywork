"use client";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { SessionProvider, signOut, useSession } from "next-auth/react";
import { useEffect, useState, type ReactNode } from "react";
import { toast, Toaster } from "sonner";
import { ApiError } from "@/lib/api/client";

function handleApiError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      toast.error("Session expirée, reconnexion en cours…");
      signOut({ callbackUrl: "/login" });
      return;
    }
    if (error.status === 403) {
      toast.error("Accès refusé.");
      return;
    }
  }
  toast.error(
    error instanceof Error ? error.message : "Une erreur inattendue est survenue."
  );
}

function SessionWatcher() {
  const { data: session } = useSession();

  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError") {
      toast.error("Votre session a expiré, veuillez vous reconnecter.");
      signOut({ callbackUrl: "/login" });
    }
  }, [session?.error]);

  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({ onError: handleApiError }),
        mutationCache: new MutationCache({ onError: handleApiError }),
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: (failureCount, error) => {
              if (error instanceof ApiError && error.status === 401) return false;
              return failureCount < 1;
            },
          },
        },
      })
  );

  return (
    <SessionProvider>
      <QueryClientProvider client={queryClient}>
        <SessionWatcher />
        {children}
        <Toaster richColors position="top-right" />
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </SessionProvider>
  );
}
