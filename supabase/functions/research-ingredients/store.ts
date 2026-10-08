import type { SupabaseClient } from "@supabase/supabase-js";
import type { KnowledgeRow, KnowledgeStore, SearchQuota } from "./types.ts";

export const PER_USER_DAILY_CALLS = 25;
/** Gemini requests per day for everyone, whatever the web source. */
export const GLOBAL_DAILY_CALLS = 450;
/**
 * Google Search grounding queries per calendar month: below the 5,000 free queries of Gemini 3 (shared by every
 * Gemini 3 model on the project), after which each query is billed. Leaves headroom for reservation overshoot and
 * UTC-vs-Pacific month boundaries.
 */
export const MONTHLY_SEARCH_QUERIES = 4_500;
/** Spreads the monthly allowance so one busy day can't use it up. */
export const DAILY_SEARCH_QUERIES = 150;

/** Postgres-backed store; [admin] must use the service role (tables have no client write policies). */
export function supabaseStore(admin: SupabaseClient): KnowledgeStore {
  return {
    async find(normalizedNames) {
      if (normalizedNames.length === 0) return new Map();
      const { data, error } = await admin.from("ingredient_knowledge").select("*").in(
        "normalized_name",
        normalizedNames,
      );
      if (error) throw error;
      return new Map((data as KnowledgeRow[]).map((row) => [row.normalized_name, row]));
    },
    async save(row) {
      const { error } = await admin.from("ingredient_knowledge").upsert(row, {
        onConflict: "normalized_name",
        ignoreDuplicates: true,
      });
      if (error) throw error;
    },
    async reserveCall(userId) {
      const { data, error } = await admin.rpc("reserve_research_call", {
        p_user: userId,
        p_user_limit: PER_USER_DAILY_CALLS,
        p_global_limit: GLOBAL_DAILY_CALLS,
      });
      if (error) throw error;
      return data === true;
    },
  };
}

/** Postgres-backed Google Search quota (see migration 20261009000000_search_quota.sql). */
export function supabaseSearchQuota(admin: SupabaseClient): SearchQuota {
  return {
    async reserve(queries) {
      const { data, error } = await admin.rpc("reserve_search_queries", {
        p_reserve: queries,
        p_daily_limit: DAILY_SEARCH_QUERIES,
        p_monthly_limit: MONTHLY_SEARCH_QUERIES,
      });
      if (error) throw error;
      return data === true;
    },
    async settle(reserved, used) {
      if (reserved === used) return;
      const { error } = await admin.rpc("settle_search_queries", { p_reserved: reserved, p_used: used });
      // Logged only: the answer is already paid for, and a missed settlement only over-counts.
      if (error) console.error("Could not settle search queries:", error);
    },
  };
}
