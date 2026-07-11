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
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

import kotlinx.datetime.Instant

@Serializable
private data class UserOutlineDbModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val username: String,
)

@Serializable
private data class ArticleMinimalDbModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val hidden: Boolean = false,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: ObjectId,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
    @Contextual val changeAt: Instant,
)

class ArticleRepository(
    mongo: MongoClient,
    private val redis: RedisClient,
) {
    private val articleCollection =
        mongo.database.getCollection<ArticleDbModel>(
            MongoCollectionNames.ARTICLE,
        )

    private val articleMinimalCollection =
        mongo.database.getCollection<ArticleMinimalDbModel>(
            MongoCollectionNames.ARTICLE,
        )

    private val userOutlineCollection =
        mongo.database.getCollection<UserOutlineDbModel>(
            MongoCollectionNames.USER,
        )

    @Volatile
    private var cachedAuthors: List<String>? = null

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

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val d = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) d[i][0] = i
        for (j in 0..s2.length) d[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
            }
        }
        return d[s1.length][s2.length]
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val len = maxOf(s1.length, s2.length)
        if (len == 0) return 1.0
        return 1.0 - levenshteinDistance(s1.lowercase(), s2.lowercase()).toDouble() / len
    }

    private suspend fun mapArticlesToListItemPage(
        pagedItems: List<ArticleMinimalDbModel>,
        totalCount: Long,
        pageSize: Int,
    ): Page<ArticleListItem> {
        val userIds = pagedItems.map { item -> item.user }.distinct()
        val usersMap = if (userIds.isEmpty()) {
            emptyMap<ObjectId, String>()
        } else {
            userOutlineCollection.find(`in`(UserOutlineDbModel::id.field(), userIds))
                .toList()
                .associate { item: UserOutlineDbModel -> item.id to item.username }
        }

        val items = pagedItems.map { item ->
            ArticleListItem(
                id = item.id.toHexString(),
                title = item.title,
                category = item.category,
                locked = item.locked,
                pinned = item.pinned,
                hidden = item.hidden,
                numViews = item.numViews,
                numComments = item.numComments,
                user = UserOutline(username = usersMap[item.user] ?: ""),
                createAt = item.createAt,
                updateAt = item.updateAt,
            )
        }

        return Page(
            items = items,
            total = totalCount,
            pageSize = pageSize,
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
        val filters = buildList {
            category?.let { add(eq(ArticleDbModel::category.field(), it)) }
            authorId?.let { add(eq(ArticleDbModel::user.field(), ObjectId(it))) }
            authorIds?.let { add(`in`(ArticleDbModel::user.field(), it.map { ObjectId(it) })) }
            query?.let { q ->
                if (fuzzyTitle) {
                    // Fuzzy title search: handled via in-memory similarity comparison.
                    // No MongoDB title filter here so we fetch all relevant category/date posts.
                } else {
                    val variants = q.split("\u0000")
                    val titleRegexes = variants.map { regex(ArticleDbModel::title.field(), it, "i") }
                    add(or(titleRegexes))
                }
            }
            startAt?.let { add(gte(ArticleDbModel::createAt.field(), it)) }
            endAt?.let { add(lte(ArticleDbModel::createAt.field(), it)) }
            minViews?.let { add(gte(ArticleDbModel::numViews.field(), it)) }
            maxViews?.let { add(lte(ArticleDbModel::numViews.field(), it)) }
            minComments?.let { add(gte(ArticleDbModel::numComments.field(), it)) }
            maxComments?.let { add(lte(ArticleDbModel::numComments.field(), it)) }
        }

        val filter = if (filters.isEmpty()) org.bson.Document() else and(filters)

        val isFuzzySearch = query != null && fuzzyTitle

        if (!isFuzzySearch) {
            val totalCount = articleCollection.countDocuments(filter)
            if (totalCount == 0L) {
                return emptyPage()
            }

            val mongoSort = when (sort) {
                ArticleListSort.Default -> {
                    if (sortDesc) {
                        descending(
                            ArticleMinimalDbModel::pinned.field(),
                            ArticleMinimalDbModel::changeAt.field(),
                        )
                    } else {
                        ascending(
                            ArticleMinimalDbModel::pinned.field(),
                            ArticleMinimalDbModel::changeAt.field(),
                        )
                    }
                }
                ArticleListSort.CreateAt -> {
                    if (sortDesc) descending(ArticleMinimalDbModel::createAt.field())
                    else ascending(ArticleMinimalDbModel::createAt.field())
                }
                ArticleListSort.Views -> {
                    if (sortDesc) descending(ArticleMinimalDbModel::numViews.field())
                    else ascending(ArticleMinimalDbModel::numViews.field())
                }
                ArticleListSort.Comments -> {
                    if (sortDesc) descending(ArticleMinimalDbModel::numComments.field())
                    else ascending(ArticleMinimalDbModel::numComments.field())
                }
            }

            val pagedItems = articleMinimalCollection
                .find(filter)
                .projection(exclude("content"))
                .sort(mongoSort)
                .skip(page * pageSize)
                .limit(pageSize)
                .toList()

            return mapArticlesToListItemPage(pagedItems, totalCount, pageSize)
        } else {
            val allItems = articleMinimalCollection
                .find(filter)
                .projection(exclude("content"))
                .toList()

            val queryVariants = query!!.split("\u0000")
            val sortedWithSim = allItems.map { item ->
                val maxSim = queryVariants.maxOf { variant ->
                    calculateSimilarity(item.title, variant)
                }
                item to maxSim
            }
            .filter { it.second > 0.1 }
            .sortedWith { a, b ->
                var cmp = b.second.compareTo(a.second)
                if (cmp == 0) {
                    cmp = b.first.pinned.compareTo(a.first.pinned)
                    if (cmp == 0) {
                        cmp = when (sort) {
                            ArticleListSort.Default -> {
                                val timeCmp = b.first.updateAt.compareTo(a.first.updateAt)
                                if (sortDesc) timeCmp else -timeCmp
                            }
                            ArticleListSort.CreateAt -> {
                                val timeCmp = b.first.createAt.compareTo(a.first.createAt)
                                if (sortDesc) timeCmp else -timeCmp
                            }
                            ArticleListSort.Views -> {
                                val viewCmp = b.first.numViews.compareTo(a.first.numViews)
                                if (sortDesc) viewCmp else -viewCmp
                            }
                            ArticleListSort.Comments -> {
                                val commentCmp = b.first.numComments.compareTo(a.first.numComments)
                                if (sortDesc) commentCmp else -commentCmp
                            }
                        }
                    }
                }
                cmp
            }
            .map { it.first }

            val totalCount = sortedWithSim.size.toLong()
            val pagedItems = sortedWithSim.drop(page * pageSize).take(pageSize)

            return mapArticlesToListItemPage(pagedItems, totalCount, pageSize)
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
    ): Boolean {
        val isDeleted = articleCollection
            .deleteOne(
                eq(ArticleDbModel::id.field(), ObjectId(id)),
            )
            .run { deletedCount > 0 }
        if (isDeleted) {
            cachedAuthors = null
        }
        return isDeleted
    }

    suspend fun createArticle(
        title: String,
        content: String,
        category: ArticleCategory,
        userId: String,
    ): ObjectId {
        val now = Clock.System.now()
        val newId = articleCollection
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
        cachedAuthors = null
        return newId
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

    suspend fun getAllArticleAuthors(): List<String> {
        val cached = cachedAuthors
        if (cached != null) {
            return cached
        }

        @Serializable
        data class AuthorModel(val username: String)
        val authors = articleCollection
            .aggregate<AuthorModel>(
                group("\$" + ArticleDbModel::user.field()),
                lookup(
                    /* from = */ MongoCollectionNames.USER,
                    /* localField = */ "_id",
                    /* foreignField = */ UserDbModel::id.field(),
                    /* as = */ "user",
                ),
                unwind("\$user"),
                project(
                    fields(
                        computed("username", "\$user." + UserDbModel::username.field()),
                        excludeId(),
                    )
                )
            )
            .toList()
            .map { it.username }

        cachedAuthors = authors
        return authors
    }
}