package com.pxr.cymatic.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ReportRecipient = "pxr0@proton.me"
private const val ReportFileName = "cymatic-bug-report.txt"

internal class BugReportFileProvider : FileProvider()

internal suspend fun openBugReportEmail(
    context: Context,
    category: BugCategory,
    summary: String,
    details: String,
    report: String,
    includeDiagnostics: Boolean,
): Boolean {
    val cleanSummary = summary.trim().lineSequence().joinToString(" ")
    val subject = "Cymatic · ${category.label} · $cleanSummary"
    val draft =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$ReportRecipient")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ReportRecipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, report)
        }
    if (includeDiagnostics) {
        val emailPackage = draft.resolveActivity(context.packageManager)?.packageName
        if (emailPackage != null) {
            val attachmentDraft =
                Intent(Intent.ACTION_SEND).apply {
                    setPackage(emailPackage)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(ReportRecipient))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        formatBugReport(category, summary, details) +
                            "\nThe full report and diagnostics are attached.",
                    )
                    putExtra(
                        Intent.EXTRA_HTML_TEXT,
                        "<h2>Cymatic bug report</h2>" +
                            "<p><b>Category:</b> ${TextUtils.htmlEncode(category.label)}<br>" +
                            "<b>Summary:</b> ${TextUtils.htmlEncode(cleanSummary)}</p>" +
                            "<h3>What happened</h3><p>${TextUtils.htmlEncode(details.trim()).replace("\n", "<br>")}</p>" +
                            "<p>The full report and diagnostics are attached.</p>",
                    )
                }
            if (attachmentDraft.resolveActivity(context.packageManager) != null) {
                val attachment = withContext(Dispatchers.IO) { reportAttachment(context, report) }
                if (attachment != null) {
                    attachmentDraft.putExtra(Intent.EXTRA_STREAM, attachment)
                    attachmentDraft.clipData = ClipData.newRawUri(ReportFileName, attachment)
                    attachmentDraft.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context.startEmail(attachmentDraft)) return true
                }
            }
        }
    }
    return context.startEmail(draft)
}

private fun Context.startEmail(draft: Intent): Boolean =
    try {
        startActivity(draft)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

private fun reportAttachment(context: Context, report: String): Uri? = runCatching {
    val directory = File(context.cacheDir, "bug-reports")
    directory.mkdirs()
    val file = File.createTempFile("cymatic-report-", ".txt", directory)
    file.writeText(report)
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.bug-reports",
        file,
        ReportFileName,
    )
}
    .getOrNull()
