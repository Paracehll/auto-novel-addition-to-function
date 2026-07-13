package util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus

class LevenshteinTest : DescribeSpec({
    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val (short, long) = if (s1.length < s2.length) s1 to s2 else s2 to s1

        var prevRow = IntArray(short.length + 1) { it }
        var currRow = IntArray(short.length + 1)

        for (i in 1..long.length) {
            currRow[0] = i
            for (j in 1..short.length) {
                val cost = if (long[i - 1] == short[j - 1]) 0 else 1
                currRow[j] = minOf(
                    currRow[j - 1] + 1,
                    prevRow[j] + 1,
                    prevRow[j - 1] + cost
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        return prevRow[short.length]
    }

    fun calculateSimilarity(s1: String, s2: String): Double {
        val s1Lower = s1.lowercase()
        val s2Lower = s2.lowercase()
        val len = maxOf(s1Lower.length, s2Lower.length)
        if (len == 0) return 1.0
        return 1.0 - levenshteinDistance(s1Lower, s2Lower).toDouble() / len
    }

    describe("levenshteinDistance") {
        it("相同字符串的编辑距离应为0") {
            levenshteinDistance("apple", "apple") shouldBe 0
            levenshteinDistance("", "") shouldBe 0
        }

        it("应正确处理空字符串") {
            levenshteinDistance("", "apple") shouldBe 5
            levenshteinDistance("apple", "") shouldBe 5
        }

        it("单次修改应计算出正确的距离") {
            levenshteinDistance("apple", "aple") shouldBe 1     // 删除
            levenshteinDistance("apple", "apples") shouldBe 1   // 插入
            levenshteinDistance("apple", "apply") shouldBe 1    // 替换
        }

        it("常规字符串应计算出正确的距离") {
            levenshteinDistance("kitten", "sitting") shouldBe 3
            levenshteinDistance("flaw", "lawn") shouldBe 2
        }

        it("长短字符串的输入顺序不应影响结果") {
            levenshteinDistance("kitten", "sitting") shouldBe levenshteinDistance("sitting", "kitten")
            levenshteinDistance("apple", "aple") shouldBe levenshteinDistance("aple", "apple")
        }
    }

    describe("calculateSimilarity") {
        it("忽略大小写，相同字符串的相似度应为1.0") {
            calculateSimilarity("apple", "apple") shouldBe 1.0
            calculateSimilarity("Apple", "aPPle") shouldBe 1.0
            calculateSimilarity("", "") shouldBe 1.0
        }

        it("部分匹配应计算出正确的相似度") {
            calculateSimilarity("kitten", "sitting") shouldBe (1.0 - 3.0 / 7.0)
            calculateSimilarity("apple", "aple") shouldBe (1.0 - 1.0 / 5.0)
        }

        it("完全不同的字符串相似度应为0.0") {
            calculateSimilarity("abc", "def") shouldBe 0.0
        }
    }
})
