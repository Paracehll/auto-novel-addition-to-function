package infra.web.repository

import infra.web.WebNovelTocItem
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class WebNovelMetadataRepositoryTest : DescribeSpec({
    describe("mergeToc with 'default' chapter ID") {
        it("unstable provider: single-chapter (default) to multi-chapter") {
            val localToc = listOf(
                WebNovelTocItem(titleJp = "TitleJp", titleZh = "TitleZh", chapterId = "default")
            )
            val remoteToc = listOf(
                WebNovelTocItem(titleJp = "Chapter 1", titleZh = null, chapterId = "1"),
                WebNovelTocItem(titleJp = "Chapter 2", titleZh = null, chapterId = "2")
            )

            val merged = mergeToc(remoteToc = remoteToc, localToc = localToc, isIdUnstable = true)

            merged.reviewReason shouldBe null
            merged.hasChanged shouldBe true
        }

        it("stable provider: single-chapter (default) to multi-chapter") {
            val localToc = listOf(
                WebNovelTocItem(titleJp = "TitleJp", titleZh = "TitleZh", chapterId = "default")
            )
            val remoteToc = listOf(
                WebNovelTocItem(titleJp = "Chapter 1", titleZh = null, chapterId = "1"),
                WebNovelTocItem(titleJp = "Chapter 2", titleZh = null, chapterId = "2")
            )

            val merged = mergeToc(remoteToc = remoteToc, localToc = localToc, isIdUnstable = false)

            merged.reviewReason shouldBe null
            merged.hasChanged shouldBe true
        }
    }
})
