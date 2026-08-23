import { describe, expect, it } from "vitest";
import { normalizeMcpTools } from "./mcpTools";

describe("normalizeMcpTools", () => {
  it("returns empty for nullish", () => {
    expect(normalizeMcpTools(undefined)).toEqual([]);
    expect(normalizeMcpTools(null)).toEqual([]);
  });

  it("keeps valid tool rows", () => {
    expect(
      normalizeMcpTools([
        { name: "maps_geo", description: "geo" },
        { name: "" },
        null as any,
      ]),
    ).toEqual([{ name: "maps_geo", description: "geo" }]);
  });
});
