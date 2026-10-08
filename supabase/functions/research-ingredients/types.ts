export type Language = "en" | "es";
export type ResearchStatus = "vegan" | "non_vegan" | "maybe" | "unknown";

export interface Source {
  title: string;
  url: string;
}

/** A row of `public.ingredient_knowledge`. */
export interface KnowledgeRow {
  normalized_name: string;
  display_name: string;
  language: Language;
  status: ResearchStatus;
  reason_en: string | null;
  reason_es: string | null;
  sources: Source[];
  model: string;
  state: "active" | "disputed";
}

export interface Research {
  status: ResearchStatus;
  reasonEn: string;
  reasonEs: string;
  sources: Source[];
  model: string;
}

/** Persistence and rate limiting, backed by Postgres in production and by fakes in tests. */
export interface KnowledgeStore {
  find(normalizedNames: string[]): Promise<Map<string, KnowledgeRow>>;
  save(row: KnowledgeRow): Promise<void>;
  /** Reserves one research call for the user; false when the user or global daily budget is exhausted. */
  reserveCall(userId: string): Promise<boolean>;
}

export interface Researcher {
  research(name: string, language: Language): Promise<Research>;
}

export interface ResearchedIngredient {
  name: string;
  normalizedName: string;
  status: ResearchStatus;
  reasonEn: string | null;
  reasonEs: string | null;
  sources: Source[];
  cached: boolean;
}

export interface ResearchResponse {
  results: ResearchedIngredient[];
  /** Not researched now (daily budget reached or the search failed); the client may retry later. */
  deferred: string[];
  /** Reported as wrong by users; not served until reviewed. */
  disputed: string[];
  /** Not accepted as ingredient names (too long, unexpected characters…). */
  rejected: string[];
}
