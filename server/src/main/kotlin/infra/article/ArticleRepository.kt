package infra.article

import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Field
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.Sorts.descending
import com.mongodb.client.model.Updates.*
import infra.*
import infra.common.Page
import infra.common.emptyPage
import infra.user.UserDbModel
import infra.user.UserOutline
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

class ArticleRepository(
    mongo: MongoClient,
    private val redis: RedisClient,
) {
    private val articleCollection =
        mongo.database.getCollection<ArticleDbModel>(
            MongoCollectionNames.ARTICLE,
        )

    suspend fun listArticle(
        page: Int,
        pageSize: Int,
        category: ArticleCategory,
    ): Page<ArticleListItem> {
        return searchArticle(
            page = page,
            pageSize = pageSize,
            category = category,
        )
    }

    suspend fun searchArticle(
        page: Int,
        pageSize: Int,
        category: ArticleCategory? = null,
        authorId: String? = null,
        authorIds: List<String>? = null,
        query: String? = null,
        fuzzyTitle: Boolean = true,
        startAt: kotlinx.datetime.Instant? = null,
        endAt: kotlinx.datetime.Instant? = null,
        minViews: Int? = null,
        maxViews: Int? = null,
        minComments: Int? = null,
        maxComments: Int? = null,
        sort: ArticleListSort = ArticleListSort.Default,
        sortDesc: Boolean = true,
    ): Page<ArticleListItem> {
        @Serializable
        data class PageModel(
            val total: Int = 0,
            val items: List<ArticleListItem>,
        )

        val filters = buildList {
            category?.let { add(eq(ArticleDbModel::category.field(), it)) }
            authorId?.let { add(eq(ArticleDbModel::user.field(), ObjectId(it))) }
            authorIds?.let { add(`in`(ArticleDbModel::user.field(), it.map { ObjectId(it) })) }
            query?.let { q ->
                if (fuzzyTitle) {
                    add(regex(ArticleDbModel::title.field(), q, "i"))
                } else {
                    val variants = q.split("\u0000")
                    if (variants.size > 1) {
                        add(`in`(ArticleDbModel::title.field(), variants))
                    } else {
                        add(eq(ArticleDbModel::title.field(), q))
                    }
                }
            }
            startAt?.let { add(gte(ArticleDbModel::createAt.field(), it)) }
            endAt?.let { add(lte(ArticleDbModel::createAt.field(), it)) }
            minViews?.let { add(gte(ArticleDbModel::numViews.field(), it)) }
            maxViews?.let { add(lte(ArticleDbModel::numViews.field(), it)) }
            minComments?.let { add(gte(ArticleDbModel::numComments.field(), it)) }
            maxComments?.let { add(lte(ArticleDbModel::numComments.field(), it)) }
        }

        val matchStage = match(if (filters.isEmpty()) org.bson.Document() else and(filters))

        val sortStage = when (sort) {
            ArticleListSort.Default -> sort(
                if (sortDesc) {
                    descending(
                        ArticleDbModel::pinned.field(),
                        ArticleDbModel::changeAt.field(),
                    )
                } else {
                    ascending(
                        ArticleDbModel::pinned.field(),
                        ArticleDbModel::changeAt.field(),
                    )
                }
            )
            ArticleListSort.CreateAt -> sort(
                if (sortDesc) descending(ArticleDbModel::createAt.field())
                else ascending(ArticleDbModel::createAt.field())
            )
            ArticleListSort.Views -> sort(
                if (sortDesc) descending(ArticleDbModel::numViews.field())
                else ascending(ArticleDbModel::numViews.field())
            )
            ArticleListSort.Comments -> sort(
                if (sortDesc) descending(ArticleDbModel::numComments.field())
                else ascending(ArticleDbModel::numComments.field())
            )
        }

        val doc = articleCollection
            .aggregate<PageModel>(
                matchStage,
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        sortStage,
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            /* from = */ MongoCollectionNames.USER,
                            /* localField = */ ArticleDbModel::user.field(),
                            /* foreignField = */ UserDbModel::id.field(),
                            /* as = */ ArticleListItem::user.field(),
                        ),
                        unwind(ArticleListItem::user.fieldPath()),
                        project(
                            fields(
                                computed(
                                    ArticleListItem::id.field(),
                                    toString(ArticleDbModel::id.field()),
                                ),
                                include(
                                    ArticleListItem::title.field(),
                                    ArticleListItem::category.field(),
                                    ArticleListItem::locked.field(),
                                    ArticleListItem::pinned.field(),
                                    ArticleListItem::hidden.field(),
                                    ArticleListItem::numViews.field(),
                                    ArticleListItem::numComments.field(),
                                    ArticleListItem::user.field() + "." + UserOutline::username.field(),
                                    ArticleListItem::createAt.field(),
                                    ArticleListItem::updateAt.field(),
                                )
                            )
                        ),
                    )
                ),
                project(
                    fields(
                        computed(PageModel::total.field(), arrayElemAt("count.count", 0)),
                        include(PageModel::items.field())
                    )
                ),
            )
            .firstOrNull()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                items = doc.items,
                total = doc.total.toLong(),
                pageSize = pageSize,
            )
        }
    }

    suspend fun getArticle(
        id: String,
    ): Article? {
        return articleCollection
            .aggregate<Article>(
                match(eq(ArticleDbModel::id.field(), ObjectId(id))),
                lookup(
                    /* from = */ MongoCollectionNames.USER,
                    /* localField = */ ArticleDbModel::user.field(),
                    /* foreignField = */ UserDbModel::id.field(),
                    /* as = */ Article::user.field(),
                ),
                unwind(Article::user.fieldPath()),
                addFields(
                    Field(
                        Article::id.field(),
                        toString(ArticleDbModel::id.field()),
                    ),
                )
            )
            .firstOrNull()
    }

    suspend fun isArticleCreateBy(
        id: String,
        userId: String,
    ): Boolean =
        articleCollection
            .countDocuments(
                and(
                    eq(ArticleDbModel::id.field(), ObjectId(id)),
                    eq(ArticleDbModel::user.field(), ObjectId(userId))
                ),
                CountOptions().limit(1),
            ) > 0

    suspend fun deleteArticle(
        id: String,
    ): Boolean =
        articleCollection
            .deleteOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
            )
            .run { deletedCount > 0 }

    suspend fun createArticle(
        title: String,
        content: String,
        category: ArticleCategory,
        userId: String,
    ): ObjectId {
        val now = Clock.System.now()
        return articleCollection
            .insertOne(
                ArticleDbModel(
                    id = ObjectId(),
                    title = title,
                    content = content,
                    category = category,
                    locked = false,
                    pinned = false,
                    numViews = 0,
                    numComments = 0,
                    user = ObjectId(userId),
                    createAt = now,
                    updateAt = now,
                    changeAt = now,
                )
            )
            .run { insertedId!!.asObjectId().value }
    }

    suspend fun increaseNumViews(
        userIdOrIp: String,
        id: String,
    ) = redis.withRateLimit("article:${userIdOrIp}:${id}") {
        articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                inc(ArticleDbModel::numViews.field(), 1),
            )
    }

    suspend fun increaseNumComments(
        id: String,
    ) =
        articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                combine(
                    inc(ArticleDbModel::numComments.field(), 1),
                    set(ArticleDbModel::changeAt.field(), Clock.System.now()),
                ),
            )

    suspend fun updateTitleAndContent(
        id: String,
        title: String,
        content: String,
        category: ArticleCategory,
    ): Boolean {
        val now = Clock.System.now()
        return articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                combine(
                    set(ArticleDbModel::title.field(), title),
                    set(ArticleDbModel::content.field(), content),
                    set(ArticleDbModel::category.field(), category),
                    set(ArticleDbModel::updateAt.field(), now),
                    set(ArticleDbModel::changeAt.field(), now),
                )
            )
            .run { matchedCount > 0 }
    }

    suspend fun updateArticlePinned(
        id: String,
        pinned: Boolean,
    ): Boolean =
        articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                set(ArticleDbModel::pinned.field(), pinned),
            )
            .run { matchedCount > 0 }

    suspend fun updateArticleLocked(
        id: String,
        locked: Boolean,
    ): Boolean =
        articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                set(ArticleDbModel::locked.field(), locked),
            )
            .run { matchedCount > 0 }

    suspend fun updateArticleHidden(
        id: String,
        hidden: Boolean,
    ): Boolean =
        articleCollection
            .updateOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
                set(ArticleDbModel::hidden.field(), hidden),
            )
            .run { matchedCount > 0 }
}