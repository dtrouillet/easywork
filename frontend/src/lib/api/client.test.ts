import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, uploadWithProgress } from "./client";

class MockXHR {
  static instances: MockXHR[] = [];
  method = "";
  url = "";
  headers: Record<string, string> = {};
  status = 0;
  responseText = "";
  statusText = "";
  upload: { onprogress: ((e: ProgressEvent) => void) | null } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  sentBody: unknown;
  aborted = false;

  constructor() {
    MockXHR.instances.push(this);
  }
  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(key: string, value: string) {
    this.headers[key] = value;
  }
  send(body: unknown) {
    this.sentBody = body;
  }
  abort() {
    this.aborted = true;
  }
}

function file() {
  return new File(["content"], "invoice.pdf", { type: "application/pdf" });
}

beforeEach(() => {
  MockXHR.instances = [];
  vi.stubGlobal("XMLHttpRequest", MockXHR);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("uploadWithProgress", () => {
  it("sets the Authorization header", () => {
    uploadWithProgress("/api/v1/documents", "tok123", file(), () => {});

    expect(MockXHR.instances[0].headers.Authorization).toBe("Bearer tok123");
  });

  it("invokes onProgress with the computed percentage", () => {
    const onProgress = vi.fn();
    uploadWithProgress("/api/v1/documents", "tok", file(), onProgress);

    MockXHR.instances[0].upload.onprogress?.(
      { lengthComputable: true, loaded: 50, total: 200 } as ProgressEvent
    );

    expect(onProgress).toHaveBeenCalledWith(25);
  });

  it("resolves with the parsed JSON body on a 2xx response", async () => {
    const promise = uploadWithProgress<{ id: string }>("/api/v1/documents", "tok", file(), () => {});
    const xhr = MockXHR.instances[0];
    xhr.status = 201;
    xhr.responseText = JSON.stringify({ id: "abc" });
    xhr.onload?.();

    await expect(promise).resolves.toEqual({ id: "abc" });
  });

  it("rejects with an ApiError carrying the status on a non-2xx response", async () => {
    const promise = uploadWithProgress("/api/v1/documents", "tok", file(), () => {});
    const xhr = MockXHR.instances[0];
    xhr.status = 415;
    xhr.responseText = "Unsupported file type";
    xhr.onload?.();

    await expect(promise).rejects.toBeInstanceOf(ApiError);
    await expect(promise).rejects.toMatchObject({ status: 415 });
  });

  it("aborts the underlying XHR when the AbortSignal fires", () => {
    const controller = new AbortController();
    uploadWithProgress("/api/v1/documents", "tok", file(), () => {}, controller.signal);
    const xhr = MockXHR.instances[0];

    controller.abort();

    expect(xhr.aborted).toBe(true);
  });
});
