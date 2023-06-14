package external

import react.ComponentClass
import react.Context
import react.Props
import react.ReactElement

external interface DateRangePickeProps : Props {
    var startDate: Moment?
    var startDateId: String
    var endDate: Moment?
    var endDateId: String
    var onFocusChange: (String) -> Unit
    var onDatesChange: (DateRange) -> Unit
    var focusedInput: String?
    var isOutsideRange: (Moment) -> Boolean
    var renderCalendarInfo: () -> ReactElement<*>?
    var displayFormat: String
    var small: Boolean
    var numberOfMonths: Int
}

external class DateRange {
    val startDate: Moment?
    val endDate: Moment?
}

@JsModule("react-dates/initialize")
@JsNonModule
external object ReactDatesInit

@JsModule("react-dates/lib/components/DateRangePicker")
@JsNonModule
external object DateRangePicker {
    val default: ComponentClass<DateRangePickeProps>
}

@JsModule("react-dates/lib/theme/DefaultTheme")
@JsNonModule
external object ReactDatesDefaultTheme {
    val default: dynamic
}

@JsModule("react-dates/constants")
@JsNonModule
external object ReactDatesConstants {
    val START_DATE: String
    val END_DATE: String
}

@JsModule("react-with-styles/lib/WithStylesContext")
@JsNonModule
external object WithStylesContext {
    val default: Context<*>
}

@JsModule("react-with-styles/lib/ThemedStyleSheet")
@JsNonModule
external object ThemedStyleSheet {
    val default: ThemedStyleSheetObj
}

external class ThemedStyleSheetObj {
    fun registerInterface(inf: dynamic)
    fun registerTheme(theme: dynamic)
}

@JsModule("react-with-styles-interface-css")
@JsNonModule
external object ReactWithStylesCSS

@JsModule("moment")
@JsNonModule
external class Moment {
    constructor()
    constructor(d: String)
    fun add(v: Int, unit: String): Moment
    fun subtract(v: Int, unit: String): Moment
    fun format(format: String): String
    fun locale(l: String): Unit
    fun toISOString(): String
}
