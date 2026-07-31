const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function apiFetch<T>(
  path: string,
  token: string,
  init?: RequestInit
): Promise<T> {
  const isFormData = init?.body instanceof FormData;

  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    ...(init?.headers as Record<string, string>),
  };
  if (!isFormData) {
    headers["Content-Type"] = "application/json";
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new ApiError(res.status, `API ${res.status}: ${text}`);
  }

  if (res.status === 204) return undefined as unknown as T;
  return res.json() as Promise<T>;
}

export function apiClient(token: string) {
  return {
    get: <T>(path: string) => apiFetch<T>(path, token),
    post: <T>(path: string, body?: unknown) =>
      apiFetch<T>(path, token, {
        method: "POST",
        body: body !== undefined ? JSON.stringify(body) : undefined,
      }),
    delete: <T>(path: string) =>
      apiFetch<T>(path, token, { method: "DELETE" }),
    upload: <T>(path: string, file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiFetch<T>(path, token, { method: "POST", body: form });
    },
  };
}
