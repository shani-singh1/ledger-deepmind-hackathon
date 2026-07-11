package com.khataagent.core.util

/**
 * Lightweight phonetic keying + similarity for romanized Indian names spoken aloud
 * (Ramesh/Rmesh, Sita/Seeta, Lakshmi/Laxmi). Shared by :data (customer lookup) and
 * :validate (ambiguity detection) so both agree on what "sounds the same" means.
 *
 * Not a full Soundex — tuned for the vowel-drop / transliteration variance we actually see.
 */
object Phonetic {

    /** A collapse key: lowercase, common transliteration folds, then drop most vowels. */
    fun key(name: String): String {
        if (name.isBlank()) return ""
        var s = name.trim().lowercase()
        // transliteration folds
        s = s.replace("ph", "f")
            .replace("gh", "g")
            .replace("bh", "b")
            .replace("dh", "d")
            .replace("th", "t")
            .replace("kh", "k")
            .replace("ck", "k")
            .replace("sh", "s")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace("aa", "a")
            .replace("x", "ks")
            .replace("z", "j")
            .replace("w", "v")
            .replace("y", "i")
        // keep first letter, drop remaining vowels, collapse dup consonants
        val first = s.first()
        val rest = s.drop(1).filter { it in 'a'..'z' && it !in "aeiou" }
        val collapsed = StringBuilder().append(first)
        for (c in rest) if (collapsed.isEmpty() || collapsed.last() != c) collapsed.append(c)
        return collapsed.toString()
    }

    /** true if two names share a phonetic key. */
    fun sounds(a: String, b: String): Boolean = key(a) == key(b) && key(a).isNotEmpty()

    /** 0.0–1.0 similarity: 1.0 exact key match, else normalized edit distance of the keys. */
    fun similarity(a: String, b: String): Double {
        val ka = key(a); val kb = key(b)
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        if (ka == kb) return 1.0
        val dist = levenshtein(ka, kb)
        val maxLen = maxOf(ka.length, kb.length)
        return (1.0 - dist.toDouble() / maxLen).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
