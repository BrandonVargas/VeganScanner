plugins {
    alias(libs.plugins.veganscanner.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.common)
            api(project.dependencies.platform(libs.supabase.bom))
            api(libs.supabase.auth)
            api(libs.supabase.functions)
            api(libs.supabase.postgrest)
        }
    }
}
