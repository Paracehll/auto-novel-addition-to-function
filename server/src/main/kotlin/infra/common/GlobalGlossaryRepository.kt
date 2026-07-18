package infra.common

import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections.exclude
import com.mongodb.client.model.Updates.addToSet
import com.mongodb.client.model.Updates.pull
import com.mongodb.client.model.Updates.set
import com.mongodb.client.model.Updates.combine
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

    suspend fun getById(id: ObjectId): GlobalGlossary? {
        return collection.find(eq(GlobalGlossary::id.field(), id)).firstOrNull()
    }

    suspend fun getByIds(ids: List<ObjectId>): List<GlobalGlossary> {
        if (ids.isEmpty()) return emptyList()
        return collection.find(`in`(GlobalGlossary::id.field(), ids)).toList()
    }

    suspend fun create(name: String, content: Map<String, String>, tag: List<String> = emptyList(), by: List<ObjectId> = emptyList()): GlobalGlossary {
        val diff = computeGlossaryDiff(emptyMap(), content)
        val initialRecord = GlobalGlossaryRecord(
            date = Clock.System.now(),
            diff = diff,
            by = by
        )
        val gg = GlobalGlossary(
            id = ObjectId(),
            name = name,
            content = content,
            termsCount = content.size,
            used = emptyList(),
            update = Clock.System.now(),
            tag = tag,
            record = listOf(initialRecord),
            version = 1L
        )
        collection.insertOne(gg)
        return gg
    }

    suspend fun update(id: ObjectId, name: String, content: Map<String, String>, tag: List<String>? = null, used: List<ObjectId>? = null, by: List<ObjectId> = emptyList()): GlobalGlossary {
        val old = getById(id) ?: throw NoSuchElementException("Global glossary not found")
        val isContentChanged = old.content != content
        val newRecord = if (isContentChanged) {
            val diff = computeGlossaryDiff(old.content, content)
            val recordItem = GlobalGlossaryRecord(
                date = Clock.System.now(),
                diff = diff,
                by = by
            )
            old.record + recordItem
        } else {
            old.record
        }
        val newVersion = if (isContentChanged) old.version + 1 else old.version

        val updated = GlobalGlossary(
            id = old.id,
            name = name,
            content = content,
            termsCount = content.size,
            used = used ?: old.used,
            update = Clock.System.now(),
            tag = tag ?: old.tag,
            record = newRecord,
            version = newVersion
        )

        collection.replaceOne(eq(GlobalGlossary::id.field(), id), updated)
        return updated
    }

    suspend fun delete(id: ObjectId) {
        collection.deleteOne(eq(GlobalGlossary::id.field(), id))
    }

    suspend fun updateRecords(id: ObjectId, record: List<GlobalGlossaryRecord>) {
        val old = getById(id) ?: return
        val newVersion = old.version + 1
        collection.updateOne(
            eq(GlobalGlossary::id.field(), id),
            combine(
                set(GlobalGlossary::record.field(), record),
                set(GlobalGlossary::version.field(), newVersion)
            )
        )
    }

    suspend fun updateUsed(id: ObjectId, novelId: ObjectId, add: Boolean) {
        val update = if (add) {
            addToSet(GlobalGlossary::used.field(), novelId)
        } else {
            pull(GlobalGlossary::used.field(), novelId)
        }
        collection.updateOne(
            eq(GlobalGlossary::id.field(), id),
            update
        )
    }

    private fun computeGlossaryDiff(oldContent: Map<String, String>, newContent: Map<String, String>): Map<String, GlobalGlossaryDiffItem> {
        val diff = mutableMapOf<String, GlobalGlossaryDiffItem>()
        for ((key, oldValue) in oldContent) {
            val newValue = newContent[key]
            if (newValue == null) {
                diff[key] = GlobalGlossaryDiffItem(old = oldValue, new = null)
            } else if (oldValue != newValue) {
                diff[key] = GlobalGlossaryDiffItem(old = oldValue, new = newValue)
            }
        }
        for ((key, newValue) in newContent) {
            if (!oldContent.containsKey(key)) {
                diff[key] = GlobalGlossaryDiffItem(old = null, new = newValue)
            }
        }
        return diff
    }
}
