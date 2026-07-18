package api

import api.plugins.*
import infra.common.GlobalGlossary
import infra.common.GlobalGlossaryDiffItem
import infra.common.GlobalGlossaryRepository
import infra.web.repository.WebNovelMetadataRepository
import infra.wenku.repository.WenkuNovelMetadataRepository
import io.ktor.http.*
import org.bson.types.ObjectId
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/global-glossary")
class GlobalGlossaryRes {
    @Resource("")
    class List(val parent: GlobalGlossaryRes)

    @Resource("/{id}")
    class Id(val parent: GlobalGlossaryRes, val id: String) {
        @Resource("/record/{index}")
        class Record(val parent: Id, val index: Int)
    }
}

@Serializable
data class GlobalGlossaryCreateBody(
    val name: String,
    val content: Map<String, String>,
    val tag: List<String> = emptyList(),
)

@Serializable
data class GlobalGlossaryUpdateBody(
    val name: String,
    val content: Map<String, String>,
    val tag: List<String> = emptyList(),
)

@Serializable
data class GlobalGlossaryRecordDto(
    val date: Long,
    val diff: Map<String, GlobalGlossaryDiffItem>,
)

@Serializable
data class GlobalGlossaryDto(
    val id: String,
    val name: String,
    val content: Map<String, String>,
    val termsCount: Int,
    val used: List<String>,
    val update: Long,
    val tag: List<String>,
    val record: List<GlobalGlossaryRecordDto>,
    val version: Long,
)

fun GlobalGlossary.asDto(usedUrls: List<String>, excludeDetails: Boolean = false) = GlobalGlossaryDto(
    id = id.toHexString(),
    name = name,
    content = if (excludeDetails) emptyMap() else content,
    termsCount = if (termsCount > 0) termsCount else content.size,
    used = usedUrls,
    update = update.epochSeconds,
    tag = tag,
    record = if (excludeDetails) emptyList() else record.map {
        GlobalGlossaryRecordDto(
            date = it.date.epochSeconds,
            diff = it.diff,
        )
    },
    version = version,
)

fun Route.routeGlobalGlossary() {
    val service by inject<GlobalGlossaryApi>()

    authenticateDb(optional = true) {
        get<GlobalGlossaryRes.List> {
            call.tryRespond {
                service.list()
            }
        }

        get<GlobalGlossaryRes.Id> { loc ->
            call.tryRespond {
                service.get(loc.id)
            }
        }
    }

    authenticateDb {
        post<GlobalGlossaryRes> {
            val user = call.user()
            val body = call.receive<GlobalGlossaryCreateBody>()
            call.tryRespond {
                service.create(user, body)
            }
        }

        put<GlobalGlossaryRes.Id> { loc ->
            val user = call.user()
            val body = call.receive<GlobalGlossaryUpdateBody>()
            call.tryRespond {
                service.update(user, loc.id, body)
            }
        }

        delete<GlobalGlossaryRes.Id> { loc ->
            val user = call.user()
            call.tryRespond {
                service.delete(user, loc.id)
            }
        }

        delete<GlobalGlossaryRes.Id.Record> { loc ->
            val user = call.user()
            call.tryRespond {
                service.deleteRecord(user, loc.parent.id, loc.index)
            }
        }
    }
}

class GlobalGlossaryApi(
    private val repo: GlobalGlossaryRepository,
    private val webNovelRepo: WebNovelMetadataRepository,
    private val wenkuNovelRepo: WenkuNovelMetadataRepository,
) {
    suspend fun list(): List<GlobalGlossaryDto> {
        return repo.list().map { it.asDto(usedUrls = it.used.map { id -> id.toHexString() }, excludeDetails = true) }
    }

    suspend fun get(id: String): GlobalGlossaryDto {
        val parsedId = try { ObjectId(id) } catch (e: Exception) { throwBadRequest("全域术语表ID格式无效: $id") }
        val gg = repo.getById(parsedId) ?: throwNotFound("无法找到ID为 $id 的全域术语表")
        val invalidRefs = mutableListOf<ObjectId>()
        val resolvedUrls = gg.used.mapNotNull { targetId ->
            val webNovel = webNovelRepo.getById(targetId)
            if (webNovel != null) {
                "/novel/${webNovel.providerId}/${webNovel.novelId}"
            } else {
                val wenkuNovel = wenkuNovelRepo.getById(targetId)
                if (wenkuNovel != null) {
                    "/wenku/${wenkuNovel.id.toHexString()}"
                } else {
                    invalidRefs.add(targetId)
                    null
                }
            }
        }
        if (invalidRefs.isNotEmpty()) {
            for (invalidRef in invalidRefs) {
                try {
                    repo.updateUsed(parsedId, invalidRef, add = false)
                } catch (e: Exception) {
                    // Log or handle error if cleanup fails
                }
            }
        }
        return gg.asDto(usedUrls = resolvedUrls)
    }

    suspend fun create(user: User, body: GlobalGlossaryCreateBody): GlobalGlossaryDto {
        user.requireNovelAccess()
        if (body.name.isBlank()) {
            throwBadRequest("名称不能为空")
        }
        return repo.create(
            name = body.name,
            content = body.content,
            tag = body.tag,
        ).asDto(emptyList())
    }

    suspend fun update(user: User, id: String, body: GlobalGlossaryUpdateBody): GlobalGlossaryDto {
        user.requireNovelAccess()
        if (body.name.isBlank()) {
            throwBadRequest("名称不能为空")
        }
        val parsedId = try { ObjectId(id) } catch (e: Exception) { throwBadRequest("无效的ID格式") }
        return repo.update(
            id = parsedId,
            name = body.name,
            content = body.content,
            tag = body.tag,
        ).asDto(emptyList())
    }

    suspend fun delete(user: User, id: String) {
        user.requireAdmin()
        val parsedId = try { ObjectId(id) } catch (e: Exception) { throwBadRequest("无效的ID格式") }
        repo.delete(parsedId)
    }

    suspend fun deleteRecord(user: User, id: String, index: Int) {
        user.requireAdmin()
        val parsedId = try { ObjectId(id) } catch (e: Exception) { throwBadRequest("无效的ID格式") }
        val gg = repo.getById(parsedId) ?: throwNotFound("全域术语表不存在")
        if (index in gg.record.indices) {
            val updatedRecords = gg.record.toMutableList()
            updatedRecords.removeAt(index)
            repo.updateRecords(parsedId, updatedRecords)
        } else {
            throwBadRequest("无效的记录索引")
        }
    }
}
