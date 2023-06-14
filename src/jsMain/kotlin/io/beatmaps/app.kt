package io.beatmaps

import external.ReactDatesInit
import io.beatmaps.index.HomePage
import io.beatmaps.maps.MapPage
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.modlog.ModLog
import io.beatmaps.modreview.ModReview
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.EditPlaylist
import io.beatmaps.playlist.Playlist
import io.beatmaps.playlist.PlaylistFeed
import io.beatmaps.upload.UploadPage
import io.beatmaps.user.PickUsernamePage
import io.beatmaps.user.ProfilePage
import io.beatmaps.user.ResetPage
import io.beatmaps.user.UserList
import io.beatmaps.user.alerts.AlertsPage
import io.beatmaps.user.authorizePage
import io.beatmaps.user.forgotPage
import io.beatmaps.user.loginPage
import io.beatmaps.user.signupPage
import kotlinx.browser.document
import kotlinx.browser.window
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createContext
import react.dom.div
import react.dom.render
import react.router.Routes
import react.router.dom.BrowserRouter
import react.useContext

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

external interface UserData {
    val userId: Int
    val admin: Boolean
    val curator: Boolean
    val suspended: Boolean
}

val globalContext = createContext<UserData?>(null)

object Config {
    const val apibase = "/api"
}

fun main() {
    ReactDatesInit // This actually needs to be referenced I guess
    window.onload = {
        document.getElementById("root")?.let { root ->
            render(root) {
                globalContext.Provider {
                    attrs.value = document.getElementById("user-data")?.let {
                        JSON.parse<UserData>(it.textContent ?: "")
                    }
                    child(App::class) { }
                }
            }
        }
    }
}

const val dateFormat = "YYYY-MM-DD"

class App : RComponent<Props, State>() {
    override fun componentDidMount() {
        viewportMinWidthPolyfill()
    }

    override fun RBuilder.render() {
        BrowserRouter {
            Routes {
                bsroute("/", klazz = HomePage::class)
                bsroute("/beatsaver/:mapKey", klazz = MapPage::class) {
                    beatsaver = true
                }
                bsroute("/maps/:mapKey", klazz = MapPage::class) {
                    beatsaver = false
                }
                bsroute("/upload", klazz = UploadPage::class)
                bsroute("/profile") {
                    withRouter(ProfilePage::class) {
                        userData = useContext(globalContext)
                    }
                }
                bsroute("/profile/:userId") {
                    withRouter(ProfilePage::class) {
                        userData = useContext(globalContext)
                    }
                }
                bsroute("/alerts", klazz = AlertsPage::class)
                bsroute("/playlists", klazz = PlaylistFeed::class)
                bsroute("/playlists/new", klazz = EditPlaylist::class)
                bsroute("/playlists/:id", klazz = Playlist::class)
                bsroute("/playlists/:id/edit", klazz = EditPlaylist::class)
                bsroute("/test") {
                    recentTestplays { }
                }
                bsroute("/modlog") {
                    withRouter(ModLog::class) {
                        userData = useContext(globalContext)
                    }
                }
                bsroute("/modreview") {
                    withRouter(ModReview::class) {
                        userData = useContext(globalContext)
                    }
                }
                bsroute("/policy/dmca", replaceHomelink = false) {
                    div {}
                }
                bsroute("/policy/tos", replaceHomelink = false) {
                    div {}
                }
                bsroute("/policy/privacy", replaceHomelink = false) {
                    div {}
                }
                bsroute("/mappers", klazz = UserList::class)
                bsroute("/login") {
                    loginPage { }
                }
                bsroute("/oauth2/authorize") {
                    authorizePage { }
                }
                bsroute("/register") {
                    signupPage { }
                }
                bsroute("/forgot") {
                    forgotPage { }
                }
                bsroute("/reset/:jwt", klazz = ResetPage::class)
                bsroute("/username", klazz = PickUsernamePage::class)
                bsroute("*") {
                    notFound { }
                }
            }
        }
    }
}
