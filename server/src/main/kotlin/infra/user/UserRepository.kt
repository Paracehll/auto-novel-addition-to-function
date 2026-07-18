package infra.user

import com.mongodb.client.model.Filters.`in`
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

    suspend fun getId(username: String): String {
        val user = userCollection
            .find(eq(UserDbModel::username.field(), username))
            .firstOrNull()
        if (user != null) {
            return user.id.toHexString()
        }

        val model = UserDbModel(
            id = ObjectId(),
            username = username,
            favoredWeb = listOf(UserFavored(id = "default", title = "默认收藏夹")),
            favoredWenku = listOf(UserFavored(id = "default", title = "默认收藏夹")),
        )
        val userId = userCollection
            .insertOne(model)
            .insertedId!!.asObjectId().value
        return userId.toHexString()
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

    suspend fun getUsernamesMap(userIds: List<String>): Map<String, String> {
        val objectIds = userIds.mapNotNull {
            try { ObjectId(it) } catch (e: Exception) { null }
        }
        if (objectIds.isEmpty()) return emptyMap()
        val users = userCollection.find(`in`(UserDbModel::id.field(), objectIds)).toList()
        return users.associate { it.id.toHexString() to it.username }
    }
}