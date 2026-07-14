package infra.common

import infra.MongoClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import koinExtensions
import org.koin.test.KoinTest
import org.koin.test.inject

class GlobalGlossaryTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val repo by inject<GlobalGlossaryRepository>()

    init {
        describe("全域术语表 Global Glossary 测试") {
            it("完整 CUID 與版本/Diff/Used 更新驗證") {
                val uid = "test-uid-123"
                val name = "测试术语表"
                val content = mapOf("りんご" to "苹果", "ばなな" to "香蕉")

                // Ensure it's clean before starting
                repo.delete(uid)

                // 1. Create
                val created = repo.create(uid, name, content)
                created.uid shouldBe uid
                created.name shouldBe name
                created.content shouldBe content
                created.ver shouldBe 1
                created.record.size shouldBe 0

                // 2. Read (Get)
                val fetched = repo.getByUid(uid)
                fetched shouldNotBe null
                fetched!!.name shouldBe name

                // 3. Update (Content and Version/Diff change)
                val newContent = mapOf("りんご" to "林檎", "めろん" to "哈密瓜")
                val updated = repo.update(uid, name, newContent)
                updated.ver shouldBe 2
                updated.record.size shouldBe 1
                val record = updated.record[0]
                record.ver shouldBe 1
                record.diff["りんご"]?.old shouldBe "苹果"
                record.diff["りんご"]?.new shouldBe "林檎"
                record.diff["ばなな"]?.old shouldBe "香蕉"
                record.diff["ばなな"]?.new shouldBe null
                record.diff["めろん"]?.old shouldBe null
                record.diff["めろん"]?.new shouldBe "哈密瓜"

                // 4. Update Used
                repo.updateUsed(uid, "/novel/syosetu/n1234ab", true)
                val withUsed = repo.getByUid(uid)
                withUsed!!.used shouldBe listOf("/novel/syosetu/n1234ab")

                repo.updateUsed(uid, "/novel/syosetu/n1234ab", false)
                val withoutUsed = repo.getByUid(uid)
                withoutUsed!!.used.size shouldBe 0

                // 5. Delete
                repo.delete(uid)
                repo.getByUid(uid) shouldBe null
            }
        }
    }
}
