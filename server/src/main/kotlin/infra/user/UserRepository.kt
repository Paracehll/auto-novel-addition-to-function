package infra.user

import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates.set
import infra.MongoClient
import infra.MongoCollectionNames
import infra.field
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

class UserRepository(
    mongo: MongoClient,
) {
    private val userCollection =
        mongo.database.getCollection<UserDbModel>(
            MongoCollectionNames.USER,
        )

    suspend fun getIdOrNull(username: String): String? {
        return userCollection
            .find(eq(UserDbModel::username.field(), username))
            .firstOrNull()
            ?.id?.toHexString()
    }

    suspend fun getIdsByName(username: String, exact: Boolean): List<String> {
        val filter = if (exact) {
            eq(UserDbModel::username.field(), username)
        } else {
            regex(UserDbModel::username.field(), username, "i")
        }
        return userCollection
            .find(filter)
            .toList()
            .map { it.id.toHexString() }
    }

    suspend fun getId(username: String): String {
        val userId = getIdOrNull(username)
        if (userId != null) {
            return userId
        }

        val model = UserDbModel(
            id = ObjectId(),
            username = username,
            favoredWeb = listOf(UserFavored(id = "default", title = "默认收藏夹")),
            favoredWenku = listOf(UserFavored(id = "default", title = "默认收藏夹")),
        )
        val insertedUserId = userCollection
            .insertOne(model)
            .insertedId!!.asObjectId().value
        return insertedUserId.toHexString()
    }

    suspend fun isReadHistoryPaused(
        userId: String,
    ): Boolean =
        userCollection
            .countDocuments(
                and(
                    eq(UserDbModel::id.field(), ObjectId(userId)),
                    ne(UserDbModel::readHistoryPaused.field(), true),
                )
            ) == 0L

    suspend fun updateUserReadHistoryPaused(
        userId: String,
        readHistoryPause: Boolean,
    ) =
        userCollection
            .updateOne(
                eq(UserDbModel::id.field(), ObjectId(userId)),
                set(UserDbModel::readHistoryPaused.field(), readHistoryPause),
            )
}