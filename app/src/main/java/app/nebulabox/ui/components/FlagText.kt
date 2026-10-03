package app.nebulabox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebulabox.R

private const val IRAN_UNICODE_FLAG = "\uD83C\uDDEE\uD83C\uDDF7"
private const val INLINE_IR_FLAG_ID = "lion_sun_flag"

@Composable
fun FlagText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    flagSizeSp: TextUnit = 16.sp,
) {
    if (!text.contains(IRAN_UNICODE_FLAG)) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val annotated = remember(text) {
        buildAnnotatedString {
            val parts = text.split(IRAN_UNICODE_FLAG)
            parts.forEachIndexed { index, part ->
                append(part)
                if (index < parts.lastIndex) {
                    appendInlineContent(INLINE_IR_FLAG_ID, IRAN_UNICODE_FLAG)
                }
            }
        }
    }

    val inlineContent = remember(flagSizeSp) {
        mapOf(
            INLINE_IR_FLAG_ID to InlineTextContent(
                Placeholder(
                    width = flagSizeSp * 1.15f,
                    height = flagSizeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_flag_lion_sun),
                    contentDescription = "Iran",
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
    )
}

@Composable
fun CountryFlagIcon(
    countryCode: String,
    flagEmoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val isIran = countryCode.equals("IR", ignoreCase = true) || flagEmoji == IRAN_UNICODE_FLAG
    if (isIran) {
        Image(
            painter = painterResource(id = R.drawable.ic_flag_lion_sun),
            contentDescription = "Iran",
            modifier = modifier.size(size),
        )
    } else if (flagEmoji.isNotBlank()) {
        Text(
            text = flagEmoji,
            style = LocalTextStyle.current.copy(fontSize = (size.value - 2f).sp),
            modifier = modifier,
        )
    }
}
