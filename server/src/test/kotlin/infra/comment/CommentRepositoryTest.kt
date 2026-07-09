package infra.comment

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import koinExtensions
import org.koin.test.KoinTest
import org.koin.test.inject
import infra.MongoClient
import infra.MongoCollectionNames

class CommentRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val mongo by inject<MongoClient>()
    private val commentRepo by inject<CommentRepository>()

    init {
        describe("countComment") {
            val site = "test-site"
            val collection = mongo.database.getCollection<CommentDbModel>(MongoCollectionNames.COMMENT)

            it("当 parent 为 null 时统计站点的所有评论") {
                collection.drop()
                commentRepo.createComment(site, null, "user1", "content1")
                val parentId = commentRepo.createComment(site, null, "user1", "content2")
                commentRepo.createComment(site, parentId.toHexString(), "user2", "reply1")

                commentRepo.countComment(site, null, true) shouldBe 3
            }

            it("当指定 parent 时仅统计回复") {
                collection.drop()
                val parentId1 = commentRepo.createComment(site, null, "user1", "content1")
                val parentId2 = commentRepo.createComment(site, null, "user1", "content2")
                commentRepo.createComment(site, parentId1.toHexString(), "user2", "reply1")
                commentRepo.createComment(site, parentId1.toHexString(), "user2", "reply2")
                commentRepo.countComment(site, parentId1.toHexString(), true) shouldBe 2

                commentRepo.createComment(site, parentId2.toHexString(), "user2", "reply3")
                commentRepo.countComment(site, parentId2.toHexString(), true) shouldBe 1
            }

            it("当 includeHidden 为 false 时排除隐藏评论") {
                collection.drop()
                commentRepo.createComment(site, null, "user1", "content1")
                val hiddenId = commentRepo.createComment(site, null, "user1", "hidden content")
                commentRepo.updateCommentHidden(hiddenId.toHexString(), true)

                commentRepo.countComment(site, null, true) shouldBe 2
                commentRepo.countComment(site, null, false) shouldBe 1
            }

            it("当 unique 为 true 时统计唯一用户数") {
                collection.drop()
                commentRepo.createComment(site, null, "user1", "content1")
                commentRepo.createComment(site, null, "user1", "content2")
                commentRepo.createComment(site, null, "user2", "content3")

                commentRepo.countComment(site, null, true, unique = true) shouldBe 2
                commentRepo.countComment(site, null, true, unique = false) shouldBe 3
            }

            it("当 reply 为 false 时排除回复") {
                collection.drop()
                val parentId = commentRepo.createComment(site, null, "user1", "content1")
                commentRepo.createComment(site, parentId.toHexString(), "user2", "reply1")

                commentRepo.countComment(site, null, true, reply = true) shouldBe 2
                commentRepo.countComment(site, null, true, reply = false) shouldBe 1
            }
        }
    }
}
