package com.ravenl.htmlannotator.compose.ext.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.ravenl.htmlannotator.compose.ext.state.HtmlContentState


@Composable
fun BasicHtmlContent(
    html: String?,
    state: HtmlContentState,
    renderTag: @Composable ColumnScope.(annotation: AnnotatedString.Range<String>, AnnotatedString) -> Unit,
    modifier: Modifier = Modifier,
    defaultStyle: TextStyle = TextStyle.Default,
    renderDefault: @Composable ColumnScope.(AnnotatedString) -> Unit = { text ->
        BasicText(text, Modifier.fillMaxWidth(), defaultStyle)
    }
): Unit = with(state) {
    srcHtml = html

    Column(modifier) {
        resultHtml?.forEach { string ->
            val tags = state.splitTags
            var annotation: AnnotatedString.Range<String>? = null
            for (tag in tags) {
                annotation =
                    string.getStringAnnotations(tag, 0, string.length).firstOrNull()
                if (annotation != null) {
                    break
                }
            }
            if (annotation == null) {
                renderDefault(string)
            } else {
                renderTag(annotation, string)
            }
        }
    }
}