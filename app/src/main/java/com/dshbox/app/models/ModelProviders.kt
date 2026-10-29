package com.dshbox.app.models

import java.net.URI

data class ModelProviderPreset(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val description: String,
    val catalog: Boolean = true,
)

data class ModelProviderProfile(
    val id: String,
    val enabled: Boolean,
    val baseUrl: String,
    val protocol: String,
    val models: List<String>,
)

object ModelProviders {
    const val OVERLAY_RELATIVE_PATH = ".dsh/starbridge/model-providers.yml"
    const val OVERLAY_GUEST_PATH = "/root/projects/$OVERLAY_RELATIVE_PATH"
    val protocols = listOf("openai-responses", "anthropic-messages", "openai-completions")
    val presets = listOf(
        ModelProviderPreset("openai", "OpenAI", "openai-responses", "https://api.openai.com/v1", "GPT · Responses API"),
        ModelProviderPreset("anthropic", "Claude", "anthropic-messages", "https://api.anthropic.com", "Claude · 原生 Messages API"),
        ModelProviderPreset("moonshotai-cn", "Kimi", "openai-completions", "https://api.moonshot.cn/v1", "月之暗面 · 中国区 API"),
        ModelProviderPreset("starbridge-glm", "GLM / 智谱", "openai-completions", "https://open.bigmodel.cn/api/paas/v4", "智谱开放平台 · 普通 API，非 Coding 套餐", false),
        ModelProviderPreset("starbridge-deepseek", "DeepSeek", "openai-completions", "https://api.deepseek.com", "保留原生内核，另加 API 配置", false),
        ModelProviderPreset("starbridge-qwen", "通义千问", "openai-completions", "https://dashscope.aliyuncs.com/compatible-mode/v1", "阿里云百炼 · 地址可按地域和业务空间修改", false),
        ModelProviderPreset("starbridge-gemini", "Gemini", "openai-completions", "https://generativelanguage.googleapis.com/v1beta/openai", "Google AI · OpenAI 兼容接口", false),
        ModelProviderPreset("xai", "Grok", "openai-responses", "https://api.x.ai/v1", "xAI · Responses API"),
        ModelProviderPreset("minimax-cn", "MiniMax", "anthropic-messages", "https://api.minimax.cn/anthropic", "MiniMax · 中国区 Messages API"),
        ModelProviderPreset("starbridge-openrouter", "OpenRouter", "openai-completions", "https://openrouter.ai/api/v1", "聚合模型 · 填写完整模型 ID", false),
        ModelProviderPreset("starbridge-custom", "自定义服务", "openai-completions", "", "自定义地址、协议与模型 ID", false),
    )

    fun keyRef(id: String): String = "STARBRIDGE_" + id.uppercase(java.util.Locale.ROOT)
        .replace('-', '_') + "_API_KEY"

    fun validate(profile: ModelProviderProfile) {
        val preset = presets.singleOrNull { it.id == profile.id } ?: error("未知服务商")
        require(profile.protocol in protocols) { "不支持的 API 协议" }
        val uri = runCatching { URI(profile.baseUrl) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "请输入完整的 HTTPS API 基础地址，不要包含密钥、查询参数或接口方法"
        }
        require(!profile.baseUrl.endsWith("/chat/completions") &&
            !profile.baseUrl.endsWith("/responses") && !profile.baseUrl.endsWith("/messages")) {
            "请填写基础地址，不要附加 chat/completions、responses 或 messages"
        }
        require(profile.models.all { it.isNotBlank() && it.length <= 240 && it.none(Char::isISOControl) }) {
            "模型 ID 不能包含控制字符，且长度不得超过 240"
        }
        require(preset.catalog || profile.models.isNotEmpty()) { "请至少填写一个模型 ID" }
    }

    /** A separate Cordis patch; no changes to the runtime archive or user's profile. */
    fun renderOverlay(profiles: List<ModelProviderProfile>): String {
        val active = profiles.filter { it.enabled }
        if (active.isEmpty()) return "[]\n"
        return buildString {
            append("- id: llm-pi-ai\n  disabled: false\n  config:\n    providers:\n")
            for (profile in active) {
                validate(profile)
                val preset = presets.single { it.id == profile.id }
                append("      ").append(quoted(profile.id)).append(":\n")
                append("        displayName: ").append(quoted(preset.name)).append('\n')
                append("        apiKeyEnv: ").append(quoted(keyRef(profile.id))).append('\n')
                append("        api: ").append(quoted(profile.protocol)).append('\n')
                append("        baseURL: ").append(quoted(profile.baseUrl)).append('\n')
                if (profile.models.isNotEmpty()) {
                    append("        models:\n")
                    profile.models.forEach { append("          - id: ").append(quoted(it)).append('\n') }
                }
            }
        }
    }

    private fun quoted(value: String) = "'" + value.replace("'", "''") + "'"
}
