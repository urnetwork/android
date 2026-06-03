package com.bringyour.network.ui.login

// rules:
// - must start with a letter
// - alpha numeric and DNS compatible
fun networkNameInputFilter(input: String): String {
    // lowercase, keep only alphanumeric characters, and drop any leading
    // non-letters so the name always starts with a letter. Stripping the
    // leading non-letters (rather than clearing the whole field) avoids
    // wiping a valid name when a stray non-letter is entered at the start.
    return input
        .lowercase()
        .filter { it.isLetterOrDigit() }
        .dropWhile { !it.isLetter() }
}