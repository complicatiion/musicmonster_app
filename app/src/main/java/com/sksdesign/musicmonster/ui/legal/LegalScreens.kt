package com.sksdesign.musicmonster.ui.legal

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sksdesign.musicmonster.R
import com.sksdesign.musicmonster.LocalSurfaceAppearance
import com.sksdesign.musicmonster.SceneGlassDialog
import com.sksdesign.musicmonster.sceneGlassPanel

/** This revision is derived from the bundled EULA + Privacy Policy + Legal Notice files.
 * Changing any of those documents requires updating the revision and re-confirming consent.
 */
object LegalDocuments {
    const val REVISION = "2026-10-85d9c1bfa1895a75"

    fun read(context: Context, section: LegalSection): String =
        runCatching { context.assets.open("legal/${section.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() } }
            .getOrElse { "Unable to load ${section.title}. Please reinstall MusicMonster before accepting the license." }
}

enum class LegalSection(val title: String, val assetName: String) {
    EULA("EULA", "eula.md"),
    PRIVACY("Privacy Policy", "privacy_policy.md"),
    NOTICE("Legal Notice", "legal_notice.md"),
    COPYRIGHT("Copyright", "copyright.md")
}


@Composable
fun LicenseConsentScreen(isSaving: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    val documentAvailable = remember(context) {
        LegalSection.entries.all { LegalDocuments.read(context, it).startsWith("Unable to load").not() }
    }
    BackHandler { onDecline() }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C4DFF), onPrimary = Color.White,
            background = Color.Black, surface = Color.Black, onSurface = Color.White
        )
    ) {
        val overlayInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                 .background(Color.Black.copy(alpha =
                    if (LocalSurfaceAppearance.current.surfaceStyle == "Glass") .28f else .72f))
                .clickable(interactionSource = overlayInteraction, indication = null) { }
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(.96f).widthIn(max = 980.dp).fillMaxHeight(.92f).sceneGlassPanel(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = com.sksdesign.musicmonster.overlayPanelColor())
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Music Monster\nInformation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 31.sp, color = Color.White)
                    LegalDocumentsPanel(context = context, modifier = Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDecline, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Decline", maxLines = 1)
                        }
                        Button(
                            onClick = onAccept,
                            enabled = !isSaving && documentAvailable,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Accept", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** An independent information dialog; opening it never modifies persisted acceptance. */
@Composable
fun LegalInformationDialog(amoledBlack: Boolean, lightMode: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    SceneGlassDialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(.96f).widthIn(max = 980.dp).fillMaxHeight(.90f).sceneGlassPanel(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = com.sksdesign.musicmonster.overlayPanelColor())
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Music Monster\nInformation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 31.sp, color = Color.White)
                    LegalDocumentsPanel(context = context, modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally), shape = RoundedCornerShape(22.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Close", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentsPanel(context: Context, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)
    ) {
        LegalSection.entries.forEach { section ->
            item(key = section.name + "_heading") {
                Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
            }
            val paragraphs = LegalDocuments.read(context, section).lineSequence().toList()
            items(paragraphs) { raw ->
                MarkdownParagraph(raw)
            }
            item(key = section.name + "_divider") { Spacer(Modifier.height(14.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .24f)); Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun LegalTabs(available: List<LegalSection>, selected: LegalSection, onSelected: (LegalSection) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
        items(available) { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelected(section) },
                label = { Text(section.title, maxLines = 1) },
                modifier = if (section == available.first()) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun DocumentPanel(section: LegalSection, markdown: String, modifier: Modifier) {
    val paragraphs = remember(markdown) { markdown.lineSequence().toList() }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)
    ) {
        item {
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        items(paragraphs) { raw -> MarkdownParagraph(raw) }
    }
}

@Composable
private fun MarkdownParagraph(raw: String) {
    when {
        raw.isBlank() -> Spacer(Modifier.height(5.dp))
        raw.matches(Regex("^\\s*[-_]{3,}\\s*$")) -> HorizontalDivider(Modifier.padding(vertical = 7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))
        raw.startsWith("### ") -> MarkdownLine(raw.drop(4), MaterialTheme.typography.titleSmall, FontWeight.SemiBold)
        raw.startsWith("## ") -> MarkdownLine(raw.drop(3), MaterialTheme.typography.titleMedium, FontWeight.Bold)
        raw.startsWith("# ") -> MarkdownLine(raw.drop(2), MaterialTheme.typography.titleLarge, FontWeight.Bold)
        raw.trimStart().startsWith("* ") || raw.trimStart().startsWith("- ") -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                MarkdownLine(raw.trimStart().drop(2), MaterialTheme.typography.bodyMedium, FontWeight.Normal)
            }
        }
        raw.startsWith("> ") -> MarkdownLine(raw.drop(2), MaterialTheme.typography.bodyMedium, FontWeight.Medium)
        else -> MarkdownLine(raw.trimEnd(), MaterialTheme.typography.bodyMedium, FontWeight.Normal)
    }
}

@Composable
private fun MarkdownLine(content: String, style: androidx.compose.ui.text.TextStyle, weight: FontWeight) {
    val uriHandler = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary
    val rich = remember(content, accent) { inlineMarkdown(content.trim(), accent) }
    ClickableText(
        text = rich,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = weight, lineHeight = (style.fontSize.value + 5).sp),
        onClick = { offset ->
            rich.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { url ->
                runCatching { uriHandler.openUri(url.item) }
            }
        }
    )
}

/** Small offline Markdown renderer for the supplied legal files: headings, lists, rules, bold, italics and links. */
internal fun inlineMarkdown(raw: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val token = Regex("\\*\\*(.+?)\\*\\*|\\[([^\\]]+)]\\((https?://[^)]+)\\)|`([^`]+)`|(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    var cursor = 0
    token.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(groups[1]); pop()
            }
            groups[2].isNotEmpty() -> {
                pushStringAnnotation("URL", groups[3])
                pushStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
                append(groups[2]); pop(); pop()
            }
            groups[4].isNotEmpty() -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Medium)); append(groups[4]); pop()
            }
            groups[5].isNotEmpty() -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(groups[5]); pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}
