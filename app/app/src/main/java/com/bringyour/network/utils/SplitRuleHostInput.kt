package com.bringyour.network.utils



sealed class SplitRuleHostError {
    object NotAscii : SplitRuleHostError()
    object BadName : SplitRuleHostError()
    object BadWildcard : SplitRuleHostError()
    object BadRange : SplitRuleHostError()
    object Duplicate : SplitRuleHostError()
    data class Covered(val by: String) : SplitRuleHostError()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitRuleHostError) return false
        return when {
            this is NotAscii && other is NotAscii -> true
            this is BadName && other is BadName -> true
            this is BadWildcard && other is BadWildcard -> true
            this is BadRange && other is BadRange -> true
            this is Duplicate && other is Duplicate -> true
            this is Covered && other is Covered -> this.by == other.by
            else -> false
        }
    }

    override fun hashCode(): Int = when (this) {
        is NotAscii -> 1
        is BadName -> 2
        is BadWildcard -> 3
        is BadRange -> 4
        is Duplicate -> 5
        is Covered -> 31 * 6 + by.hashCode()
    }
}

data class SplitRuleHostValidation(
    val normalized: String?,
    val error: SplitRuleHostError?,
    val note: String?,
) {
    val isAccepted: Boolean
        get() = normalized != null && error == null
}

/**
 * Validates a hand-typed split rule host, wildcard, or CIDR range.
 *
 * Invariant: NEVER be more permissive than the Go matcher. The Go matcher
 * files anything it cannot parse as an exact host name and has no downstream error
 * channel, so an invalid string becomes a rule that is created, saved, and never matched.
 */
object SplitRuleHostInput {
    private const val MAX_NAME_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63

    fun validate(raw: String, existing: List<String> = emptyList()): SplitRuleHostValidation {
        val host = raw.trim().lowercase()
        if (host.isEmpty()) {
            return SplitRuleHostValidation(normalized = null, error = null, note = null)
        }

        // The matcher lowercases and compares bytes; a resolved name arrives
        // as punycode, so a unicode name here could only ever be dead
        if (!host.all { it.code in 0..127 }) {
            return rejected(SplitRuleHostError.NotAscii)
        }

        val normalized: String
        var note: String? = null

        val wildcardBase = host.dropPrefixIfPresent("**.") ?: host.dropPrefixIfPresent("*.")
        if (wildcardBase != null) {
            if (!isValidName(wildcardBase)) {
                return rejected(SplitRuleHostError.BadWildcard)
            }
            normalized = host
        } else if (host.contains('/')) {
            val masked = maskedPrefix(host) ?: return rejected(SplitRuleHostError.BadRange)
            normalized = masked
            if (masked != host) {
                note = masked
            }
        } else {
            val address = normalizedAddress(host)
            if (address != null) {
                normalized = address
                if (address != host) {
                    note = address
                }
            } else {
                if (!isValidName(host)) {
                    return rejected(SplitRuleHostError.BadName)
                }
                normalized = host
            }
        }

        if (existing.contains(normalized)) {
            return rejected(SplitRuleHostError.Duplicate)
        }
        val cover = existing.firstOrNull { covers(it, normalized) }
        if (cover != null) {
            return SplitRuleHostValidation(normalized = null, error = SplitRuleHostError.Covered(cover), note = null)
        }
        return SplitRuleHostValidation(normalized = normalized, error = null, note = note)
    }

    private fun rejected(error: SplitRuleHostError): SplitRuleHostValidation =
        SplitRuleHostValidation(normalized = null, error = error, note = null)

    fun isValidName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH || name.endsWith('.')) {
            return false
        }
        val labels = name.split('.')
        if (labels.size < 2) {
            return false
        }
        return labels.all { label ->
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return@all false
            if (label.startsWith('-') || label.endsWith('-')) return@all false
            label.all { ch -> (ch in 'a'..'z') || (ch in '0'..'9') || ch == '-' }
        }
    }

    fun normalizedAddress(value: String): String? {
        val v4 = parseIpv4Bytes(value)
        if (v4 != null) {
            return formatIpv4(v4)
        }
        val v6 = parseIpv6Bytes(value) ?: return null
        // ::ffff:1.2.3.4 -> 1.2.3.4, matching netip's Unmap()
        if (v6.take(10).all { it == 0.toByte() } && v6[10] == 0xFF.toByte() && v6[11] == 0xFF.toByte()) {
            return formatIpv4(v6.copyOfRange(12, 16))
        }
        return formatIpv6(v6)
    }

    fun maskedPrefix(value: String): String? {
        val parts = value.split('/')
        if (parts.size != 2 || parts[1].isEmpty()) {
            return null
        }
        // digits only and no leading zero: toIntOrNull alone takes "+8" and
        // "08", which netip.ParsePrefix refuses
        val bitsText = parts[1]
        if (!bitsText.all { it in '0'..'9' } || (bitsText.length > 1 && bitsText.startsWith('0'))) {
            return null
        }
        val bits = bitsText.toIntOrNull() ?: return null
        val address = parts[0]

        val v4 = parseIpv4Bytes(address)
        if (v4 != null) {
            if (bits !in 0..32) return null
            val masked = mask(v4, bits, 4) ?: return null
            return "${formatIpv4(masked)}/$bits"
        }

        val v6 = parseIpv6Bytes(address)
        if (v6 != null) {
            if (bits !in 0..128) return null
            val masked = mask(v6, bits, 16) ?: return null
            return "${formatIpv6(masked)}/$bits"
        }
        return null
    }

    private fun mask(raw: ByteArray, bits: Int, byteCount: Int): ByteArray? {
        if (raw.size != byteCount) return null
        val bytes = raw.copyOf()
        for (index in 0 until byteCount) {
            val bitsBefore = index * 8
            if (bits <= bitsBefore) {
                bytes[index] = 0
            } else if (bits < bitsBefore + 8) {
                val dropped = 8 - (bits - bitsBefore)
                val mask = ((0xFF shl dropped) and 0xFF)
                bytes[index] = (bytes[index].toInt() and mask).toByte()
            }
        }
        return bytes
    }

    fun covers(wildcard: String, candidate: String): Boolean {
        val doubleBase = wildcard.dropPrefixIfPresent("**.")
        if (doubleBase != null) {
            return candidate == doubleBase || candidate.endsWith(".$doubleBase")
        }
        val singleBase = wildcard.dropPrefixIfPresent("*.")
        if (singleBase != null) {
            return candidate.endsWith(".$singleBase")
        }
        return false
    }

    fun message(error: SplitRuleHostError): String {
        return when (error) {
            SplitRuleHostError.NotAscii -> "Use the ASCII form of the name."
            SplitRuleHostError.BadName -> "Enter a host name like example.com."
            SplitRuleHostError.BadWildcard -> "A wildcard needs a name after it, like *.example.com."
            SplitRuleHostError.BadRange -> "Enter an IP range like 10.0.0.0/8."
            SplitRuleHostError.Duplicate -> "Already in this rule."
            is SplitRuleHostError.Covered -> "Already covered by ${error.by} in this rule."
        }
    }

    private fun parseIpv4Bytes(s: String): ByteArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part.startsWith('0')) return null
            // toIntOrNull alone takes a sign ("+1")
            if (!part.all { it in '0'..'9' }) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }

    private fun formatIpv4(bytes: ByteArray): String =
        "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"

    // toIntOrNull(16) alone takes a sign: "-1" would store as ffff
    private fun isHexWord(part: String): Boolean =
        part.length in 1..4 && part.all { it in '0'..'9' || it in 'a'..'f' }

    private fun parseIpv6Bytes(s: String): ByteArray? {
        if (s.isEmpty()) return null
        if (s.startsWith(':') && !s.startsWith("::")) return null
        if (s.endsWith(':') && !s.endsWith("::")) return null

        val doubleColonCount = s.split("::").size - 1
        if (doubleColonCount > 1) return null

        val lastColon = s.lastIndexOf(':')
        val ipv4Part = if (lastColon >= 0 && s.substring(lastColon + 1).contains('.')) {
            s.substring(lastColon + 1)
        } else null

        val ipv4Bytes = if (ipv4Part != null) {
            parseIpv4Bytes(ipv4Part) ?: return null
        } else null

        // in "::1.2.3.4" the "::" ends at lastColon, so cutting there would
        // leave a lone ":"; keep the whole "::"
        val hexString = when {
            ipv4Part == null -> s
            s.substring(0, lastColon).endsWith(':') -> s.substring(0, lastColon + 1)
            else -> s.substring(0, lastColon)
        }

        val headParts: List<String>
        val tailParts: List<String>

        if (hexString.contains("::")) {
            val split = hexString.split("::")
            headParts = if (split[0].isEmpty()) emptyList() else split[0].split(':')
            tailParts = if (split.size > 1 && split[1].isNotEmpty()) split[1].split(':') else emptyList()
        } else {
            headParts = hexString.split(':')
            tailParts = emptyList()
        }

        val totalWordsNeeded = if (ipv4Bytes != null) 6 else 8
        val explicitWords = headParts.size + tailParts.size

        if (!hexString.contains("::")) {
            if (explicitWords != totalWordsNeeded) return null
        } else {
            if (explicitWords >= totalWordsNeeded) return null
        }

        val words = IntArray(totalWordsNeeded)
        var idx = 0
        for (p in headParts) {
            if (!isHexWord(p)) return null
            val w = p.toIntOrNull(16) ?: return null
            words[idx++] = w
        }
        val zeros = totalWordsNeeded - explicitWords
        idx += zeros
        for (p in tailParts) {
            if (!isHexWord(p)) return null
            val w = p.toIntOrNull(16) ?: return null
            words[idx++] = w
        }

        val bytes = ByteArray(16)
        for (i in 0 until totalWordsNeeded) {
            bytes[i * 2] = (words[i] ushr 8).toByte()
            bytes[i * 2 + 1] = (words[i] and 0xFF).toByte()
        }
        if (ipv4Bytes != null) {
            System.arraycopy(ipv4Bytes, 0, bytes, 12, 4)
        }
        return bytes
    }

    private fun formatIpv6(bytes: ByteArray): String {
        val words = IntArray(8)
        for (i in 0 until 8) {
            words[i] = ((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)
        }
        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        var curLen = 0
        for (i in 0 until 8) {
            if (words[i] == 0) {
                if (curStart < 0) {
                    curStart = i
                    curLen = 1
                } else {
                    curLen++
                }
                if (curLen > bestLen) {
                    bestLen = curLen
                    bestStart = curStart
                }
            } else {
                curStart = -1
                curLen = 0
            }
        }
        if (bestLen < 2) {
            bestStart = -1
        }

        val sb = StringBuilder()
        var i = 0
        while (i < 8) {
            if (i == bestStart) {
                sb.append("::")
                i += bestLen
                if (i >= 8) break
            } else if (i > 0 && sb.isNotEmpty() && !sb.endsWith("::")) {
                sb.append(':')
            }
            sb.append(Integer.toHexString(words[i]))
            i++
        }
        return sb.toString()
    }
}



private fun String.dropPrefixIfPresent(prefix: String): String? =
    if (startsWith(prefix)) substring(prefix.length) else null
