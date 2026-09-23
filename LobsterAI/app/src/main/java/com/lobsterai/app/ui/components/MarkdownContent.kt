package com.lobsterai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val codeRegex = Regex("```([A-Za-z0-9_+.#-]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    val matches = codeRegex.findAll(markdown).toList()
    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            var cursor = 0
            matches.forEach { match ->
                if (match.range.first > cursor) {
                    RichText(markdown.substring(cursor, match.range.first))
                }
                CodeBlock(
                    language = match.groupValues.getOrNull(1).orEmpty(),
                    code = match.groupValues.getOrNull(2).orEmpty().trimEnd()
                )
                cursor = match.range.last + 1
            }
            if (cursor < markdown.length) RichText(markdown.substring(cursor))
            if (markdown.isEmpty()) RichText("")
        }
    }
}

@Composable
private fun RichText(text: String) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            val headingLevel = line.takeWhile { it == '#' }.length.takeIf { it in 1..6 && line.getOrNull(it) == ' ' }
            val content = if (headingLevel != null) line.drop(headingLevel + 1) else line
            val start = length
            append(content)
            if (headingLevel != null) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = secondary), start, length)
            }
            applyInlineMarkdown(this, start, content, primary, secondary)
            if (index != lines.lastIndex) append('\n')
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = primary)
}

private fun applyInlineMarkdown(
    builder: AnnotatedString.Builder,
    lineStart: Int,
    line: String,
    primary: Color,
    accent: Color
) {
    Regex("\\*\\*(.+?)\\*\\*").findAll(line).forEach { match ->
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Bold, color = primary),
            lineStart + match.range.first,
            lineStart + match.range.last + 1
        )
    }
    Regex("`([^`]+)`").findAll(line).forEach { match ->
        builder.addStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, color = accent),
            lineStart + match.range.first,
            lineStart + match.range.last + 1
        )
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val keywordColor = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.tertiary
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val normalColor = MaterialTheme.colorScheme.onSurface
    val annotated = buildAnnotatedString {
        append(code)
        Regex("\\b(class|fun|val|var|if|else|when|for|while|return|import|package|public|private|protected|internal|suspend|data|object|interface|override|async|await|const|let|def|function|new|try|catch|finally|throw)\\b")
            .findAll(code).forEach { addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1) }
        Regex("\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'").findAll(code).forEach {
            addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
        }
        Regex("(?m)//.*$|#.*$").findAll(code).forEach {
            addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (language.isNotBlank()) {
            Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = annotated,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = normalColor
        )
    }
}
