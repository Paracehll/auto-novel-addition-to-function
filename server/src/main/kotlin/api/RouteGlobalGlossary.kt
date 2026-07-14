package api

import api.plugins.*
import infra.common.GlobalGlossary
import infra.common.GlobalGlossaryRepository
import io.ktor.http.*
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

    @Resource("/{uid}")
    class Id(val parent: GlobalGlossaryRes, val uid: String)
}

@Serializable
data class GlobalGlossaryCreateBody(
    val uid: String,
    val name: String,
    val content: Map<String, String>,
)

@Serializable
data class GlobalGlossaryUpdateBody(
    val name: String,
    val content: Map<String, String>,
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
    }
}

class GlobalGlossaryApi(
    private val repo: GlobalGlossaryRepository,
) {
    suspend fun list(): List<GlobalGlossary> {
        return repo.list()
    }

    suspend fun get(uid: String): GlobalGlossary {
        return repo.getByUid(uid) ?: throwNotFound("全域术语表不存在")
    }

    suspend fun create(user: User, body: GlobalGlossaryCreateBody): GlobalGlossary {
        user.requireNovelAccess()
        if (body.uid.isBlank() || body.name.isBlank()) {
            throwBadRequest("UID 和名称不能为空")
        }
        if (repo.getByUid(body.uid) != null) {
            throwBadRequest("UID 已存在")
        }
        return repo.create(
            uid = body.uid,
            name = body.name,
            content = body.content,
        )
    }

    suspend fun update(user: User, uid: String, body: GlobalGlossaryUpdateBody): GlobalGlossary {
        user.requireNovelAccess()
        if (body.name.isBlank()) {
            throwBadRequest("名称不能为空")
        }
        return repo.update(
            uid = uid,
            name = body.name,
            content = body.content,
        )
    }

    suspend fun delete(user: User, uid: String) {
        user.requireAdmin()
        repo.delete(uid)
    }
}
