import { describe, expect, it } from "vitest";
import {
  buildDefaultBody,
  fieldsToSchema,
  schemaToFields,
  emptyField,
  inferFieldsFromJson,
  inferSchemaFromJson,
} from "./bodySchema";

describe("bodySchema", () => {
  it("roundtrips nested object/array/map", () => {
    const fields = [
      emptyField("string", "name"),
      {
        ...emptyField("object", "profile"),
        required: true,
        children: [
          { ...emptyField("integer", "age"), defaultValue: "18", required: true },
        ],
      },
      {
        ...emptyField("array", "tags"),
        items: { ...emptyField("string"), defaultValue: "a" },
      },
      {
        ...emptyField("map", "attrs"),
        mapValue: emptyField("boolean"),
        defaultValue: '{"x":true}',
      },
    ];
    fields[0].defaultValue = "alice";

    const schema = fieldsToSchema(fields);
    expect(schema?.type).toBe("object");
    expect((schema?.properties as any).profile.type).toBe("object");
    expect((schema?.properties as any).tags.type).toBe("array");
    expect((schema?.properties as any).attrs["x-field-kind"]).toBe("map");

    const back = schemaToFields(schema);
    expect(back.map((f) => f.name)).toEqual(["name", "profile", "tags", "attrs"]);
    expect(back.find((f) => f.name === "attrs")?.kind).toBe("map");

    const body = buildDefaultBody(fields);
    expect(body.name).toBe("alice");
    expect(body.profile).toEqual({ age: 18 });
    expect(body.tags).toEqual(["a"]);
    expect(body.attrs).toEqual({ x: true });
  });

  it("infers response fields from JSON object with empty descriptions", () => {
    const fields = inferFieldsFromJson({
      code: 0,
      data: { id: 1, name: "n" },
      ok: true,
    });
    expect(fields.map((f) => f.name)).toEqual(["code", "data", "ok"]);
    expect(fields.every((f) => f.description === "")).toBe(true);
    const data = fields.find((f) => f.name === "data");
    expect(data?.kind).toBe("object");
    expect(data?.children?.map((c) => c.name)).toEqual(["id", "name"]);

    const schema = inferSchemaFromJson({ a: "x", b: [1, 2] });
    expect(schema?.type).toBe("object");
    expect((schema?.properties as any).a.type).toBe("string");
    expect((schema?.properties as any).b.type).toBe("array");
  });
});
