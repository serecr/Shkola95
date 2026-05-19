package com.devin.schoolbell95.data

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var shift: Int
        get() = sp.getInt(KEY_SHIFT, 1)
        set(value) = sp.edit { putInt(KEY_SHIFT, value) }

    var className: String
        get() = sp.getString(KEY_CLASS, "6А") ?: "6А"
        set(value) = sp.edit { putString(KEY_CLASS, value) }

    var darkMode: String
        get() = sp.getString(KEY_DARK, "system") ?: "system"
        set(value) = sp.edit { putString(KEY_DARK, value) }

    var accentColor: String
        get() = sp.getString(KEY_ACCENT, "blue") ?: "blue"
        set(value) = sp.edit { putString(KEY_ACCENT, value) }

    var dynamicColor: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, false)
        set(value) = sp.edit { putBoolean(KEY_DYNAMIC, value) }

    var notifyOnBell: Boolean
        get() = sp.getBoolean(KEY_BELL, true)
        set(value) = sp.edit { putBoolean(KEY_BELL, value) }

    var notify5Min: Boolean
        get() = sp.getBoolean(KEY_5MIN, true)
        set(value) = sp.edit { putBoolean(KEY_5MIN, value) }

    /** Hugging Face read token, used to download the gated Gemma model. */
    var hfToken: String
        get() = sp.getString(KEY_HF_TOKEN, "") ?: ""
        set(value) = sp.edit { putString(KEY_HF_TOKEN, value.trim()) }

    /** Optional override for the model download URL (.task file). Empty = use default. */
    var modelUrl: String
        get() = sp.getString(KEY_MODEL_URL, "") ?: ""
        set(value) = sp.edit { putString(KEY_MODEL_URL, value.trim()) }

    /** When true the assistant routes free-form questions to the on-device LLM. */
    var aiEnabled: Boolean
        get() = sp.getBoolean(KEY_AI_ENABLED, true)
        set(value) = sp.edit { putBoolean(KEY_AI_ENABLED, value) }

    /** User-tunable system prompt for the local LLM. Empty = use built-in default. */
    var aiSystemPrompt: String
        get() = sp.getString(KEY_AI_PROMPT, "") ?: ""
        set(value) = sp.edit { putString(KEY_AI_PROMPT, value) }

    companion object {
        private const val KEY_SHIFT = "shift"
        private const val KEY_CLASS = "class"
        private const val KEY_DARK = "dark"
        private const val KEY_ACCENT = "accent"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_BELL = "bell"
        private const val KEY_5MIN = "five_min"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_MODEL_URL = "model_url"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_PROMPT = "ai_system_prompt"
    }
}
