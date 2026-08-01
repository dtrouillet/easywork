import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";

export function renderWithProviders(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    ),
  };
}

export function mockSession(overrides?: Partial<{ accessToken: string }>) {
  return {
    accessToken: "test-access-token",
    user: { name: "Test User", email: "test@example.com" },
    expires: "2099-01-01T00:00:00.000Z",
    ...overrides,
  };
}
