package com.bringyour.network.ui.login

// rules:
// - must start with a letter
// - alpha numeric and DNS compatible
fun networkNameInputFilter(input: String): String {
    if (input.isEmpty()) {
        return input
    }
    // must start with a letter
    if (!input[0].isLetter()) {
        return ""
    }

    // convert all uppercase letters to lowercase
    val filtered = input.map {
        if (it.isUpperCase()) it.lowercaseChar() else it
    }.filter {
        // allow only alphanumeric characters and hyphens
        it.isLetterOrDigit()
    }.joinToString("")

    return filtered
}