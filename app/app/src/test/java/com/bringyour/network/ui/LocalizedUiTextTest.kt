package com.bringyour.network.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app shows or speaks comes from the string catalogs, and TalkBack
 * reads each thing once. A lint-style check over the Kotlin and resource XML
 * of the source sets that ship (the unit test, device test and shared fixture
 * sets never reach a user), with three rules.
 *
 * - Spoken: TalkBack reads a content description as written, so an English
 *   literal there is read in English in every language (about 70 icons were).
 *   A content description is null, for an icon whose adjacent text already
 *   says what it does, or comes from the catalogs. Reported: a literal used as
 *   a contentDescription argument or semantics property, as the value or
 *   default of anything named *Description, as a positional Icon or Image
 *   argument, or as an android:contentDescription attribute.
 * - Shown: a literal with a letter in it reads the same, English, on every
 *   screen. Reported: the text of Text, showSnackbar and Toast.makeText, the
 *   value or default of anything named text, *Text, *Label, title, *Title or
 *   *Placeholder, and the android:text, hint, title, label, summary,
 *   description and tooltipText attributes. Literals in @Preview functions
 *   are design-time samples and are skipped.
 * - Repeated: an icon labeled with the text of a Text beside it is read twice
 *   ("Licenses, Licenses"). Beside means in the icon's own block, or in
 *   another slot of the call that block is an argument of (an AlertDialog's
 *   icon and title).
 *
 * In every rule a literal in an if, when or elvis branch counts, and one
 * nested in a call's parentheses (a format argument of stringResource, say)
 * does not. Reads the module's sources; no device.
 */
class LocalizedUiTextTest {

    private val ui = "main/java/com/bringyour/network/ui"

    // never shipped: unit tests, device tests and the fixtures they share
    private val testSourceSets = setOf("test", "testPlay", "androidTest", "acceptanceShared")

    // content description literals that are never spoken, as "<path under src>: <literal>"
    // with the reason; none so far, and an entry that matches nothing fails
    private val spokenAllowed = emptyMap<String, String>()

    private val debugOnly = "shown only in debug builds (BuildConfig.DEBUG): sample data switches for developers"
    private val previewData = "sample data for the @Preview; the real license names and texts come from the bundled licenses"

    // visible literals that no user reads as English text, as "<path under src>: <literal>"
    // with the reason; an entry that matches nothing fails
    private val shownAllowed = mapOf(
        "$ui/settings/DeveloperScreen.kt: \"Earnings (debug)\"" to debugOnly,
        "$ui/settings/DeveloperScreen.kt: \"Sample protocol data\"" to debugOnly,
        "$ui/settings/DeveloperScreen.kt: \"Sample gas key unfunded (v\$version)\"" to debugOnly,
        "$ui/settings/DeveloperScreen.kt: \"Sample starts without a wallet\"" to debugOnly,
        "$ui/settings/DeveloperScreen.kt: \"Sample starts with a Solana payout wallet\"" to debugOnly,
        "$ui/settings/DeveloperScreen.kt: \"Sample has 3.87 USDC waiting\"" to debugOnly,
        "$ui/settings/LicensesScreen.kt: \"Creative Commons Attribution-ShareAlike 4.0 International\"" to previewData,
        "$ui/settings/LicensesScreen.kt: \"Apache License\\nVersion 2.0, January 2004\\nhttp://www.apache.org/licenses/\"" to previewData,
        "$ui/stats/DnsSettingsScreen.kt: \"https://\"" to "the URL scheme the field expects, typed the same in every language",
    )

    // icons whose label may repeat the text beside them; none so far
    private val repeatedAllowed = emptyMap<String, String>()

    // a contentDescription argument or semantics property, up to its '='
    private val describedPattern = Regex("""\bcontentDescription\s*=(?!=)""")
    // a val, var or parameter named *Description that is given a value or default
    private val descriptionPattern = Regex(
        """\b(?:(?:val|var)\s+\w*[dD]escription\s*(?::\s*[\w.?<>]+\s*)?|\w*[dD]escription\s*:\s*[\w.?<>]+\s*)=(?!=)"""
    )
    // a call of Icon or Image, up to its '('
    private val iconPattern = Regex("""\b(?:Icon|Image)\s*\(""")
    private val textPattern = Regex("""\bText\s*\(""")

    // the names whose value is shown: text, *Text, *Label, title, *Title, placeholder, *Placeholder
    private val shownName = """(?:text|\w*Text|\w*Label|title|\w*Title|placeholder|\w*Placeholder)"""
    // such a name as an argument, up to its '='
    private val shownArgumentPattern = Regex("""(?<![\w.])$shownName\s*=(?!=)""")
    // such a name as a val, var or parameter that is given a value or default
    private val shownDeclarationPattern = Regex(
        """\b(?:val|var)\s+$shownName\b\s*(?::\s*[\w.?<>]+\s*)?=(?!=)|(?<![\w.])$shownName\s*:\s*[\w.?<>]+\s*=(?!=)"""
    )

    /** A call whose text is shown, and its argument that holds the text, by name or else by position. */
    private data class ShownCall(val pattern: Regex, val name: String?, val position: Int)

    private val shownCalls = listOf(
        ShownCall(pattern = Regex("""\bText\s*\("""), name = "text", position = 0),
        ShownCall(pattern = Regex("""\bshowSnackbar\s*\("""), name = "message", position = 0),
        ShownCall(pattern = Regex("""\bmakeText\s*\("""), name = null, position = 1),
    )

    private val xmlPattern = Regex(
        """android:(contentDescription|text|hint|title|label|summary|description|tooltipText)\s*=\s*"([^"]*)""""
    )

    /** The three rules' findings over a set of sources, each as "path:line: expression". */
    private class Findings(val spoken: List<String>, val shown: List<String>, val repeated: List<String>)

    // gradle runs unit tests with the module directory as the working
    // directory; the other candidates cover runners that start a level up
    private fun sourceRoot(): File {
        val root = listOf("src", "app/src", "app/app/src")
            .map { File(it) }
            .firstOrNull { File(it, "main/java").isDirectory }
        assertNotNull("the module's src directory is not found", root)
        return root!!
    }

    private fun scanShipped(): Findings {
        val src = sourceRoot()
        val shipped = src.listFiles()!!.filter { it.isDirectory && it.name !in testSourceSets }
        val kotlinFiles = shipped.flatMap { set ->
            set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        val xmlFiles = shipped.flatMap { set ->
            set.walkTopDown().filter {
                it.isFile && it.extension == "xml" && (it.name == "AndroidManifest.xml" || "${File.separator}res${File.separator}" in it.path)
            }.toList()
        }
        // a scan that reads nothing would pass
        assertTrue("only ${kotlinFiles.size} kotlin files under $src", 100 < kotlinFiles.size)

        val sources = kotlinFiles.map { KotlinSource(path = it.relativeTo(src).path, text = it.readText()) }
        val xml = xmlFiles.map { xmlFindings(it.relativeTo(src).path, it.readText()) }
        return Findings(
            spoken = (sources.flatMap { spokenLiterals(it) } + xml.flatMap { it.spoken }).sorted(),
            shown = (sources.flatMap { shownLiterals(it) } + xml.flatMap { it.shown }).sorted(),
            repeated = sources.flatMap { repeatedLabels(it) }.sorted(),
        )
    }

    // fails on an allowed entry that matches nothing, then on any finding not allowed;
    // an allowed entry is named without its line, which moves
    private fun assertNoFindings(problem: String, found: List<String>, allowed: Map<String, String>) {
        val withoutLine = { finding: String -> finding.replaceFirst(Regex(""":\d+: """), ": ") }
        val unmatched = allowed.keys - found.map(withoutLine).toSet()
        assertTrue("allowed entries that no longer exist: $unmatched", unmatched.isEmpty())
        val reported = found.filter { withoutLine(it) !in allowed }
        assertTrue("${reported.size} $problem:\n${reported.joinToString("\n")}", reported.isEmpty())
    }

    /** Each literal content description in a Kotlin source. */
    private fun spokenLiterals(source: KotlinSource): List<String> {
        val found = mutableListOf<String>()
        for (value in source.assignedValues(listOf(describedPattern, descriptionPattern))) {
            if (source.hasLiteral(value, words = false)) found.add(source.finding(value))
        }
        for (match in iconPattern.findAll(source.text)) {
            if (!source.isCode(match.range.first)) continue
            for (argument in source.arguments(match.range.last)) {
                val start = source.skipSpace(argument.first, argument.last + 1)
                val positional = !Regex("""^\w+\s*=(?!=)""").containsMatchIn(source.text.substring(start, argument.last + 1))
                if (positional && start <= argument.last && source.kinds[start] == Kind.STRING) {
                    found.add(source.finding(argument))
                }
            }
        }
        return found
    }

    /** Each visible literal with a letter in it, outside @Preview functions, in a Kotlin source. */
    private fun shownLiterals(source: KotlinSource): List<String> {
        val found = mutableListOf<String>()
        for (call in shownCalls) {
            for (match in call.pattern.findAll(source.text)) {
                if (!source.isCode(match.range.first) || source.inPreview(match.range.first)) continue
                val value = source.argument(match.range.last, call.name, call.position) ?: continue
                if (source.hasLiteral(value, words = true)) found.add(source.finding(value))
            }
        }
        for (value in source.assignedValues(listOf(shownArgumentPattern, shownDeclarationPattern))) {
            if (source.inPreview(value.first)) continue
            if (source.hasLiteral(value, words = true)) found.add(source.finding(value))
        }
        return found.distinct()
    }

    /** Each icon labeled with the text of a Text beside it, in a Kotlin source. */
    private fun repeatedLabels(source: KotlinSource): List<String> {
        val text = source.text
        // the same expression however it is spaced, and with or without "id ="
        val normal = { range: IntRange -> text.substring(range).replace(Regex("""\bid\s*=\s*"""), "").replace(Regex("""\s+"""), "") }
        val found = mutableListOf<String>()
        for (match in iconPattern.findAll(text)) {
            if (!source.isCode(match.range.first) || source.inPreview(match.range.first)) continue
            val label = source.argument(match.range.last, "contentDescription", 1) ?: continue
            val labelText = normal(label)
            if (labelText.isEmpty() || labelText == "null" || source.hasLiteral(label, words = false)) continue
            // the icon's own block, and when that block is an argument, the call it is passed to
            val block = source.innerOpen[match.range.first]
            if (block < 0) continue
            val neighbourhoods = mutableListOf(block..(source.closeOf[block] ?: text.length - 1))
            val call = source.innerOpen[block]
            if (text[block] == '{' && 0 <= call && text[call] == '(') {
                neighbourhoods.add(call..(source.closeOf[call] ?: text.length - 1))
            }
            val repeated = neighbourhoods.any { neighbourhood ->
                textPattern.findAll(text, neighbourhood.first).takeWhile { it.range.first <= neighbourhood.last }.any { t ->
                    val shown = if (source.isCode(t.range.first)) source.argument(t.range.last, "text", 0) else null
                    shown != null && normal(shown) == labelText
                }
            }
            if (repeated) found.add(source.finding(label))
        }
        return found
    }

    /** Each literal text attribute in a resource or manifest file: content descriptions, and the visible ones. */
    private fun xmlFindings(path: String, source: String): Findings {
        // blank comments but keep their line breaks
        val code = Regex("""<!--[\s\S]*?-->""").replace(source) { comment ->
            comment.value.replace(Regex("""[^\n]"""), " ")
        }
        val spoken = mutableListOf<String>()
        val shown = mutableListOf<String>()
        for (match in xmlPattern.findAll(code)) {
            val (attribute, value) = match.destructured
            if (value.startsWith("@") || value.startsWith("?")) continue
            val finding = "$path:${1 + code.substring(0, match.range.first).count { it == '\n' }}: \"$value\""
            if (attribute == "contentDescription") {
                spoken.add(finding)
            } else if (value.any { it.isLetter() }) {
                shown.add(finding)
            }
        }
        return Findings(spoken = spoken, shown = shown, repeated = emptyList())
    }

    @Test
    fun `no content description is a string literal`() {
        assertNoFindings(
            "content descriptions are literals, which TalkBack reads in English in every language. Use null for an icon whose adjacent text says what it does, else a string resource",
            scanShipped().spoken,
            spokenAllowed,
        )
    }

    @Test
    fun `no visible text is an English literal`() {
        assertNoFindings(
            "visible texts are literals, which read in English in every language. Use a string resource from the localizations store",
            scanShipped().shown,
            shownAllowed,
        )
    }

    @Test
    fun `no icon repeats the text beside it`() {
        assertNoFindings(
            "icons are labeled with the text beside them, so TalkBack reads it twice. Make the icon decorative (contentDescription = null)",
            scanShipped().repeated,
            repeatedAllowed,
        )
    }

    @Test
    fun `the spoken scan reports each literal form and nothing else`() {
        val source = KotlinSource(
            path = "Sample.kt",
            text = """
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
            """.trimIndent(),
        )
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
            spokenLiterals(source),
        )
    }

    @Test
    fun `the shown scan reports each literal form and nothing else`() {
        val source = KotlinSource(
            path = "Sample.kt",
            text = """
                @Composable
                fun Sample(on: Boolean, count: Int, zeroLabel: String = "Unlimited") {
                    Text("Hello")
                    Text(text = if (on) "On" else stringResource(id = R.string.off))
                    Text(stringResource(R.string.verified_project_on, "Hub"))
                    Text("${'$'}count")
                    Text("—")
                    Text("${'$'}{count} TAO")
                    Row(modifier = Modifier.testTag("acceptance.row"), title = "Plain title")
                    PopupActionRow(onClick = {}, text = "Guest Mode")
                    val statusText = when {
                        on -> "Connecting"
                        else -> stringResource(id = R.string.connected)
                    }
                    val scale by animateFloatAsState(targetValue = 1f, label = "scale")
                    val signed = sign(message = "Welcome - ${'$'}count")
                    scope.launch { snackbarHostState.showSnackbar(message = "Saved") }
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                    Toast.makeText(context, context.getString(R.string.close), Toast.LENGTH_SHORT).show()
                }

                @Preview(showBackground = true)
                @Composable
                private fun SamplePreview() {
                    Text("Preview only")
                }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "Sample.kt:3: \"Hello\"",
                "Sample.kt:4: if (on) \"On\" else stringResource(id = R.string.off)",
                "Sample.kt:8: \"${'$'}{count} TAO\"",
                "Sample.kt:17: \"Saved\"",
                "Sample.kt:18: \"Failed\"",
                "Sample.kt:2: \"Unlimited\"",
                "Sample.kt:9: \"Plain title\"",
                "Sample.kt:10: \"Guest Mode\"",
                "Sample.kt:11: when { on -> \"Connecting\" else -> stringResource(id = R.string.connected) }",
            ),
            shownLiterals(source),
        )
    }

    @Test
    fun `the repeated scan reports an icon that repeats its text and nothing else`() {
        val source = KotlinSource(
            path = "Sample.kt",
            text = """
                @Composable
                fun Sample(label: String, onClick: () -> Unit) {
                    Row(modifier = Modifier.clickable { onClick() }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(id = R.string.licenses))
                        Text(stringResource(R.string.licenses))
                    }
                    AlertDialog(
                        icon = { Icon(Icons.Filled.LinkOff, contentDescription = label) },
                        title = { Text(text = label) },
                        onDismissRequest = onClick,
                        confirmButton = {},
                    )
                    Row {
                        Text(label)
                        IconButton(onClick = onClick) {
                            Icon(Icons.Filled.Share, contentDescription = label)
                        }
                    }
                    Row {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(label)
                    }
                    Row {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(id = R.string.learn_more))
                        Text(label)
                    }
                }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "Sample.kt:4: stringResource(id = R.string.licenses)",
                "Sample.kt:8: label",
            ),
            repeatedLabels(source),
        )
    }

    @Test
    fun `the resource scan reports literal text attributes and nothing else`() {
        val layout = """
            <ImageView android:contentDescription="Logo" />
            <!-- <ImageView android:contentDescription="Commented" /> -->
            <ImageView android:contentDescription="@string/logo" />
            <ImageView android:contentDescription="@null" />
            <TextView android:text="Hello" tools:text="Design sample" android:hint="@string/hint" />
            <TextView android:text="—" android:label="?attr/title" />
        """.trimIndent()
        val findings = xmlFindings("layout.xml", layout)
        assertEquals(listOf("layout.xml:1: \"Logo\""), findings.spoken)
        assertEquals(listOf("layout.xml:5: \"Hello\""), findings.shown)
    }
}

/** How a source character reads: code, part of a string literal (quotes and template text included), or ignored. */
private enum class Kind { CODE, STRING, IGNORED }

/**
 * A Kotlin source read for the rules: the kind of each character, its
 * brackets and its @Preview function bodies. Comments, char literals and
 * backticked names are ignored, so their quotes and brackets mean nothing; a
 * string template's ${...} is code again.
 */
private class KotlinSource(val path: String, val text: String) {
    val kinds: Array<Kind> = Array(text.length) { Kind.CODE }
    // the innermost bracket open at each position, -1 at the top level
    val innerOpen = IntArray(text.length)
    // the closing bracket of each opening one
    val closeOf = HashMap<Int, Int>()
    private val previewBodies = mutableListOf<IntRange>()

    init {
        markKinds()
        val open = ArrayDeque<Int>()
        for (i in text.indices) {
            innerOpen[i] = open.lastOrNull() ?: -1
            if (kinds[i] != Kind.CODE) continue
            when (text[i]) {
                '(', '[', '{' -> open.addLast(i)
                ')', ']', '}' -> open.removeLastOrNull()?.let { closeOf[it] = i }
            }
        }
        // a preview function, not @PreviewParameter on one of its parameters
        for (match in Regex("""@Preview(?!Parameter)\w*""").findAll(text)) {
            if (!isCode(match.range.first)) continue
            val function = text.indexOf("fun ", match.range.last)
            if (function < 0) continue
            val body = (function until text.length).firstOrNull { text[it] == '{' && isCode(it) } ?: continue
            previewBodies.add(match.range.first..(closeOf[body] ?: text.length - 1))
        }
    }

    private fun markKinds() {
        val mark = { from: Int, to: Int, kind: Kind ->
            for (k in from until minOf(to, text.length)) {
                kinds[k] = kind
            }
        }
        // the open string quotes ("\"" or "\"\"\"") and the braces opened in
        // their ${...} templates, innermost last
        val open = ArrayDeque<String>()
        var i = 0
        while (i < text.length) {
            val quote = open.lastOrNull()?.takeIf { it.startsWith("\"") }
            if (quote != null) {
                when {
                    quote == "\"" && text[i] == '\\' -> {
                        mark(i, i + 2, Kind.STRING)
                        i += 2
                    }
                    text.startsWith("\${", i) -> {
                        mark(i, i + 2, Kind.STRING)
                        open.addLast("{")
                        i += 2
                    }
                    text.startsWith(quote, i) -> {
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
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    mark(i, end, Kind.IGNORED)
                    i = end
                }
                text.startsWith("/*", i) -> {
                    // kotlin block comments nest
                    var depth = 0
                    var j = i
                    while (j < text.length) {
                        if (text.startsWith("/*", j)) {
                            depth++
                            j += 2
                        } else if (text.startsWith("*/", j)) {
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
                text.startsWith("\"\"\"", i) -> {
                    mark(i, i + 3, Kind.STRING)
                    open.addLast("\"\"\"")
                    i += 3
                }
                text[i] == '"' -> {
                    kinds[i] = Kind.STRING
                    open.addLast("\"")
                    i++
                }
                text[i] == '\'' || text[i] == '`' -> {
                    val close = text[i]
                    val from = if (close == '\'' && text.getOrNull(i + 1) == '\\') i + 3 else i + 2
                    val end = text.indexOf(close, minOf(from, text.length)).let { if (it < 0) text.length else it + 1 }
                    mark(i, end, Kind.IGNORED)
                    i = end
                }
                text[i] == '{' && open.isNotEmpty() -> {
                    open.addLast("{")
                    i++
                }
                text[i] == '}' && open.lastOrNull() == "{" -> {
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
    }

    fun isCode(index: Int) = kinds[index] == Kind.CODE

    fun inPreview(index: Int) = previewBodies.any { index in it }

    // the first position from start, before end, that is not white space
    fun skipSpace(start: Int, end: Int): Int {
        var i = start
        while (i < end && text[i].isWhitespace()) i++
        return i
    }

    // the report line of an expression: its file, its 1-based line and its text on one line
    fun finding(range: IntRange): String {
        val start = skipSpace(range.first, range.last + 1)
        val line = 1 + text.substring(0, start).count { it == '\n' }
        return "$path:$line: ${text.substring(range).trim().replace(Regex("""\s+"""), " ")}"
    }

    /** The argument spans of the call whose '(' is at open, split at its own commas. */
    fun arguments(open: Int): List<IntRange> {
        val close = closeOf[open] ?: return emptyList()
        val result = mutableListOf<IntRange>()
        var start = open + 1
        for (i in open + 1..close) {
            if (i == close || (text[i] == ',' && innerOpen[i] == open && isCode(i))) {
                if (text.substring(start, i).isNotBlank()) result.add(start until i)
                start = i + 1
            }
        }
        return result
    }

    /**
     * The value of the argument named name in the call whose '(' is at open,
     * else of its positional argument at position; null when there is none.
     */
    fun argument(open: Int, name: String?, position: Int): IntRange? {
        val positional = mutableListOf<IntRange>()
        for (argument in arguments(open)) {
            val named = Regex("""^\s*(\w+)\s*=(?!=)""").find(text.substring(argument))
            if (named == null) {
                positional.add(argument)
            } else if (named.groupValues[1] == name) {
                return (argument.first + named.range.last + 1)..argument.last
            }
        }
        return positional.getOrNull(position)
    }

    /**
     * Whether a string literal sits in range outside every call's parentheses,
     * and for words, whether it has a letter outside its templates and escapes.
     */
    fun hasLiteral(range: IntRange, words: Boolean): Boolean {
        val open = ArrayDeque<Char>()
        var i = range.first
        while (i <= range.last) {
            if (kinds[i] == Kind.STRING && open.none { it == '(' || it == '[' }) {
                var end = i
                while (end <= range.last && kinds[end] == Kind.STRING) end++
                val content = text.substring(i, end)
                    .replace(Regex("""\\u[0-9a-fA-F]{4}|\\.|\$\{|\$\w+|["}]"""), "")
                if (!words || content.any { it.isLetter() }) return true
                i = end
                continue
            }
            if (kinds[i] == Kind.CODE) {
                when (text[i]) {
                    '(', '[', '{' -> open.addLast(text[i])
                    ')', ']', '}' -> open.removeLastOrNull()
                }
            }
            i++
        }
        return false
    }

    /**
     * The value of each assignment that one of the patterns finds up to its
     * '=', once per '='. In an argument list only a comma or the closing
     * parenthesis ends a value; in a block, a line that the next one does not
     * continue does too.
     */
    fun assignedValues(patterns: List<Regex>): List<IntRange> {
        val equalsAt = sortedMapOf<Int, Int>()
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                if (isCode(match.range.first)) equalsAt.putIfAbsent(match.range.last, match.range.first)
            }
        }
        return equalsAt.map { (equals, first) ->
            val inArguments = 0 <= innerOpen[first] && text[innerOpen[first]] == '('
            (equals + 1) until valueEnd(equals + 1, inArguments)
        }
    }

    private fun valueEnd(start: Int, inArguments: Boolean): Int {
        val open = ArrayDeque<Char>()
        var j = start
        while (j < text.length) {
            val c = text[j]
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
                        .map { text[it] }.joinToString("").trimEnd()
                    val next = skipSpace(j + 1, text.length)
                    val continued = before.isEmpty() ||
                        listOf("=", "(", "else", "->", "?:", "&&", "||", "+", ".").any { before.endsWith(it) } ||
                        listOf("else", "?:", ".", "?.", "&&", "||", "+").any { text.startsWith(it, next) }
                    if (!continued) break
                }
            }
            j++
        }
        return j
    }
}
