declare global {
  interface Window {
    __ENV__?: { API_URL?: string };
  }
}

/**
 * Resolved per-call rather than once at module load: the browser reads
 * window.__ENV__ (populated by /env.js, written by the Docker image's
 * entrypoint from a container env var at startup — see frontend/Dockerfile)
 * so one built image works across environments, since NEXT_PUBLIC_* vars are
 * otherwise baked in at build time. Falls back to NEXT_PUBLIC_API_URL for any
 * server-side execution (SSR/route handlers, no window) and local dev, where
 * /env.js doesn't exist.
 */
function apiBase(): string {
  if (typeof window !== "undefined" && window.__ENV__?.API_URL) {
    return window.__ENV__.API_URL;
  }
  return process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
}

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

  const res = await fetch(`${apiBase()}${path}`, {
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
    patch: <T>(path: string, body?: unknown) =>
      apiFetch<T>(path, token, {
        method: "PATCH",
        body: body !== undefined ? JSON.stringify(body) : undefined,
      }),
    delete: <T>(path: string) =>
      apiFetch<T>(path, token, { method: "DELETE" }),
    upload: <T>(path: string, file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiFetch<T>(path, token, { method: "POST", body: form });
    },
    downloadBlob: async (path: string): Promise<Blob> => {
      const res = await fetch(`${apiBase()}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        const text = await res.text().catch(() => res.statusText);
        throw new ApiError(res.status, `API ${res.status}: ${text}`);
      }
      return res.blob();
    },
  };
}

/**
 * Plain `fetch` (used by apiClient above) cannot report upload progress, so file
 * uploads that need a progress callback go through XMLHttpRequest instead.
 */
export function uploadWithProgress<T>(
  path: string,
  token: string,
  file: File,
  onProgress: (percent: number) => void,
  signal?: AbortSignal
): Promise<T> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${apiBase()}${path}`);
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.status === 204 ? (undefined as unknown as T) : (JSON.parse(xhr.responseText) as T));
      } else {
        reject(new ApiError(xhr.status, `API ${xhr.status}: ${xhr.responseText || xhr.statusText}`));
      }
    };
    xhr.onerror = () => reject(new ApiError(0, "Network error during upload"));

    if (signal) {
      signal.addEventListener("abort", () => xhr.abort());
    }

    xhr.send(form);
  });
}
