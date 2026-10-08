import type { SupabaseClient } from "@supabase/supabase-js";
import type { KnowledgeRow, KnowledgeStore } from "./types.ts";

export const PER_USER_DAILY_CALLS = 25;
/** Below the Gemini free tier's 500 grounded requests per day. */
export const GLOBAL_DAILY_CALLS = 450;

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
