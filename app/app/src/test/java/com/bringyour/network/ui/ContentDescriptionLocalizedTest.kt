package com.bringyour.network.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TalkBack reads a content description as written, so an English literal
 * there is read in English in every language (about 70 icons were). A content
 * description is null, for an icon whose adjacent text already says what it
 * does, or comes from the string catalogs (stringResource and the like).
 *
 * A lint-style check over the Kotlin and resource XML of the source sets that
 * ship; the unit test, device test and shared fixture sets never reach a user.
 * It reports a string literal used as
 * - a contentDescription named argument or semantics property,
 * - the value or default of a val, var or parameter named *Description, which
 *   includes contentDescription itself and is the usual way a label reaches a
 *   content description indirectly,
 * - a positional argument of Icon or Image (the content description slot),
 * - an android:contentDescription attribute.
 * A literal in an if, when or elvis branch counts; one nested in a call's
 * parentheses (a format argument of stringResource, say) is not the label.
 *
 * Reads the module's sources; no device.
 */
class ContentDescriptionLocalizedTest {

    // never shipped: unit tests, device tests and the fixtures they share
    private val testSourceSets = setOf("test", "testPlay", "androidTest", "acceptanceShared")

    // literals that are never shown or spoken, as "<path under src>: <literal>",
    // each with its reason; none so far, and an entry that matches nothing fails
    private val notUserFacing = emptySet<String>()

    // a contentDescription argument or semantics property, up to its '='
    private val namedPattern = Regex("""\bcontentDescription\s*=(?!=)""")
    // a val, var or parameter named *Description that is given a value or default
    private val declaredPattern = Regex(
        """\b(?:(?:val|var)\s+\w*[dD]escription\s*(?::\s*[\w.?<>]+\s*)?|\w*[dD]escription\s*:\s*[\w.?<>]+\s*)=(?!=)"""
    )
    // a call of Icon or Image, up to its '('
    private val iconPattern = Regex("""\b(?:Icon|Image)\s*\(""")
    private val namedArgumentPattern = Regex("""^\w+\s*=(?!=)""")
    private val xmlPattern = Regex("""android:contentDescription\s*=\s*"([^"]*)"""")

    // how a source character reads: code, part of a string literal (quotes and
    // template text included), or ignored (a comment, a char literal or a
    // backticked name, whose quotes and brackets mean nothing)
    private enum class Kind { CODE, STRING, IGNORED }

    // gradle runs unit tests with the module directory as the working
    // directory; the other candidates cover runners that start a level up
    private fun sourceRoot(): File {
        val root = listOf("src", "app/src", "app/app/src")
            .map { File(it) }
            .firstOrNull { File(it, "main/java").isDirectory }
        assertNotNull("the module's src directory is not found", root)
        return root!!
    }

    /** The [Kind] of each character of a Kotlin source. */
    private fun kinds(source: String): Array<Kind> {
        val kinds = Array(source.length) { Kind.CODE }
        val mark = { from: Int, to: Int, kind: Kind ->
            for (k in from until minOf(to, source.length)) {
                kinds[k] = kind
            }
        }
        // the open string quotes ("\"" or "\"\"\"") and the braces opened in
        // their ${...} templates, innermost last; a template is code again
        val open = ArrayDeque<String>()
        var i = 0
        while (i < source.length) {
            val quote = open.lastOrNull()?.takeIf { it.startsWith("\"") }
            if (quote != null) {
                when {
                    quote == "\"" && source[i] == '\\' -> {
                        mark(i, i + 2, Kind.STRING)
                        i += 2
                    }
                    source.startsWith("\${", i) -> {
                        mark(i, i + 2, Kind.STRING)
                        open.addLast("{")
                        i += 2
                    }
                    source.startsWith(quote, i) -> {
                        mark(i, i + quote.length, Kind.STRING)
                        open.removeLast()
                        i += quote.length
                    }
                    else -> {
                        kinds[i] = Kind.STRING
                        i++
                    }
                }
                continue
            }
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    mark(i, end, Kind.IGNORED)
                    i = end
                }
                source.startsWith("/*", i) -> {
                    // kotlin block comments nest
                    var depth = 0
                    var j = i
                    while (j < source.length) {
                        if (source.startsWith("/*", j)) {
                            depth++
                            j += 2
                        } else if (source.startsWith("*/", j)) {
                            depth--
                            j += 2
                            if (depth == 0) break
                        } else {
                            j++
                        }
                    }
                    mark(i, j, Kind.IGNORED)
                    i = j
                }
                source.startsWith("\"\"\"", i) -> {
                    mark(i, i + 3, Kind.STRING)
                    open.addLast("\"\"\"")
                    i += 3
                }
                source[i] == '"' -> {
                    kinds[i] = Kind.STRING
                    open.addLast("\"")
                    i++
                }
                source[i] == '\'' || source[i] == '`' -> {
                    val close = source[i]
                    val from = if (close == '\'' && source.getOrNull(i + 1) == '\\') i + 3 else i + 2
                    val end = source.indexOf(close, minOf(from, source.length)).let { if (it < 0) source.length else it + 1 }
                    mark(i, end, Kind.IGNORED)
                    i = end
                }
                source[i] == '{' && open.isNotEmpty() -> {
                    open.addLast("{")
                    i++
                }
                source[i] == '}' && open.lastOrNull() == "{" -> {
                    open.removeLast()
                    // the brace that closes a template belongs to its string
                    if (open.lastOrNull()?.startsWith("\"") == true) {
                        kinds[i] = Kind.STRING
                    }
                    i++
                }
                else -> i++
            }
        }
        return kinds
    }

    // the innermost bracket open at each position, or ' ' at the top level
    private fun enclosing(source: String, kinds: Array<Kind>): CharArray {
        val result = CharArray(source.length)
        val open = ArrayDeque<Char>()
        for (i in source.indices) {
            result[i] = open.lastOrNull() ?: ' '
            if (kinds[i] != Kind.CODE) continue
            when (source[i]) {
                '(', '[', '{' -> open.addLast(source[i])
                ')', ']', '}' -> open.removeLastOrNull()
            }
        }
        return result
    }

    // the 1-based line of a source offset
    private fun lineOf(source: String, index: Int) = 1 + source.substring(0, index).count { it == '\n' }

    // an expression on one line, for the report
    private fun collapse(text: String) = text.trim().replace(Regex("""\s+"""), " ")

    /**
     * The end of the expression that starts at [start] and whether a literal
     * sits in it outside every call's parentheses. In an argument list only a
     * comma or the closing parenthesis ends it; in a block, a line that the
     * next one does not continue does too.
     */
    private fun expression(source: String, kinds: Array<Kind>, start: Int, inArguments: Boolean): Pair<Int, Boolean> {
        val open = ArrayDeque<Char>()
        var literal = false
        var j = start
        while (j < source.length) {
            val c = source[j]
            if (kinds[j] == Kind.STRING) {
                if (open.none { it == '(' || it == '[' }) literal = true
                j++
                continue
            }
            if (kinds[j] == Kind.CODE) {
                if (c == '(' || c == '[' || c == '{') {
                    open.addLast(c)
                } else if (c == ')' || c == ']' || c == '}') {
                    if (open.isEmpty()) break
                    open.removeLast()
                } else if ((c == ',' || c == ';') && open.isEmpty()) {
                    break
                } else if (c == '\n' && open.isEmpty() && !inArguments) {
                    // the code so far, without its comments, and where the next line starts
                    val before = (start until j).filter { kinds[it] != Kind.IGNORED }
                        .map { source[it] }.joinToString("").trimEnd()
                    var next = j + 1
                    while (next < source.length && source[next].isWhitespace()) next++
                    val continued = before.isEmpty() ||
                        listOf("=", "(", "else", "->", "?:", "&&", "||", "+", ".").any { before.endsWith(it) } ||
                        listOf("else", "?:", ".", "?.", "&&", "||", "+").any { source.startsWith(it, next) }
                    if (!continued) break
                }
            }
            j++
        }
        return j to literal
    }

    /** Each literal content description in a Kotlin source, as "path:line: expression". */
    private fun kotlinLiterals(path: String, source: String): List<String> {
        val kinds = kinds(source)
        val enclosing = enclosing(source, kinds)
        val found = mutableListOf<String>()

        // one assignment can match both patterns; key it by its '='
        val assignments = sortedMapOf<Int, Int>()
        for (pattern in listOf(namedPattern, declaredPattern)) {
            for (match in pattern.findAll(source)) {
                if (kinds[match.range.first] != Kind.CODE) continue
                assignments.putIfAbsent(match.range.last, match.range.first)
            }
        }
        for ((equals, first) in assignments) {
            val (end, literal) = expression(source, kinds, equals + 1, enclosing[first] == '(')
            if (literal) {
                found.add("$path:${lineOf(source, first)}: ${collapse(source.substring(equals + 1, end))}")
            }
        }

        for (match in iconPattern.findAll(source)) {
            if (kinds[match.range.first] != Kind.CODE) continue
            // split the call's arguments at its own commas
            var argumentStart = match.range.last + 1
            var depth = 0
            var j = argumentStart
            while (j < source.length) {
                val c = source[j]
                val argumentEnd = kinds[j] == Kind.CODE && depth == 0 && (c == ',' || c == ')')
                if (argumentEnd) {
                    val argument = source.substring(argumentStart, j)
                    val offset = argumentStart + (argument.length - argument.trimStart().length)
                    val positional = !namedArgumentPattern.containsMatchIn(argument.trim())
                    if (positional && offset < j && kinds[offset] == Kind.STRING) {
                        found.add("$path:${lineOf(source, offset)}: ${collapse(argument)}")
                    }
                    if (c == ')') break
                    argumentStart = j + 1
                } else if (kinds[j] == Kind.CODE && (c == '(' || c == '[' || c == '{')) {
                    depth++
                } else if (kinds[j] == Kind.CODE && (c == ')' || c == ']' || c == '}')) {
                    depth--
                }
                j++
            }
        }
        return found
    }

    /** Each literal android:contentDescription in a resource file, as "path:line: \"value\"". */
    private fun xmlLiterals(path: String, source: String): List<String> {
        // blank comments but keep their line breaks
        val code = Regex("""<!--[\s\S]*?-->""").replace(source) { comment ->
            comment.value.replace(Regex("""[^\n]"""), " ")
        }
        return xmlPattern.findAll(code)
            .filter { !it.groupValues[1].startsWith("@") }
            .map { "$path:${lineOf(code, it.range.first)}: \"${it.groupValues[1]}\"" }
            .toList()
    }

    @Test
    fun `no content description is a string literal`() {
        val src = sourceRoot()
        val shipped = src.listFiles()!!.filter { it.isDirectory && it.name !in testSourceSets }
        val kotlinFiles = shipped.flatMap { set ->
            set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        val xmlFiles = shipped.flatMap { set ->
            File(set, "res").walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        }
        // a scan that reads nothing would pass
        assertTrue("only ${kotlinFiles.size} kotlin files under $src", 100 < kotlinFiles.size)

        val found = (
            kotlinFiles.flatMap { kotlinLiterals(it.relativeTo(src).path, it.readText()) } +
                xmlFiles.flatMap { xmlLiterals(it.relativeTo(src).path, it.readText()) }
            ).sorted()

        // an allowed literal is named without its line, which moves
        val withoutLine = { finding: String -> finding.replaceFirst(Regex(""":\d+: """), ": ") }
        val unmatched = notUserFacing - found.map(withoutLine).toSet()
        assertTrue("allowed literals that no longer exist: $unmatched", unmatched.isEmpty())

        val reported = found.filter { withoutLine(it) !in notUserFacing }
        assertTrue(
            """
            |${reported.size} content descriptions are literals, which TalkBack reads in English in every
            |language. Use null for an icon whose adjacent text says what it does, else a string resource:
            |${reported.joinToString("\n")}
            """.trimMargin(),
            reported.isEmpty(),
        )
    }

    @Test
    fun `the scan reports each literal form and nothing else`() {
        val source = """
            // contentDescription = "a comment"
            val note = "contentDescription = \"inside a string\""
            @Composable
            fun Sample(label: String, on: Boolean, contentDescription: String = "Info", altDescription: String? = null) {
                Icon(Icons.Filled.Check, contentDescription = "Check")
                Icon(Icons.Filled.Check, contentDescription = null)
                Icon(Icons.Filled.Check, contentDescription = stringResource(id = R.string.back))
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.verified_project_on, "Hub"))
                Icon(Icons.Filled.Star, contentDescription = if (on) "Filled star" else null)
                Icon(Icons.Filled.Star, "Star")
                Image(painterResource(R.drawable.globe), null)
                Box(modifier = Modifier.semantics { contentDescription = "Globe" })
                val stateDescription = when (on) {
                    true -> "On"
                    false -> stringResource(id = R.string.off)
                }
                val same = label == contentDescription
                Text(text = "${'$'}label ${'$'}{"x".length}", modifier = Modifier.testTag("acceptance.sample"))
                val ch = '"'
                Icon(Icons.Filled.Close, contentDescription = "Close")
                val trailingDescription = stringResource(id = R.string.back) // a comment that ends in =
                "the next statement"
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "Sample.kt:4: \"Info\"",
                "Sample.kt:5: \"Check\"",
                "Sample.kt:9: if (on) \"Filled star\" else null",
                "Sample.kt:12: \"Globe\"",
                "Sample.kt:13: when (on) { true -> \"On\" false -> stringResource(id = R.string.off) }",
                "Sample.kt:20: \"Close\"",
                "Sample.kt:10: \"Star\"",
            ),
            kotlinLiterals("Sample.kt", source),
        )

        val layout = """
            <ImageView android:contentDescription="Logo" />
            <!-- <ImageView android:contentDescription="Commented" /> -->
            <ImageView android:contentDescription="@string/logo" />
            <ImageView android:contentDescription="@null" />
        """.trimIndent()
        assertEquals(listOf("layout.xml:1: \"Logo\""), xmlLiterals("layout.xml", layout))
    }
}
