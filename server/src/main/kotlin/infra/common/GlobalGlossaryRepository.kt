package infra.common

import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections.exclude
import com.mongodb.client.model.Updates.addToSet
import com.mongodb.client.model.Updates.pull
import com.mongodb.client.model.Updates.set
import infra.MongoClient
import infra.MongoCollectionNames
import infra.field
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import kotlinx.datetime.Clock

class GlobalGlossaryRepository(mongo: MongoClient) {
    private val collection = mongo.database.getCollection<GlobalGlossary>(
        MongoCollectionNames.GLOBAL_GLOSSARY
    )

    suspend fun list(): List<GlobalGlossary> {
        return collection.find()
            .projection(exclude(GlobalGlossary::record.field(), GlobalGlossary::content.field()))
            .toList()
    }

    suspend fun getByUid(uid: String): GlobalGlossary? {
        return collection.find(eq(GlobalGlossary::uid.field(), uid)).firstOrNull()
    }

    suspend fun getByUids(uids: List<String>): List<GlobalGlossary> {
        if (uids.isEmpty()) return emptyList()
        return collection.find(`in`(GlobalGlossary::uid.field(), uids)).toList()
    }

    suspend fun create(uid: String, name: String, content: Map<String, String>, tag: List<String> = emptyList()): GlobalGlossary {
        val existing = getByUid(uid)
        if (existing != null) {
            throw IllegalArgumentException("UID already exists")
        }
        val diff = computeGlossaryDiff(emptyMap(), content)
        val initialRecord = GlobalGlossaryRecord(
            date = Clock.System.now(),
            diff = diff
        )
        val gg = GlobalGlossary(
            id = ObjectId(),
            uid = uid,
            name = name,
            content = content,
            termsCount = content.size,
            used = emptyList(),
            update = Clock.System.now(),
            tag = tag,
            record = listOf(initialRecord)
        )
        collection.insertOne(gg)
        return gg
    }

    suspend fun update(uid: String, name: String, content: Map<String, String>, tag: List<String>? = null, used: List<ObjectId>? = null): GlobalGlossary {
        val old = getByUid(uid) ?: throw NoSuchElementException("Global glossary not found")
        val isContentChanged = old.content != content
        val newRecord = if (isContentChanged) {
            val diff = computeGlossaryDiff(old.content, content)
            val recordItem = GlobalGlossaryRecord(
                date = Clock.System.now(),
                diff = diff
            )
            old.record + recordItem
        } else {
            old.record
        }

        val updated = GlobalGlossary(
            id = old.id,
            uid = old.uid,
            name = name,
            content = content,
            termsCount = content.size,
            used = used ?: old.used,
            update = Clock.System.now(),
            tag = tag ?: old.tag,
            record = newRecord
        )

        collection.replaceOne(eq(GlobalGlossary::uid.field(), uid), updated)
        return updated
    }

    suspend fun delete(uid: String) {
        collection.deleteOne(eq(GlobalGlossary::uid.field(), uid))
    }

    suspend fun updateRecords(uid: String, record: List<GlobalGlossaryRecord>) {
        collection.updateOne(
            eq(GlobalGlossary::uid.field(), uid),
            set(GlobalGlossary::record.field(), record)
        )
    }

    suspend fun updateUsed(uid: String, novelId: ObjectId, add: Boolean) {
        val update = if (add) {
            addToSet(GlobalGlossary::used.field(), novelId)
        } else {
            pull(GlobalGlossary::used.field(), novelId)
        }
        collection.updateOne(
            eq(GlobalGlossary::uid.field(), uid),
            update
        )
    }

    private fun computeGlossaryDiff(oldContent: Map<String, String>, newContent: Map<String, String>): Map<String, GlobalGlossaryDiffItem> {
        val diff = mutableMapOf<String, GlobalGlossaryDiffItem>()
        for ((key, oldValue) in oldContent) {
            val newValue = newContent[key]
            if (newValue == null) {
                diff[key] = GlobalGlossaryDiffItem(type = "delete", old = oldValue)
            } else if (oldValue != newValue) {
                diff[key] = GlobalGlossaryDiffItem(type = "modify", old = oldValue, new = newValue)
            }
        }
        for ((key, newValue) in newContent) {
            if (!oldContent.containsKey(key)) {
                diff[key] = GlobalGlossaryDiffItem(type = "add", new = newValue)
            }
        }
        return diff
    }
}
