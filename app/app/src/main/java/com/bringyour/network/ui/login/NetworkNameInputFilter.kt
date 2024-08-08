package com.bringyour.network.ui.login

import android.text.InputFilter
import android.text.Spanned

// TODO - deprecate NetworkNameInputFilter, use function instead
// rules:
// - must start with a letter
// - alpha numeric and DNS compatible
class NetworkNameInputFilter: InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        if (start == end) {
            // delete, accept
            return null
        } else if (dstart == 0) {
            // must start with a letter
            val out = StringBuilder()
            for (i in start until end) {
                val allow = (Character.isLetter(source[i]) ||
                        0 < out.length && (Character.isLetter(source[i]) || Character.isDigit(source[i])))
                if (allow) {
                    out.append(Character.toLowerCase(source[i]))
                }
            }
            return out
        } else {
            val out = StringBuilder()
            for (i in start until end) {
                val allow = Character.isLetter(source[i]) || Character.isDigit(source[i])
                if (allow) {
                    out.append(Character.toLowerCase(source[i]))
                }
            }
            return out
        }
    }
}

// rules:
// - must start with a letter
// - alpha numeric and DNS compatible
fun NetworkNameInputFilter(input: String): String {
    // must start with a letter
    if (input.isEmpty() || !input[0].isLetter()) {
        return input
    }

    // convert all uppercase letters to lowercase
    val filtered = input.map {
        if (it.isUpperCase()) it.lowercaseChar() else it
    }.filter {
        // allow only alphanumeric characters and hyphens
        it.isLetterOrDigit() || it == '-'
    }.joinToString("")

    return filtered
}