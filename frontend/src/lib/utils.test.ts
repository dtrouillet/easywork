import { describe, expect, it } from "vitest";
import { formatBytes, formatDate } from "./utils";

describe("formatBytes", () => {
  it("returns 0 B for zero bytes", () => {
    expect(formatBytes(0)).toBe("0 B");
  });

  it("formats bytes into the largest fitting unit", () => {
    expect(formatBytes(1536)).toBe("1.5 KB");
  });
});

describe("formatDate", () => {
  it("returns an em dash placeholder for null", () => {
    expect(formatDate(null)).toBe("—");
  });

  it("formats an ISO date as day/month/year", () => {
    expect(formatDate("2026-03-05T12:00:00.000Z")).toBe("05 Mar 2026");
  });
});
