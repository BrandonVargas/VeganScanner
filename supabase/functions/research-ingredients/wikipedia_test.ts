import { assertEquals } from "@std/assert";
import { wikipediaRetriever } from "./wikipedia.ts";

function fakeWikipedia(pages: Record<string, Record<string, string>>, failing: string[] = []) {
  const requests: string[] = [];
  const fetchFn = ((input: string) => {
    const url = new URL(input);
    const language = url.hostname.split(".")[0];
    requests.push(`${language}:${url.searchParams.get("list") ?? url.searchParams.get("prop")}`);
    if (failing.includes(language)) return Promise.resolve(new Response("down", { status: 503 }));
    const articles = pages[language] ?? {};
    if (url.searchParams.get("list") === "search") {
      const search = Object.keys(articles).map((title) => ({ title }));
      return Promise.resolve(Response.json({ query: { search } }));
    }
    const titles = url.searchParams.get("titles")!.split("|");
    return Promise.resolve(
      Response.json({ query: { pages: titles.map((title) => ({ title, extract: articles[title] })) } }),
    );
  }) as typeof fetch;
  return { fetchFn, requests };
}

Deno.test("searches the label language first, then the other one, and returns intro excerpts", async () => {
  const { fetchFn, requests } = fakeWikipedia({
    es: { "Goma gellan": "La goma gellan se obtiene por fermentación bacteriana." },
    en: { "Gellan gum": "Gellan gum is a water-soluble polysaccharide." },
  });

  const excerpts = await wikipediaRetriever(fetchFn).retrieve("goma gelana", "es");

  assertEquals(excerpts.map((e) => e.title), ["Goma gellan (Wikipedia ES)", "Gellan gum (Wikipedia EN)"]);
  assertEquals(excerpts[0].url, "https://es.wikipedia.org/wiki/Goma_gellan");
  assertEquals(requests.sort(), ["en:extracts", "en:search", "es:extracts", "es:search"]);
});

Deno.test("a failing or empty language never breaks research", async () => {
  const { fetchFn } = fakeWikipedia({ en: { "Gellan gum": "Polysaccharide." } }, ["es"]);

  const excerpts = await wikipediaRetriever(fetchFn).retrieve("goma gelana", "es");

  assertEquals(excerpts.map((e) => e.title), ["Gellan gum (Wikipedia EN)"]);
});

Deno.test("long extracts are trimmed", async () => {
  const { fetchFn } = fakeWikipedia({ en: { Salt: "x ".repeat(2_000) } });

  const [excerpt] = await wikipediaRetriever(fetchFn).retrieve("salt", "en");

  assertEquals(excerpt.text.length <= 900, true);
});
