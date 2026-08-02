"use client";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { SessionProvider, useSession } from "next-auth/react";
import { useEffect, useState, type ReactNode } from "react";
import { toast, Toaster } from "sonner";
import { ApiError } from "@/lib/api/client";
import { fullSignOut } from "@/lib/sign-out";

function handleApiError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      toast.error("Session expired, signing out…");
      fullSignOut();
      return;
    }
    if (error.status === 403) {
      toast.error("Access denied.");
      return;
    }
  }
  toast.error(
    error instanceof Error ? error.message : "An unexpected error occurred."
  );
}

function SessionWatcher() {
  const { data: session } = useSession();

  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError") {
      toast.error("Your session has expired, please sign in again.");
      fullSignOut();
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
