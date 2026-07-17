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
                val created = repo.create(name, content, tag)
                val id = created.id
                created.name shouldBe name
                created.content shouldBe content
                created.tag shouldBe tag
                created.version shouldBe 1L
                created.record.size shouldBe 1
                val initialRecord = created.record[0]
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

                // 3. Update (Content and Version/Diff change)
                val newContent = mapOf("りんご" to "林檎", "めろん" to "哈密瓜")
                val newTag = listOf("abc", "123")
                val updated = repo.update(id, name, newContent, newTag)
                updated.tag shouldBe newTag
                updated.version shouldBe 2L
                updated.record.size shouldBe 2
                val record = updated.record[1]
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
        }
    }
}
