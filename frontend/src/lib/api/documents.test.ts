import { describe, expect, it, vi, afterEach } from "vitest";
import { documentsApi } from "./documents";

describe("documentsApi.retry", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("POSTs to the retry endpoint for the given document id", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      text: () => Promise.resolve(""),
    });
    vi.stubGlobal("fetch", fetchMock);

    await documentsApi("test-token").retry("doc-1");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain("/api/v1/documents/doc-1/retry");
    expect(init).toMatchObject({ method: "POST" });
  });
});

describe("documentsApi.reclassify", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("POSTs to the reclassify endpoint for the given document id", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: () => Promise.resolve("{}"),
      json: () => Promise.resolve({}),
    });
    vi.stubGlobal("fetch", fetchMock);

    await documentsApi("test-token").reclassify("doc-1");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain("/api/v1/documents/doc-1/reclassify");
    expect(init).toMatchObject({ method: "POST" });
  });
});
