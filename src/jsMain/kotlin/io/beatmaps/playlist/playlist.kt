package io.beatmaps.playlist

import external.Axios
import external.DragAndDrop.DragDropContext
import external.draggable
import external.droppable
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetailWithOrder
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.api.PlaylistPage
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.beatmapInfo
import io.beatmaps.index.modal
import io.beatmaps.setPageTitle
import io.beatmaps.upload.UploadRequestConfig
import io.beatmaps.util.textToContent
import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.classes
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.Audio
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.xhr.FormData
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.img
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.ref
import react.setState
import kotlin.math.ceil

external interface PlaylistProps : WithRouterProps

external interface PlaylistState : State {
    var loading: Boolean?
    var playlist: PlaylistFull?
    var maps: List<MapDetailWithOrder>?
}

var CommonAttributeGroupFacade.onTransitionEndFunction: (Event) -> Unit
    get() = throw UnsupportedOperationException("You can't read variable onTransitionEnd")
    set(newValue) {
        consumer.onTagEvent(this, "ontransitionend", newValue)
    }

class Playlist : RComponent<PlaylistProps, PlaylistState>() {
    private val modalRef = createRef<ModalComponent>()
    private val reasonRef = createRef<HTMLTextAreaElement>()
    private val itemsPerPage = 100

    override fun componentDidMount() {
        setPageTitle("Playlist")

        setState {
            maps = listOf()
        }

        loadPage()
    }

    private fun loadPage(page: Int? = 0) {
        if (state.loading == true)
            return

        val id = props.params["id"]
        setState {
            loading = true
        }

        Axios.get<PlaylistPage>(
            "${Config.apibase}/playlists/id/$id/$page",
            generateConfig<String, PlaylistPage>()
        ).then {
            setPageTitle("Playlist - ${it.data.playlist?.name}")
            setState {
                loading = false
                playlist = it.data.playlist
                it.data.maps?.let { newMaps ->
                    maps = maps?.plus(newMaps)?.sortedBy { m -> m.order }
                }
            }
            if ((it.data.maps?.size ?: 0) >= itemsPerPage) {
                loadPage((page ?: 0) + 1)
            }
        }.catch {
            props.history.push("/")
        }
    }

    private fun updateOrder(mapId: String, order: Float) {
        val id = props.params["id"]
        Axios.post<String>(
            "${Config.apibase}/playlists/id/$id/add",
            PlaylistMapRequest(mapId, true, order),
            generateConfig<PlaylistMapRequest, String>()
        )
    }

    private fun reorderMaps(start: Int, end: Int) {
        if (start == end) {
            return
        }

        state.maps?.let { localMaps ->
            setState {
                maps = localMaps.toMutableList().also { mutable ->
                    val elem = mutable.removeAt(start)

                    val previousOrder = if (end <= 0) 0f else mutable[end - 1].order
                    val nextOrder = if (end > 0 && end >= mutable.size - 1) mutable[end - 1].order + 2 else mutable[end].order

                    val midOrder = (previousOrder + nextOrder) / 2
                    val nextWholeOrder = ceil(previousOrder)
                    val previousIsWhole = nextWholeOrder == previousOrder

                    val newOrder = if (nextOrder - previousOrder > 1) {
                        if (previousIsWhole) {
                            previousOrder + 1 // 1, 4 -> 2
                        } else {
                            nextWholeOrder // 1.1, 4 -> 2
                        }
                    } else if (nextWholeOrder != ceil(nextOrder) && !previousIsWhole) {
                        nextWholeOrder // 1.6, 2.1 -> 2
                    } else {
                        midOrder // 1, 2 -> 1.5
                    }

                    mutable.add(end, elem.copy(order = newOrder))
                    updateOrder(elem.map.id, newOrder)
                }
            }
        }
    }

    private fun delete() {
        val id = props.params["id"]
        setState {
            loading = true
        }

        val data = FormData()
        data.append("deleted", "true")
        data.append("reason", reasonRef.current?.value ?: "")

        Axios.post<dynamic>(
            "${Config.apibase}/playlists/id/$id/edit", data,
            UploadRequestConfig { }
        ).then { r ->
            if (r.status == 200) {
                props.history.push(state.playlist?.owner?.profileLink("playlists") ?: "/")
            } else {
                setState {
                    loading = false
                }
            }
        }.catch {
            setState {
                loading = false
            }
        }
    }

    private fun curate(playlistId: Int, curated: Boolean = true) {
        Axios.post<PlaylistFull>("${Config.apibase}/playlists/curate", CurateMap(playlistId, curated), generateConfig<CurateMap, PlaylistFull>()).then({
            setState {
                playlist = it.data
            }
        }) { }
    }

    private val audio = Audio().also {
        it.volume = 0.4
    }

    override fun componentWillUnmount() {
        audio.pause()
    }

    override fun RBuilder.render() {
        modal {
            ref = modalRef
        }
        globalContext.Consumer { userData ->
            div("row mt-3") {
                div("playlist-info col-lg-4") {
                    state.playlist?.let { pl ->
                        if (pl.deletedAt != null) {
                            div("alert alert-danger text-center") {
                                +"DELETED"
                            }
                        } else if (pl.type != EPlaylistType.System && (state.playlist?.owner?.id == userData?.userId || userData?.admin == true)) {
                            div("btn-group") {
                                routeLink("/playlists/${pl.playlistId}/edit", className = "btn btn-primary") {
                                    +"Edit"
                                }
                                a("#", classes = "btn btn-danger") {
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        modalRef.current?.showDialog(
                                            ModalData(
                                                "Delete playlist",
                                                bodyCallback = {
                                                    p {
                                                        +"Are you sure? This action cannot be reversed."
                                                    }
                                                    if (userData?.admin == true) {
                                                        p {
                                                            +"Reason for action:"
                                                        }
                                                        textarea(classes = "form-control") {
                                                            ref = reasonRef
                                                        }
                                                    }
                                                },
                                                buttons = listOf(ModalButton("YES, DELETE", "danger", ::delete), ModalButton("Cancel"))
                                            )
                                        )
                                    }
                                    +"Delete"
                                }
                            }
                        }
                        if (pl.type != EPlaylistType.System && pl.deletedAt == null && userData?.curator == true) {
                            div("break") {}
                            div("btn-group") {
                                a("#", classes = "btn " + if (pl.curatedAt == null) "btn-green" else "btn-expert") {
                                    val text = ((if (pl.curatedAt == null) "" else "Un-") + "Curate")
                                    attrs.title = text
                                    attrs.attributes["aria-label"] = text
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        curate(pl.playlistId, pl.curatedAt == null)
                                    }
                                    +text
                                }
                            }
                        }
                        div("list-group") {
                            img("Cover", pl.playlistImage512 ?: pl.playlistImage) { }
                            div("list-group-item d-flex justify-content-between") {
                                +"Name"
                                span("text-truncate ms-4") {
                                    +pl.name
                                }
                            }
                            routeLink(pl.owner.profileLink(), className = "list-group-item d-flex justify-content-between") {
                                +"Created by"
                                span("text-truncate ms-4") {
                                    attrs.title = pl.owner.name
                                    +pl.owner.name
                                }
                            }
                            pl.curator?.let { curator ->
                                div("list-group-item d-flex justify-content-between") {
                                    +"Curated by"
                                    span("text-truncate ms-4") {
                                        +curator.name
                                    }
                                }
                            }
                            div("list-group-item d-flex justify-content-between") {
                                +"Maps"
                                span("text-truncate ms-4") {
                                    +(state.maps?.size ?: 0).toString()
                                }
                            }
                            if (pl.description.isNotBlank()) {
                                div("list-group-item ws-normal text-break") {
                                    textToContent(pl.description)
                                }
                            }
                        }
                        div("btn-group d-flex") {
                            a(pl.downloadURL, classes = "btn btn-success") {
                                +"Download"
                            }
                            a("bsplaylist://playlist/${pl.downloadURL}/beatsaver-${pl.playlistId}.bplist", classes = "btn btn-info") {
                                +"One-Click"
                            }
                        }
                        if ((state.maps?.size ?: 0) > 0) {
                            div("list-group") {
                                div("list-group-item ws-normal") {
                                    div("mb-1") {
                                        +"Mappers"
                                    }
                                    state.maps?.let { maps ->
                                        maps.map { it.map.uploader }.groupBy { it.id }.entries.map {
                                            it.value.size to it.value.first()
                                        }.sortedByDescending { it.first }.mapIndexed { idx, it ->
                                            if (idx > 0) {
                                                +", "
                                            }
                                            routeLink(it.second.profileLink()) {
                                                +it.second.name
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                div("col-lg-8") {
                    if (state.playlist?.owner?.id == userData?.userId) {
                        DragDropContext {
                            attrs.onDragEnd = {
                                it.destination?.let { dest ->
                                    reorderMaps(it.source.index, dest.index)
                                }
                            }
                            droppable("playlist") {
                                attrs.classes = setOf("playlist")
                                state.maps?.mapIndexed { idx, it ->
                                    draggable(it.map.id, idx) {
                                        attrs.classes = setOf("drag-beatmap")

                                        beatmapInfo {
                                            obj = it.map
                                            version = it.map.publishedVersion()
                                            modal = modalRef
                                            audio = this@Playlist.audio
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        div("playlist") {
                            state.maps?.map {
                                beatmapInfo {
                                    obj = it.map
                                    version = it.map.publishedVersion()
                                    modal = modalRef
                                    audio = this@Playlist.audio
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.playlist(handler: PlaylistProps.() -> Unit) =
    child(Playlist::class) {
        this.attrs(handler)
    }
