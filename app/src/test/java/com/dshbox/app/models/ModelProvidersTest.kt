package com.dshbox.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProvidersTest {
    private fun custom(url: String = "https://gateway.example/v1", models: List<String> = listOf("vendor/model")) =
        ModelProviderProfile("starbridge-custom", true, url, "openai-completions", models)

    private fun refused(profile: ModelProviderProfile) {
        try {
            ModelProviders.validate(profile)
            throw AssertionError("Unsafe or incomplete profile accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun urlsCannotSmuggleCredentialsOrChangeTheRequestEndpoint() {
        listOf("http://gateway.example/v1", "https://secret@gateway.example/v1",
            "https://gateway.example/v1?key=secret", "https://gateway.example/v1#secret",
            "https://gateway.example/v1/chat/completions").forEach { refused(custom(it)) }
    }

    @Test fun customRouteNeedsAnExplicitModelButCatalogRouteDoesNot() {
        refused(custom(models = emptyList()))
        ModelProviders.validate(ModelProviderProfile("openai", true, "https://api.openai.com/v1", "openai-responses", emptyList()))
    }

    @Test fun modelIdsCannotInjectAnotherYamlEntry() {
        refused(custom(models = listOf("model\n- id: other-plugin")))
        refused(custom(models = listOf("model\u0000suffix")))
    }

    @Test fun removingAllRoutesLeavesAValidEmptyPatch() {
        assertEquals("[]\n", ModelProviders.renderOverlay(emptyList()))
        assertEquals("[]\n", ModelProviders.renderOverlay(listOf(custom().copy(enabled = false))))
    }

    @Test fun quotesInModelIdsRemainInsideOneYamlScalar() {
        val rendered = ModelProviders.renderOverlay(listOf(custom(models = listOf("vendor/model's-name"))))
        assertTrue(rendered.contains("'vendor/model''s-name'"))
        assertFalse(rendered.contains("apiKey:"))
    }

    @Test fun everyPresetHasAUniqueValidCredentialReference() {
        val refs = ModelProviders.presets.map { ModelProviders.keyRef(it.id) }
        assertEquals(refs.size, refs.toSet().size)
        assertTrue(refs.all { it.matches(Regex("[A-Z_][A-Z0-9_]*")) })
    }
}
