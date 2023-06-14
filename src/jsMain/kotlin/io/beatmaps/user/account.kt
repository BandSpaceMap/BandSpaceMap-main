package io.beatmaps.user

import external.Axios
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.AccountRequest
import io.beatmaps.api.AccountType
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.upload.UploadRequestConfig
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onSubmitFunction
import kotlinx.html.role
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h5
import react.dom.hr
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.span
import react.dom.textarea
import react.dom.value
import react.setState
import kotlin.collections.set

external interface AccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

external interface AccountComponentState : State {
    var loading: Boolean?
    var userLoading: Boolean?
    var uploading: Boolean?
    var errors: List<String>
    var usernameErrors: List<String>
    var username: String
    var descriptionErrors: List<String>
    var description: String
}

class AccountComponent : RComponent<AccountComponentProps, AccountComponentState>() {
    private val avatarRef = createRef<HTMLInputElement>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    private val usernameRef = createRef<HTMLInputElement>()
    private val descriptionRef = createRef<HTMLTextAreaElement>()

    private val currpassRef = createRef<HTMLInputElement>()
    private val passwordRef = createRef<HTMLInputElement>()
    private val password2Ref = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        setState {
            loading = false
            uploading = false
            userLoading = false
            errors = listOf()
            usernameErrors = listOf()
            username = props.userDetail.name
            descriptionErrors = listOf()
            description = props.userDetail.description ?: ""
        }
    }

    override fun RBuilder.render() {
        // Having 2 forms confuses password managers, only the password section needs to invoke them
        div(classes = "user-form") {
            h5("mt-5") {
                +"Account details"
            }
            hr("mt-2") {}
            if (props.userDetail.type != AccountType.DISCORD) {
                div("mb-3 row") {
                    label("col-sm-2 col-form-label") {
                        attrs.reactFor = "email"
                        +"Email"
                    }
                    div("col-sm-10") {
                        input(InputType.text, classes = "form-control-plaintext") {
                            key = "email"
                            attrs.id = "email"
                            attrs.disabled = true
                            attrs.value = props.userDetail.email ?: ""
                        }
                    }
                }
            }
            div("invalid-feedback") {
                val error = state.usernameErrors.firstOrNull()
                if (error != null) {
                    attrs.jsStyle {
                        display = "block"
                    }
                    +error
                }
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "name"
                    +"Username"
                }
                input(InputType.text, classes = "form-control") {
                    key = "username"
                    attrs.id = "name"
                    attrs.value = state.username
                    attrs.onChangeFunction = {
                        setState {
                            username = usernameRef.current?.value ?: ""
                        }
                    }
                    ref = usernameRef
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            if (props.userDetail.name == state.username) {
                                setState {
                                    usernameErrors = listOf("That's already your username!")
                                }
                            } else {
                                setState {
                                    userLoading = true
                                }

                                Axios.post<ActionResponse>(
                                    "${Config.apibase}/users/username",
                                    AccountDetailReq(state.username),
                                    generateConfig<AccountDetailReq, ActionResponse>()
                                ).then {
                                    if (it.data.success) {
                                        props.onUpdate()
                                        setState {
                                            userLoading = false
                                        }
                                    } else {
                                        setState {
                                            usernameErrors = it.data.errors
                                            userLoading = false
                                        }
                                    }
                                }.catch {
                                    // Cancelled request
                                    setState {
                                        userLoading = false
                                    }
                                }
                            }
                        }
                        attrs.disabled = state.userLoading == true
                        +"Change username"
                    }
                }
            }
            div("invalid-feedback") {
                val error = state.descriptionErrors.firstOrNull()
                if (error != null) {
                    attrs.jsStyle {
                        display = "block"
                    }
                    +error
                }
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "description"
                    +"Description"
                }
                textarea(classes = "form-control") {
                    key = "description"
                    attrs.id = "description"
                    attrs.value = state.description
                    attrs.rows = "5"
                    attrs.maxLength = "500"
                    attrs.onChangeFunction = {
                        setState {
                            description = descriptionRef.current?.value ?: ""
                        }
                    }
                    ref = descriptionRef
                }
                span("badge badge-" + if (state.description.length > 480) "danger" else "dark") {
                    attrs.id = "count_message"
                    +"${state.description.length} / 500"
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            if (props.userDetail.description != state.description) {
                                setState {
                                    userLoading = true
                                }

                                Axios.post<ActionResponse>(
                                    "${Config.apibase}/users/description",
                                    AccountDetailReq(state.description),
                                    generateConfig<AccountDetailReq, ActionResponse>()
                                ).then {
                                    if (it.data.success) {
                                        props.onUpdate()
                                        setState {
                                            userLoading = false
                                        }
                                    } else {
                                        setState {
                                            descriptionErrors = it.data.errors
                                            userLoading = false
                                        }
                                    }
                                }.catch {
                                    // Cancelled request
                                    setState {
                                        userLoading = false
                                    }
                                }
                            }
                        }
                        attrs.disabled = state.userLoading == true
                        +"Change description"
                    }
                }
            }
        }
        if (props.userDetail.type == AccountType.SIMPLE) {
            div(classes = "user-form") {
                h5("mt-5") {
                    +"Avatar"
                }
                hr("mt-2") {}
                div("mb-3") {
                    input(InputType.file) {
                        key = "avatar"
                        ref = avatarRef
                        attrs.hidden = state.uploading == true
                    }
                    div("d-grid") {
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            attrs.hidden = state.uploading == true
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()

                                val file = avatarRef.current?.files?.let { it[0] }
                                if (file != null) {
                                    setState {
                                        uploading = true
                                    }

                                    val data = FormData()
                                    data.asDynamic().append("file", file)

                                    Axios.post<dynamic>(
                                        "/avatar", data,
                                        UploadRequestConfig { progress ->
                                            val v = ((progress.loaded * 100f) / progress.total).toInt()
                                            progressBarInnerRef.current?.style?.width = "$v%"
                                        }
                                    ).then { r ->
                                        if (r.status == 200) {
                                            props.onUpdate()
                                            setState {
                                                uploading = false
                                            }
                                        } else {
                                            setState {
                                                uploading = false
                                                avatarRef.current?.value = ""
                                            }
                                        }
                                    }.catch {
                                        setState {
                                            uploading = false
                                            avatarRef.current?.value = ""
                                        }
                                    }
                                }
                            }
                            +"Upload"
                        }
                    }
                    div("progress") {
                        attrs.hidden = state.uploading == false
                        div("progress-bar progress-bar-striped progress-bar-animated bg-info") {
                            attrs.role = "progressbar"
                            ref = progressBarInnerRef
                        }
                    }
                }
            }
        }
        if (props.userDetail.type != AccountType.DISCORD) {
            form(classes = "user-form", action = "/profile", method = FormMethod.post) {
                attrs.onSubmitFunction = { ev ->
                    ev.preventDefault()

                    Axios.post<ActionResponse>(
                        "${Config.apibase}/users/me",
                        AccountRequest(
                            currpassRef.current?.value ?: "",
                            passwordRef.current?.value ?: "",
                            password2Ref.current?.value ?: "",
                        ),
                        generateConfig<AccountRequest, ActionResponse>()
                    ).then {
                        if (it.data.success) {
                            currpassRef.current?.value = ""
                            passwordRef.current?.value = ""
                            password2Ref.current?.value = ""
                            setState {
                                errors = listOf("Password updated")
                                loading = false
                            }
                        } else {
                            setState {
                                errors = it.data.errors
                                loading = false
                            }
                        }
                    }.catch {
                        // Cancelled request
                        setState {
                            loading = false
                        }
                    }
                }
                h5("mt-5") {
                    +"Password"
                }
                hr("mt-2") {}
                input(InputType.text) {
                    attrs.hidden = true
                    key = "hiddenname"
                    attrs.name = "username"
                    attrs.value = props.userDetail.name
                    attrs.attributes["autocomplete"] = "username"
                }
                div("invalid-feedback") {
                    val error = state.errors.firstOrNull()
                    if (error != null) {
                        attrs.jsStyle {
                            display = "block"
                        }
                        +error
                    }
                }
                div("mb-3") {
                    label("form-label") {
                        attrs.reactFor = "current-password"
                        +"Current Password"
                    }
                    input(InputType.password, classes = "form-control") {
                        key = "curpass"
                        ref = currpassRef
                        attrs.id = "current-password"
                        attrs.required = true
                        attrs.placeholder = "Current Password"
                        attrs.attributes["autocomplete"] = "current-password"
                    }
                }
                div("mb-3") {
                    label("form-label") {
                        attrs.reactFor = "new-password"
                        +"New Password"
                    }
                    input(InputType.password, classes = "form-control") {
                        key = "password"
                        ref = passwordRef
                        attrs.id = "new-password"
                        attrs.required = true
                        attrs.placeholder = "New Password"
                        attrs.attributes["autocomplete"] = "new-password"
                    }
                    input(InputType.password, classes = "form-control") {
                        key = "password2"
                        ref = password2Ref
                        attrs.id = "new-password2"
                        attrs.required = true
                        attrs.placeholder = "Repeat Password"
                        attrs.attributes["autocomplete"] = "new-password"
                    }
                    div("d-grid") {
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            attrs.disabled = state.loading == true
                            +"Change password"
                        }
                    }
                }
            }
        }
        if (props.userDetail.type == AccountType.DUAL) {
            form(classes = "user-form", action = "/profile/unlink-discord", method = FormMethod.post) {
                h5("mt-5") {
                    +"Unlink discord"
                }
                hr("mt-2") {}
                div("mb-3 d-grid") {
                    button(classes = "btn btn-danger", type = ButtonType.submit) {
                        attrs.disabled = state.loading == true
                        +"Unlink discord"
                    }
                }
            }
        } else if (props.userDetail.type == AccountType.SIMPLE) {
            form(classes = "user-form", action = "/profile/unlink-discord", method = FormMethod.post) {
                h5("mt-5") {
                    +"Link discord"
                }
                hr("mt-2") {}
                div("mb-3 d-grid") {
                    a("/discord-link", classes = "btn btn-info") {
                        +"Link discord"
                    }
                }
            }
        }
    }
}

fun RBuilder.account(handler: AccountComponentProps.() -> Unit) =
    child(AccountComponent::class) {
        this.attrs(handler)
    }
