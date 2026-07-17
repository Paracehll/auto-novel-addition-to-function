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
import java.util.UUID

@Resource("/global-glossary")
class GlobalGlossaryRes {
    @Resource("")
    class List(val parent: GlobalGlossaryRes)

    @Resource("/{uid}")
    class Id(val parent: GlobalGlossaryRes, val uid: String) {
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
    val uid: String,
    val name: String,
    val content: Map<String, String>,
    val termsCount: Int,
    val used: List<String>,
    val update: Long,
    val tag: List<String>,
    val record: List<GlobalGlossaryRecordDto>,
)

fun GlobalGlossary.asDto(usedUrls: List<String>, excludeDetails: Boolean = false) = GlobalGlossaryDto(
    id = id.toHexString(),
    uid = uid,
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
    }
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
                service.get(loc.uid)
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
                service.update(user, loc.uid, body)
            }
        }

        delete<GlobalGlossaryRes.Id> { loc ->
            val user = call.user()
            call.tryRespond {
                service.delete(user, loc.uid)
            }
        }

        delete<GlobalGlossaryRes.Id.Record> { loc ->
            val user = call.user()
            call.tryRespond {
                service.deleteRecord(user, loc.parent.uid, loc.index)
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

    suspend fun get(uid: String): GlobalGlossaryDto {
        val gg = repo.getByUid(uid) ?: throwNotFound("全域术语表不存在")
        val resolvedUrls = gg.used.mapNotNull { id ->
            val webNovel = webNovelRepo.getById(id)
            if (webNovel != null) {
                "/novel/${webNovel.providerId}/${webNovel.novelId}"
            } else {
                val wenkuNovel = wenkuNovelRepo.getById(id)
                if (wenkuNovel != null) {
                    "/wenku/${wenkuNovel.id.toHexString()}"
                } else {
                    null
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
        val generatedUid = UUID.randomUUID().toString()
        return repo.create(
            uid = generatedUid,
            name = body.name,
            content = body.content,
            tag = body.tag,
        ).asDto(emptyList())
    }

    suspend fun update(user: User, uid: String, body: GlobalGlossaryUpdateBody): GlobalGlossaryDto {
        user.requireNovelAccess()
        if (body.name.isBlank()) {
            throwBadRequest("名称不能为空")
        }
        return repo.update(
            uid = uid,
            name = body.name,
            content = body.content,
            tag = body.tag,
        ).asDto(emptyList())
    }

    suspend fun delete(user: User, uid: String) {
        user.requireAdmin()
        repo.delete(uid)
    }

    suspend fun deleteRecord(user: User, uid: String, index: Int) {
        user.requireAdmin()
        val gg = repo.getByUid(uid) ?: throwNotFound("全域术语表不存在")
        if (index in gg.record.indices) {
            val updatedRecords = gg.record.toMutableList()
            updatedRecords.removeAt(index)
            repo.updateRecords(uid, updatedRecords)
        } else {
            throwBadRequest("无效的记录索引")
        }
    }
}
