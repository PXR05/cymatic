package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ReadingBlock(
    val text: String,
    val heading: Boolean = false,
    val code: Boolean = false,
)

internal fun readingBlocks(source: String): List<ReadingBlock> = buildList {
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var fenced = false
    fun flush() {
        if (paragraph.isNotEmpty()) {
            add(ReadingBlock(paragraph.joinToString(" ")))
            paragraph.clear()
        }
    }
    source.replace("\r\n", "\n").trim().lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("```") -> {
                flush()
                if (fenced) {
                    add(ReadingBlock(code.joinToString("\n"), code = true))
                    code.clear()
                }
                fenced = !fenced
            }
            fenced -> code += raw
            line.isEmpty() || line.matches(Regex("[-=]{3,}")) -> flush()
            line.matches(Regex("#{1,6} .*")) -> {
                flush()
                add(ReadingBlock(line.trimStart('#', ' '), heading = true))
            }
            line.matches(Regex("[0-9]+\\. [A-Z][^.]{0,75}\\.")) ||
                (line.length < 100 &&
                    line.any(Char::isLetter) &&
                    line == line.uppercase() &&
                    line.none(Char::isLowerCase)) -> {
                flush()
                add(ReadingBlock(line, heading = true))
            }
            line.matches(Regex("[-*•] .*")) || line.matches(Regex("[0-9]+[.)] .*")) -> {
                flush()
                paragraph += line
            }
            line.matches(Regex("[A-Z][A-Za-z /-]{1,30}: .*")) -> {
                flush()
                paragraph += line
                flush()
            }
            isEmpty() &&
                paragraph.isEmpty() &&
                line.length < 80 &&
                !line.startsWith("Copyright", true) -> add(ReadingBlock(line, heading = true))
            else -> paragraph += line
        }
    }
    flush()
    if (code.isNotEmpty()) add(ReadingBlock(code.joinToString("\n"), code = true))
}

@Composable
fun ReadingDocument(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { readingBlocks(text) }
    SelectionContainer {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            blocks.forEach { block ->
                val rich =
                    remember(block.text) {
                        buildAnnotatedString {
                            val tokens =
                                Regex(
                                    "\\[([^]]+)]\\((https?://[^)]+)\\)|https?://[^\\s]+|\\*\\*([^*]+)\\*\\*|`([^`]+)`"
                                )
                            var offset = 0
                            tokens.findAll(block.text).forEach { match ->
                                append(block.text.substring(offset, match.range.first))
                                when {
                                    match.groupValues[2].isNotEmpty() ->
                                        withLink(LinkAnnotation.Url(match.groupValues[2])) {
                                            append(match.groupValues[1])
                                        }
                                    match.value.startsWith("http") ->
                                        withLink(LinkAnnotation.Url(match.value)) {
                                            append(match.value)
                                        }
                                    match.groupValues[3].isNotEmpty() ->
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                            append(match.groupValues[3])
                                        }
                                    else -> append(match.groupValues[4])
                                }
                                offset = match.range.last + 1
                            }
                            append(block.text.substring(offset))
                        }
                    }
                Text(
                    rich,
                    modifier = Modifier.fillMaxWidth(),
                    style =
                        (if (block.heading) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.bodyMedium)
                            .copy(lineHeight = if (block.heading) 24.sp else 20.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
