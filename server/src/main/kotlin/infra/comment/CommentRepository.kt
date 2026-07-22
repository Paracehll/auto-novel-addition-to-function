package infra.comment

import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.Sorts.descending
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import infra.*
import infra.common.Page
import infra.common.emptyPage
import infra.user.UserDbModel
import infra.user.UserOutline
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class CommentRepository(
    mongo: MongoClient,
) {
    private val commentCollection =
        mongo.database.getCollection<CommentDbModel>(
            MongoCollectionNames.COMMENT,
        )

    suspend fun getBatchReplies(
        site: String,
        parentIds: List<ObjectId>,
        replyLimit: Int,
    ): Map<String, List<Comment>> {
        if (parentIds.isEmpty()) return emptyMap()

        @Serializable
        data class ParentReplies(
            @SerialName("_id") val parentId: String,
            val replies: List<Comment>,
        )

        val pipeline = listOf(
            match(
                and(
                    eq(CommentDbModel::site.field(), site),
                    `in`(CommentDbModel::parent.field(), parentIds),
                )
            ),
            sort(ascending(CommentDbModel::id.field())),
            lookup(
                /* from = */ MongoCollectionNames.USER,
                /* localField = */ CommentDbModel::user.field(),
                /* foreignField = */ UserDbModel::id.field(),
                /* as = */ "userOutline",
            ),
            unwind("\$userOutline"),
            project(
                fields(
                    computed(Comment::id.field(), toString(CommentDbModel::id.field())),
                    computed("parent", toString(CommentDbModel::parent.field())),
                    include(
                        Comment::site.field(),
                        Comment::content.field(),
                        Comment::hidden.field(),
                        Comment::numReplies.field(),
                        Comment::createAt.field(),
                    ),
                    computed(Comment::user.field() + "." + UserOutline::username.field(), "\$userOutline.username")
                )
            ),
            Document(
                "\$group",
                Document("_id", "\$parent")
                    .append("replies", Document("\$push", "\$\$ROOT"))
            ),
            project(
                fields(
                    computed("replies", Document("\$slice", listOf("\$replies", replyLimit)))
                )
            )
        )

        val list = commentCollection.aggregate<ParentReplies>(pipeline).toList()
        return list.associate { it.parentId to it.replies }
    }

    suspend fun listComment(
        site: String,
        parent: String?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): Page<Comment> {
        @Serializable
        data class PageModel(
            val total: Int = 0,
            val items: List<Comment>,
        )

        val doc = commentCollection
            .aggregate<PageModel>(
                match(
                    and(
                        eq(CommentDbModel::site.field(), site),
                        eq(CommentDbModel::parent.field(), parent?.let { ObjectId(it) }),
                    ),
                ),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        sort(
                            if (reverse) descending(CommentDbModel::id.field())
                            else ascending(CommentDbModel::id.field()),
                        ),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            /* from = */ MongoCollectionNames.USER,
                            /* localField = */ CommentDbModel::user.field(),
                            /* foreignField = */ UserDbModel::id.field(),
                            /* as = */ Comment::user.field(),
                        ),
                        unwind(Comment::user.fieldPath()),
                        project(
                            fields(
                                computed(
                                    Comment::id.field(),
                                    toString(CommentDbModel::id.field()),
                                ),
                                include(
                                    Comment::site.field(),
                                    Comment::content.field(),
                                    Comment::hidden.field(),
                                    Comment::numReplies.field(),
                                    Comment::user.field() + "." + UserOutline::username.field(),
                                    Comment::createAt.field(),
                                )
                            )
                        )
                    )
                ),
                project(
                    fields(
                        computed(PageModel::total.field(), arrayElemAt("count.count", 0)),
                        include(PageModel::items.field()),
                    )
                )
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

    suspend fun isCommentCanRevoke(
        id: String,
        userId: String,
    ): Boolean =
        commentCollection
            .countDocuments(
                and(
                    eq(CommentDbModel::id.field(), ObjectId(id)),
                    eq(CommentDbModel::user.field(), ObjectId(userId)),
                    gt(CommentDbModel::createAt.field(), Clock.System.now() - 1.days),
                ),
                CountOptions().limit(1),
            ) > 0

    suspend fun deleteComment(
        id: String,
    ): Boolean = commentCollection
        .deleteMany(
            or(
                eq(CommentDbModel::id.field(), ObjectId(id)),
                eq(CommentDbModel::parent.field(), ObjectId(id)),
            )
        )
        .run { deletedCount > 0 }

    suspend fun deleteCommentBySite(
        site: String,
    ) {
        commentCollection
            .deleteMany(
                eq(CommentDbModel::site.field(), site),
            )
    }

    suspend fun createComment(
        site: String,
        parent: String?,
        user: String,
        content: String,
    ): ObjectId =
        commentCollection
            .insertOne(
                CommentDbModel(
                    id = ObjectId(),
                    site = site,
                    content = content,
                    numReplies = 0,
                    parent = parent?.let { ObjectId(it) },
                    user = ObjectId(user),
                    createAt = Clock.System.now(),
                )
            )
            .run { insertedId!!.asObjectId().value }

    suspend fun increaseNumReplies(
        id: String,
    ): Boolean =
        commentCollection
            .updateOne(
                eq(CommentDbModel::id.field(), ObjectId(id)),
                inc(CommentDbModel::numReplies.field(), 1),
            )
            .run { matchedCount > 0 }

    suspend fun updateCommentHidden(
        id: String,
        hidden: Boolean,
    ): Boolean =
        commentCollection
            .updateOne(
                eq(CommentDbModel::id.field(), ObjectId(id)),
                set(CommentDbModel::hidden.field(), hidden),
            )
            .run { matchedCount > 0 }
}