package external

import react.ComponentClass
import react.Props
import react.RBuilder
import react.RefObject
import kotlin.js.Promise

external interface IGoogleReCaptchaProps : Props {
    var sitekey: String
    var onChange: (Any) -> Unit
    var theme: String?
    var type: String?
    var tabindex: Int?
    var onExpired: () -> Unit
    var onErrored: () -> Unit
    var stoken: String?
    var hl: String?
    var size: String?
    var badge: String?
}

@JsModule("react-google-recaptcha")
@JsNonModule
external object ReCAPTCHA {
    val default: ComponentClass<IGoogleReCaptchaProps>

    fun execute(): String
    fun executeAsync(): Promise<String>
    fun reset()
}

fun RBuilder.recaptcha(captchaRef: RefObject<ReCAPTCHA>) {
    ReCAPTCHA.default {
        attrs.sitekey = "6LdN6pYmAAAAAGaDiZSZLX9lLuoOfPhZSjHgaYdK"
        attrs.size = "invisible"
        ref = captchaRef
    }
}
