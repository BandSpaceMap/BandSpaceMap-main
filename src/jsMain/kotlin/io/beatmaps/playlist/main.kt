package io.beatmaps.playlist

import io.beatmaps.WithRouterProps
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SortOrderTarget
import io.beatmaps.dateFormat
import io.beatmaps.setPageTitle
import io.beatmaps.shared.FilterCategory
import io.beatmaps.shared.FilterInfo
import io.beatmaps.shared.SearchParamGenerator
import io.beatmaps.shared.buildURL
import io.beatmaps.shared.includeIfNotNull
import io.beatmaps.shared.queryParams
import io.beatmaps.shared.search
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.State
import react.setState

external interface PlaylistFeedProps : WithRouterProps

external interface PlaylistFeedState : State {
    var searchParams: PlaylistSearchParams?
}

val playlistFilters = listOf<FilterInfo<PlaylistSearchParams>>(
    FilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
    FilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true }
)

class PlaylistFeed : RComponent<PlaylistFeedProps, PlaylistFeedState>() {
    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Playlists")
    }

    private fun fromURL() = URLSearchParams(window.location.search).let { params ->
        PlaylistSearchParams(
            params.get("q") ?: "",
            params.get("minNps")?.toFloatOrNull(),
            params.get("maxNps")?.toFloatOrNull(),
            params.get("from"),
            params.get("to"),
            null,
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance
        )
    }

    override fun componentWillUpdate(nextProps: PlaylistFeedProps, nextState: PlaylistFeedState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                nextState.searchParams = fromParams
            }
        }
    }

    private fun updateSearchParams(searchParamsLocal: PlaylistSearchParams?, row: Int?) {
        if (searchParamsLocal == null) return

        with(searchParamsLocal) {
            buildURL(
                listOfNotNull(
                    *queryParams(),
                    includeIfNotNull(curated, "curated"),
                    includeIfNotNull(verified, "verified")
                ),
                "playlists", row, state.searchParams, props.history
            )
        }

        setState {
            searchParams = searchParamsLocal
        }
    }

    override fun RBuilder.render() {
        search<PlaylistSearchParams> {
            typedState = state.searchParams
            sortOrderTarget = SortOrderTarget.Playlist
            maxNps = 16
            filters = playlistFilters
            paramsFromPage = SearchParamGenerator {
                PlaylistSearchParams(
                    inputRef.current?.value?.trim() ?: "",
                    if (state.minNps?.let { it > 0 } == true) state.minNps else null,
                    if (state.maxNps?.let { it < props.maxNps } == true) state.maxNps else null,
                    state.startDate?.format(dateFormat),
                    state.endDate?.format(dateFormat),
                    null,
                    if (isFiltered("curated")) true else null,
                    if (isFiltered("verified")) true else null,
                    state.order ?: SearchOrder.Relevance
                )
            }
            updateSearchParams = ::updateSearchParams
        }
        playlistTable {
            search = state.searchParams
            own = false
            history = props.history
            visible = true
            updateScrollIndex = {
                updateSearchParams(state.searchParams, if (it < 2) null else it)
            }
        }
    }
}

fun RBuilder.playlistFeed(handler: PlaylistFeedProps.() -> Unit) =
    child(PlaylistFeed::class) {
        this.attrs(handler)
    }
