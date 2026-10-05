package com.tapgerman.reader.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * User-facing text that the view model produces but the view has to resolve.
 *
 * All UI copy used to be inline English in the composables, which meant the strings had to move
 * to `res/values/strings.xml`; that is easy. The awkward part is the handful of messages the
 * `ViewModel` originates — "No dictionary entry for X", the four Anki outcomes, the two delete
 * confirmations — which cannot call `stringResource` because they are not composables.
 *
 * The alternative, passing a `Context` into the view model and calling `getString` there, works
 * and is what a lot of projects do, but it quietly makes the view model responsible for the UI
 * language and makes every one of those messages untestable and untranslatable. This carries the
 * resource id instead and lets the view resolve it, so a translator sees a string and a test sees
 * no Android dependency at all.
 */
sealed interface UiText {

    /**
     * Text that is already final, or is built from data the view model has and a translation
     * cannot restructure: an article title, a word the learner typed.
     */
    data class Raw(val value: String) : UiText

    /** A localised string, with positional arguments applied. */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** Convenience so a `UiText` reads at the call site rather than always being wrapped. */
fun uiText(value: String): UiText = UiText.Raw(value)
