import { assertEquals, assertRejects, assertStringIncludes, assertThrows } from "@std/assert";
import {
  DEFAULT_GEMINI_MODEL,
  geminiResearcher,
  parseGeminiResponse,
  RESERVED_SEARCH_QUERIES,
  SEARCH_HINT,
  searchQueryCount,
  SYSTEM_PROMPT,
  UpstreamError,
} from "./gemini.ts";
import type { SearchQuota } from "./types.ts";
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

Deno.test("repairs a value the model left out after searching", () => {
  const research = parseGeminiResponse(answer(
    '```json\n{\n  "status": "non_vegan",\n  "reason_en": "From egg whites.",\n  "reason_es": "De clara de huevo.",\n' +
      '  "used_excerpts":\n}\n```',
  ));
  assertEquals(research.status, "non_vegan");
  assertEquals(research.reasonEn, "From egg whites.");
  assertEquals(research.sources, []);
  assertEquals(parseGeminiResponse(answer('{"status":"vegan","reason_en":"a","reason_es":"b",}')).status, "vegan");
});

Deno.test("rejects answers without JSON or with an unexpected status", () => {
  assertThrows(() => parseGeminiResponse(answer("I think it's vegan")));
  assertThrows(() => parseGeminiResponse(answer('{"status":"yes"}')));
  assertThrows(() => parseGeminiResponse(answer('{"status": vegan}')));
});

function capture(grounding?: unknown) {
  let captured: { url: string; init: RequestInit } | undefined;
  const fetchFn = ((url: string, init: RequestInit) => {
    captured = { url, init };
    return Promise.resolve(
      new Response(
        JSON.stringify(answer('{"status":"vegan","reason_en":"a","reason_es":"b","used_excerpts":[2]}', grounding)),
      ),
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

function fakeQuota(available: number) {
  const settled: [number, number][] = [];
  let remaining = available;
  const quota: SearchQuota = {
    reserve: (queries) => Promise.resolve(remaining >= queries && (remaining -= queries, true)),
    settle: (reserved, used) => {
      settled.push([reserved, used]);
      return Promise.resolve();
    },
  };
  return { quota, settled };
}

const grounding = {
  webSearchQueries: ["sorbitan monostearate source", ""],
  groundingChunks: [{ web: { uri: "https://example.org/e491", title: "example.org" } }],
};

Deno.test("with search: Wikipedia excerpts plus the search tool, settling the queries Gemini ran", async () => {
  const { fetchFn, get } = capture(grounding);
  const { quota, settled } = fakeQuota(10);

  const research = await geminiResearcher("k", { googleSearch: true, searchQuota: quota, retriever, fetchFn })
    .research("goma gelana", "es");

  const body = JSON.parse(get().init.body as string);
  assertEquals(body.tools, [{ google_search: {} }]);
  assertEquals(body.generationConfig.responseMimeType, undefined);
  assertEquals(body.systemInstruction.parts[0].text, SYSTEM_PROMPT + SEARCH_HINT);
  assertStringIncludes(body.contents[0].parts[0].text, "[2] Gellan gum (Wikipedia EN)");
  assertEquals(settled, [[RESERVED_SEARCH_QUERIES, 1]]);
  assertEquals(research.model, `${DEFAULT_GEMINI_MODEL}+wikipedia+google-search`);
  assertEquals(research.sources, [
    { title: "Gellan gum (Wikipedia EN)", url: "https://en.wikipedia.org/wiki/Gellan_gum" },
    { title: "example.org", url: "https://example.org/e491" },
  ]);
});

Deno.test("when Gemini answers without searching, the reservation is released and the label says so", async () => {
  const { fetchFn } = capture();
  const { quota, settled } = fakeQuota(10);

  const research = await geminiResearcher("k", { googleSearch: true, searchQuota: quota, retriever, fetchFn })
    .research("goma gelana", "es");

  assertEquals(settled, [[RESERVED_SEARCH_QUERIES, 0]]);
  assertEquals(research.model, `${DEFAULT_GEMINI_MODEL}+wikipedia`);
});

Deno.test("once the search quota is used up, requests go without the search tool", async () => {
  const { fetchFn, get } = capture();
  const { quota, settled } = fakeQuota(RESERVED_SEARCH_QUERIES - 1);

  const research = await geminiResearcher("k", { googleSearch: true, searchQuota: quota, retriever, fetchFn })
    .research("goma gelana", "es");

  const body = JSON.parse(get().init.body as string);
  assertEquals(body.tools, undefined);
  assertEquals(body.systemInstruction.parts[0].text, SYSTEM_PROMPT);
  assertEquals(settled, []);
  assertEquals(research.model, `${DEFAULT_GEMINI_MODEL}+wikipedia`);
});

Deno.test("releases the reservation when Gemini rejects a grounded request", async () => {
  const failing = (() => Promise.resolve(new Response("bad", { status: 400 }))) as typeof fetch;
  const { quota, settled } = fakeQuota(10);

  await assertRejects(
    () => geminiResearcher("k", { googleSearch: true, searchQuota: quota, fetchFn: failing }).research("sal", "es"),
    UpstreamError,
  );
  assertEquals(settled, [[RESERVED_SEARCH_QUERIES, 0]]);
});

Deno.test("counts only non-empty search queries", () => {
  assertEquals(searchQueryCount(answer("{}", grounding)), 1);
  assertEquals(searchQueryCount(answer("{}")), 0);
});

Deno.test("surfaces HTTP errors with their status so the handler can defer", async () => {
  const failing = (() => Promise.resolve(new Response("quota", { status: 429 }))) as typeof fetch;
  const error = await assertRejects(
    () => geminiResearcher("k", { fetchFn: failing }).research("sal", "es"),
    UpstreamError,
  );
  assertEquals(error.status, 429);
});
