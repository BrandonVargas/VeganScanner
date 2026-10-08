import { assertEquals, assertRejects, assertThrows } from "@std/assert";
import { geminiResearcher, parseGeminiResponse, SYSTEM_PROMPT } from "./gemini.ts";

const groundedAnswer = {
  candidates: [{
    content: {
      parts: [{
        text:
          '```json\n{"status": "maybe", "reason_en": "Made from stearic acid, which can come from animal fat or plants.", ' +
          '"reason_es": "Se elabora con ácido esteárico, que puede venir de grasa animal o de plantas."}\n```',
      }],
    },
    groundingMetadata: {
      groundingChunks: [
        {
          web: {
            uri: "https://vertexaisearch.cloud.google.com/grounding-api-redirect/a",
            title: "veganfoodandliving.com",
          },
        },
        { web: { uri: "https://vertexaisearch.cloud.google.com/grounding-api-redirect/b", title: "en.wikipedia.org" } },
        { web: { uri: "https://vertexaisearch.cloud.google.com/grounding-api-redirect/c", title: "en.wikipedia.org" } },
      ],
    },
  }],
};

Deno.test("parses a grounded answer, including JSON wrapped in markdown", () => {
  const research = parseGeminiResponse(groundedAnswer);

  assertEquals(research.status, "maybe");
  assertEquals(research.reasonEs.startsWith("Se elabora"), true);
  assertEquals(research.sources.map((s) => s.title), ["veganfoodandliving.com", "en.wikipedia.org"]);
});

Deno.test("rejects answers without JSON or with an unexpected status", () => {
  assertThrows(() => parseGeminiResponse({ candidates: [{ content: { parts: [{ text: "I think it's vegan" }] } }] }));
  assertThrows(() => parseGeminiResponse({ candidates: [{ content: { parts: [{ text: '{"status":"yes"}' }] } }] }));
});

Deno.test("sends one grounded request with the key in a header and the name as data", async () => {
  let captured: { url: string; init: RequestInit } | undefined;
  const fakeFetch = ((url: string, init: RequestInit) => {
    captured = { url, init };
    return Promise.resolve(new Response(JSON.stringify(groundedAnswer)));
  }) as typeof fetch;

  await geminiResearcher("test-key", fakeFetch).research("monoestearato de sorbitán", "es");

  const body = JSON.parse(captured!.init.body as string);
  assertEquals((captured!.init.headers as Record<string, string>)["x-goog-api-key"], "test-key");
  assertEquals(captured!.url.includes("test-key"), false);
  assertEquals(body.tools, [{ google_search: {} }]);
  assertEquals(body.systemInstruction.parts[0].text, SYSTEM_PROMPT);
  assertEquals(body.contents[0].parts[0].text, "Ingredient (Spanish label): monoestearato de sorbitán");
});

Deno.test("surfaces HTTP errors so the handler can defer", async () => {
  const failing = (() => Promise.resolve(new Response("quota", { status: 429 }))) as typeof fetch;
  await assertRejects(() => geminiResearcher("k", failing).research("sal", "es"));
});
