import type { Language } from "./types.ts";

/** A short reference text the LLM can base its answer on. */
export interface Excerpt {
  title: string;
  url: string;
  text: string;
}

export interface Retriever {
  retrieve(name: string, language: Language): Promise<Excerpt[]>;
}

const USER_AGENT = "VeganScanner/1.0 (https://github.com/BrandonVargas/VeganScanner; ingredient research)";
const RESULTS_PER_LANGUAGE = 2;
const MAX_EXCERPT_CHARS = 900;

/**
 * Free, keyless web context from Wikipedia (MediaWiki Action API): the top search results in the label's language
 * and in the other supported language, with their intro paragraphs. Failures return no excerpts, never errors.
 */
export function wikipediaRetriever(fetchFn: typeof fetch = fetch): Retriever {
  async function get(language: Language, params: Record<string, string>): Promise<unknown> {
    const url = new URL(`https://${language}.wikipedia.org/w/api.php`);
    for (const [key, value] of Object.entries({ ...params, format: "json", formatversion: "2", utf8: "1" })) {
      url.searchParams.set(key, value);
    }
    const response = await fetchFn(url.toString(), {
      headers: { "user-agent": USER_AGENT, "api-user-agent": USER_AGENT },
      signal: AbortSignal.timeout(6_000),
    });
    if (!response.ok) throw new Error(`Wikipedia HTTP ${response.status}`);
    return response.json();
  }

  async function search(name: string, language: Language): Promise<Excerpt[]> {
    // deno-lint-ignore no-explicit-any
    const found: any = await get(language, {
      action: "query",
      list: "search",
      srsearch: name,
      srlimit: String(RESULTS_PER_LANGUAGE),
    });
    const titles: string[] = (found?.query?.search ?? []).map((result: { title: string }) => result.title);
    if (titles.length === 0) return [];

    // deno-lint-ignore no-explicit-any
    const extracts: any = await get(language, {
      action: "query",
      prop: "extracts",
      exintro: "1",
      explaintext: "1",
      redirects: "1",
      titles: titles.join("|"),
    });
    // deno-lint-ignore no-explicit-any
    const pages: any[] = extracts?.query?.pages ?? [];
    return titles.flatMap((title) => {
      const page = pages.find((p) => p?.title === title);
      const text = String(page?.extract ?? "").replace(/\s+/g, " ").trim();
      if (!text) return [];
      return [{
        title: `${title} (Wikipedia ${language.toUpperCase()})`,
        url: `https://${language}.wikipedia.org/wiki/${encodeURIComponent(title.replaceAll(" ", "_"))}`,
        text: text.slice(0, MAX_EXCERPT_CHARS),
      }];
    });
  }

  return {
    async retrieve(name, language) {
      const languages: Language[] = language === "es" ? ["es", "en"] : ["en", "es"];
      const results = await Promise.all(languages.map((lang) => search(name, lang).catch(() => [])));
      return results.flat();
    },
  };
}
