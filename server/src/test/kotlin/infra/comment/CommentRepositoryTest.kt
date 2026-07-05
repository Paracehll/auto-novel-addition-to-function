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
        describe("CommentRepository.countComment") {
            val site = "test-site"
            val collection = mongo.database.getCollection<CommentDbModel>(MongoCollectionNames.COMMENT)

            it("should count all comments for a site when parent is null") {
                collection.drop()
                commentRepo.createComment(site, null, "user1", "content1")
                val parentId = commentRepo.createComment(site, null, "user1", "content2")
                commentRepo.createComment(site, parentId.toHexString(), "user2", "reply1")

                commentRepo.countComment(site, null, true) shouldBe 3
            }

            it("should count only replies when parent is specified") {
                collection.drop()
                val parentId1 = commentRepo.createComment(site, null, "user1", "content1")
                val parentId2 = commentRepo.createComment(site, null, "user1", "content2")
                commentRepo.createComment(site, parentId1.toHexString(), "user2", "reply1")
                commentRepo.createComment(site, parentId1.toHexString(), "user2", "reply2")
                commentRepo.countComment(site, parentId1.toHexString(), true) shouldBe 2

                commentRepo.createComment(site, parentId2.toHexString(), "user2", "reply3")
                commentRepo.countComment(site, parentId2.toHexString(), true) shouldBe 1
            }

            it("should exclude hidden comments when includeHidden is false") {
                collection.drop()
                commentRepo.createComment(site, null, "user1", "content1")
                val hiddenId = commentRepo.createComment(site, null, "user1", "hidden content")
                commentRepo.updateCommentHidden(hiddenId.toHexString(), true)

                commentRepo.countComment(site, null, true) shouldBe 2
                commentRepo.countComment(site, null, false) shouldBe 1
            }
        }
    }
}
