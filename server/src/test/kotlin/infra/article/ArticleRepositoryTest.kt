package infra.article

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import koinExtensions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.time.Duration.Companion.days
import infra.MongoClient

class ArticleRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val repo by inject<ArticleRepository>()
    private val mongo by inject<MongoClient>()

    init {
        val articleCollection by lazy { mongo.database.getCollection<ArticleDbModel>("article") }

        beforeTest {
            articleCollection.deleteMany(org.bson.Document())
        }

        describe("searchArticle") {
            it("應該能依 category 過濾") {
                val now = Clock.System.now()
                val user = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "A1", "C1", ArticleCategory.General, false, false, false, 0, 0, user, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "A2", "C2", ArticleCategory.Guide, false, false, false, 0, 0, user, now, now, now))

                val result = repo.searchArticle(0, 10, category = ArticleCategory.General)
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "A1"
            }

            it("應該能依 authorId 過濾") {
                val now = Clock.System.now()
                val user1 = ObjectId()
                val user2 = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "A1", "C1", ArticleCategory.General, false, false, false, 0, 0, user1, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "A2", "C2", ArticleCategory.General, false, false, false, 0, 0, user2, now, now, now))

                val result = repo.searchArticle(0, 10, authorId = user1.toHexString())
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "A1"
            }

            it("應該能依日期區間過濾") {
                val now = Clock.System.now()
                val user = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Old", "C", ArticleCategory.General, false, false, false, 0, 0, user, now - 10.days, now - 10.days, now - 10.days))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "New", "C", ArticleCategory.General, false, false, false, 0, 0, user, now, now, now))

                val result = repo.searchArticle(0, 10, startAt = now - 5.days)
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "New"

                val result2 = repo.searchArticle(0, 10, endAt = now - 5.days)
                result2.items shouldHaveSize 1
                result2.items[0].title shouldBe "Old"
            }

            it("應該能依觀看數區間過濾") {
                val now = Clock.System.now()
                val user = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Low", "C", ArticleCategory.General, false, false, false, 10, 0, user, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Mid", "C", ArticleCategory.General, false, false, false, 100, 0, user, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "High", "C", ArticleCategory.General, false, false, false, 1000, 0, user, now, now, now))

                val result = repo.searchArticle(0, 10, minViews = 50, maxViews = 500)
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "Mid"
            }

            it("應該能依評論數區間過濾") {
                val now = Clock.System.now()
                val user = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Low", "C", ArticleCategory.General, false, false, false, 0, 5, user, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "High", "C", ArticleCategory.General, false, false, false, 0, 50, user, now, now, now))

                val result = repo.searchArticle(0, 10, minComments = 10)
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "High"
            }

            it("應該能支援不同排序模式") {
                val now = Clock.System.now()
                val user = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "V10", "C", ArticleCategory.General, false, false, false, 10, 50, user, now - 1.days, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "V100", "C", ArticleCategory.General, false, false, false, 100, 5, user, now, now, now))

                val byViews = repo.searchArticle(0, 10, sort = ArticleListSort.Views)
                byViews.items[0].title shouldBe "V100"

                val byComments = repo.searchArticle(0, 10, sort = ArticleListSort.Comments)
                byComments.items[0].title shouldBe "V10"

                val byCreateAt = repo.searchArticle(0, 10, sort = ArticleListSort.CreateAt)
                byCreateAt.items[0].title shouldBe "V100"
            }

            it("多條件組合應該正確工作") {
                val now = Clock.System.now()
                val user1 = ObjectId()
                val user2 = ObjectId()
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Target", "C", ArticleCategory.General, false, false, false, 100, 0, user1, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Other User", "C", ArticleCategory.General, false, false, false, 100, 0, user2, now, now, now))
                articleCollection.insertOne(ArticleDbModel(ObjectId(), "Low Views", "C", ArticleCategory.General, false, false, false, 10, 0, user1, now, now, now))

                val result = repo.searchArticle(0, 10, authorId = user1.toHexString(), minViews = 50)
                result.items shouldHaveSize 1
                result.items[0].title shouldBe "Target"
            }
        }
    }
}
