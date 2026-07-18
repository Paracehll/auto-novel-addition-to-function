package infra.common

import infra.MongoClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import koinExtensions
import org.bson.types.ObjectId
import org.koin.test.KoinTest
import org.koin.test.inject

class GlobalGlossaryTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val repo by inject<GlobalGlossaryRepository>()

    init {
        describe("GlobalGlossary") {
            it("完整 CUID 與版本/Diff/Used 更新驗證") {
                val name = "测试术语表"
                val content = mapOf("りんご" to "苹果", "ばなな" to "香蕉")
                val tag = listOf("abc", "886")

                // 1. Create
                val userIdStr = "654321098765432109876543"
                val created = repo.create(name, content, tag, by = listOf(ObjectId(userIdStr)))
                val id = created.id
                created.name shouldBe name
                created.content shouldBe content
                created.tag shouldBe tag
                created.version shouldBe 1L
                created.record.size shouldBe 1
                val initialRecord = created.record[0]
                initialRecord.by shouldBe listOf(ObjectId(userIdStr))
                initialRecord.diff["りんご"]?.old shouldBe null
                initialRecord.diff["りんご"]?.new shouldBe "苹果"
                initialRecord.diff["ばなな"]?.old shouldBe null
                initialRecord.diff["ばなな"]?.new shouldBe "香蕉"

                // 2. Read (Get)
                val fetched = repo.getById(id)
                fetched shouldNotBe null
                fetched!!.name shouldBe name
                fetched.tag shouldBe tag
                fetched.version shouldBe 1L
                fetched.record[0].by shouldBe listOf(ObjectId(userIdStr))

                // 3. Update (Content and Version/Diff change)
                val newContent = mapOf("りんご" to "林檎", "めろん" to "哈密瓜")
                val newTag = listOf("abc", "123")
                val updated = repo.update(id, name, newContent, newTag, by = listOf(ObjectId("000000000000000000000000")))
                updated.tag shouldBe newTag
                updated.version shouldBe 2L
                updated.record.size shouldBe 2
                updated.record[0].by shouldBe listOf(ObjectId(userIdStr))
                val record = updated.record[1]
                record.by shouldBe listOf(ObjectId("000000000000000000000000"))
                record.diff["りんご"]?.old shouldBe "苹果"
                record.diff["りんご"]?.new shouldBe "林檎"
                record.diff["ばなな"]?.old shouldBe "香蕉"
                record.diff["ばなな"]?.new shouldBe null
                record.diff["めろん"]?.old shouldBe null
                record.diff["めろん"]?.new shouldBe "哈密瓜"

                // 4. Test content reconstruction at historical versions
                updated.contentAtVersion(1L) shouldBe content
                updated.contentAtVersion(2L) shouldBe newContent

                // 5. Update Used
                val dummyNovelId = ObjectId()
                repo.updateUsed(id, dummyNovelId, true)
                val withUsed = repo.getById(id)
                withUsed!!.used shouldBe listOf(dummyNovelId)

                repo.updateUsed(id, dummyNovelId, false)
                val withoutUsed = repo.getById(id)
                withoutUsed!!.used.size shouldBe 0

                // 6. Delete
                repo.delete(id)
                repo.getById(id) shouldBe null
            }

            it("仅重新排序时不增加版本号") {
                val name = "排序测试"
                val content = mapOf("A" to "1", "B" to "2")
                val created = repo.create(name, content)
                val id = created.id
                created.version shouldBe 1L
                created.record.size shouldBe 1

                // 重新排序内容 (B在前, A在后)
                val reorderedContent = mapOf("B" to "2", "A" to "1")
                val updated = repo.update(id, name, reorderedContent)

                // 版本号不应该增加，且历史记录条数也不应该增加
                updated.version shouldBe 1L
                updated.record.size shouldBe 1

                // 验证实际内容依然与传入的重新排序后的内容保持一致 (B在前, A在后)
                val fetched = repo.getById(id)
                fetched shouldNotBe null
                fetched!!.content.keys.toList() shouldBe listOf("B", "A")

                // 再次更新内容本身 (修改内容)
                val modifiedContent = mapOf("B" to "2", "A" to "3")
                val modified = repo.update(id, name, modifiedContent)

                // 版本号应该增加，且历史记录条数也应该增加
                modified.version shouldBe 2L
                modified.record.size shouldBe 2

                // 清理
                repo.delete(id)
            }
        }
    }
}
