import { assertEquals, assertRejects, assertStringIncludes, assertThrows } from "@std/assert";
import { DEFAULT_GEMINI_MODEL, geminiResearcher, parseGeminiResponse, SYSTEM_PROMPT, UpstreamError } from "./gemini.ts";
import type { Excerpt, Retriever } from "./wikipedia.ts";

const excerpts: Excerpt[] = [
  { title: "Sustancias poliméricas (Wikipedia ES)", url: "https://es.wikipedia.org/wiki/A", text: "Unrelated." },
  {
    title: "Gellan gum (Wikipedia EN)",
    url: "https://en.wikipedia.org/wiki/Gellan_gum",
    text: "Gellan gum is produced by the bacterium Sphingomonas elodea.",
  },
];

const answer = (text: string, grounding?: unknown) => ({
  candidates: [{ content: { parts: [{ text }] }, ...(grounding ? { groundingMetadata: grounding } : {}) }],
});

Deno.test("keeps only the excerpts the model says it used as sources", () => {
  const research = parseGeminiResponse(
    answer('{"status":"vegan","reason_en":"Made by bacteria.","reason_es":"Hecha por bacterias.","used_excerpts":[2]}'),
    excerpts,
  );

  assertEquals(research.status, "vegan");
  assertEquals(research.reasonEs, "Hecha por bacterias.");
  assertEquals(research.sources, [{
    title: "Gellan gum (Wikipedia EN)",
    url: "https://en.wikipedia.org/wiki/Gellan_gum",
  }]);
});

Deno.test("parses JSON wrapped in markdown and grounding sources", () => {
  const research = parseGeminiResponse(
    answer('```json\n{"status":"maybe","reason_en":"a","reason_es":"b","used_excerpts":[]}\n```', {
      groundingChunks: [
        { web: { uri: "https://redirect/a", title: "veganfoodandliving.com" } },
        { web: { uri: "https://redirect/b", title: "veganfoodandliving.com" } },
      ],
    }),
  );

  assertEquals(research.status, "maybe");
  assertEquals(research.sources.map((s) => s.title), ["veganfoodandliving.com"]);
});

Deno.test("ignores out-of-range excerpt numbers", () => {
  const research = parseGeminiResponse(
    answer('{"status":"vegan","reason_en":"a","reason_es":"b","used_excerpts":[0, 7, "x"]}'),
    excerpts,
  );
  assertEquals(research.sources, []);
});

Deno.test("rejects answers without JSON or with an unexpected status", () => {
  assertThrows(() => parseGeminiResponse(answer("I think it's vegan")));
  assertThrows(() => parseGeminiResponse(answer('{"status":"yes"}')));
});

function capture() {
  let captured: { url: string; init: RequestInit } | undefined;
  const fetchFn = ((url: string, init: RequestInit) => {
    captured = { url, init };
    return Promise.resolve(
      new Response(JSON.stringify(answer('{"status":"vegan","reason_en":"a","reason_es":"b","used_excerpts":[2]}'))),
    );
  }) as typeof fetch;
  return { fetchFn, get: () => captured! };
}

const retriever: Retriever = { retrieve: () => Promise.resolve(excerpts) };

Deno.test("free mode: Wikipedia excerpts in the prompt, JSON mode, no search tool, key in a header", async () => {
  const { fetchFn, get } = capture();

  const research = await geminiResearcher("test-key", { retriever, fetchFn }).research("goma gelana", "es");

  const { url, init } = get();
  const body = JSON.parse(init.body as string);
  assertStringIncludes(url, `/models/${DEFAULT_GEMINI_MODEL}:generateContent`);
  assertEquals(url.includes("test-key"), false);
  assertEquals((init.headers as Record<string, string>)["x-goog-api-key"], "test-key");
  assertEquals(body.tools, undefined);
  assertEquals(body.generationConfig.responseMimeType, "application/json");
  assertEquals(body.systemInstruction.parts[0].text, SYSTEM_PROMPT);
  assertStringIncludes(body.contents[0].parts[0].text, "Ingredient (Spanish label): goma gelana");
  assertStringIncludes(body.contents[0].parts[0].text, "[2] Gellan gum (Wikipedia EN): Gellan gum is produced");
  assertEquals(research.model, `${DEFAULT_GEMINI_MODEL}+wikipedia`);
  assertEquals(research.sources.length, 1);
});

Deno.test("paid mode: Google Search grounding tool and no JSON mode", async () => {
  const { fetchFn, get } = capture();

  const research = await geminiResearcher("k", { model: "gemini-3.8-flash", googleSearch: true, fetchFn })
    .research("sal", "es");

  const body = JSON.parse(get().init.body as string);
  assertEquals(body.tools, [{ google_search: {} }]);
  assertEquals(body.generationConfig.responseMimeType, undefined);
  assertEquals(research.model, "gemini-3.8-flash+google-search");
});

Deno.test("surfaces HTTP errors with their status so the handler can defer", async () => {
  const failing = (() => Promise.resolve(new Response("quota", { status: 429 }))) as typeof fetch;
  const error = await assertRejects(
    () => geminiResearcher("k", { fetchFn: failing }).research("sal", "es"),
    UpstreamError,
  );
  assertEquals(error.status, 429);
});
