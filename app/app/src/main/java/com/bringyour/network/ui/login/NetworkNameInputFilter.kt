package com.bringyour.network.ui.login

// rules:
// - must start with a letter
// - alpha numeric and DNS compatible
fun networkNameInputFilter(input: String): String {
    // must start with a letter
    if (input.isEmpty() || !input[0].isLetter()) {
        return input
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