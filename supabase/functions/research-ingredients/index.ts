// Researches unknown food ingredients on the web (Gemini with capped Google Search grounding, or Wikipedia excerpts)
// and caches the answers for every user in public.ingredient_knowledge. See docs/adr/0007-cloud-ingredient-research.md.
import { createClient } from "@supabase/supabase-js";
import { geminiResearcher } from "./gemini.ts";
import { researchIngredients } from "./handler.ts";
import { supabaseSearchQuota, supabaseStore } from "./store.ts";
import { wikipediaRetriever } from "./wikipedia.ts";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: request.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false },
  });
  // Any signed-in user, including the app's silent anonymous sessions; needed for per-user rate limits.
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json({ error: "Sign-in required" }, 401);

  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (!geminiKey) return json({ error: "Research is not configured" }, 503);

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const body = await request.json().catch(() => null);
  const result = await researchIngredients(body, user.id, {
    store: supabaseStore(admin),
    researcher: geminiResearcher(geminiKey, {
      model: Deno.env.get("GEMINI_MODEL") || undefined,
      googleSearch: Deno.env.get("GEMINI_GOOGLE_SEARCH") === "true",
      searchQuota: supabaseSearchQuota(admin),
      retriever: wikipediaRetriever(),
    }),
  });
  return json(result.body, result.status);
});
