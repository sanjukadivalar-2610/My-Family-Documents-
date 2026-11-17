package com.example.data.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.DocumentCategory
import java.io.ByteArrayOutputStream

data class SampleDocumentTemplate(
    val title: String,
    val category: DocumentCategory,
    val fileType: String,
    val fileName: String,
    val maskedId: String,
    val notes: String,
    val issueOffsetDays: Long,
    val expiryOffsetDays: Long // 0L = no expiry
)

object SampleDocumentFactory {

    private const val DAY_MS = 86_400_000L

    fun templates(memberName: String): List<SampleDocumentTemplate> = listOf(
        SampleDocumentTemplate(
            title = "$memberName - Sample Aadhaar",
            category = DocumentCategory.AADHAAR,
            fileType = "png",
            fileName = "sample_aadhaar_card.png",
            maskedId = "DEMO-0000-1001",
            notes = "Synthetic sample card for development testing only.",
            issueOffsetDays = -1200L,
            expiryOffsetDays = 0L
        ),
        SampleDocumentTemplate(
            title = "$memberName - Sample PAN Card",
            category = DocumentCategory.PAN,
            fileType = "png",
            fileName = "sample_pan_card.png",
            maskedId = "DEMOP0001A",
            notes = "Synthetic sample PAN card for testing.",
            issueOffsetDays = -900L,
            expiryOffsetDays = 0L
        ),
        SampleDocumentTemplate(
            title = "$memberName - Sample Passport",
            category = DocumentCategory.PASSPORT,
            fileType = "pdf",
            fileName = "sample_passport.pdf",
            maskedId = "Z9990001",
            notes = "Expiring soon demo document to test expiry reminders.",
            issueOffsetDays = -3635L,
            expiryOffsetDays = 18L
        ),
        SampleDocumentTemplate(
            title = "$memberName - Sample Driving Licence",
            category = DocumentCategory.DRIVING_LICENCE,
            fileType = "pdf",
            fileName = "sample_driving_licence.pdf",
            maskedId = "KA01DEMO0001",
            notes = "Synthetic sample licence PDF.",
            issueOffsetDays = -1800L,
            expiryOffsetDays = 420L
        ),
        SampleDocumentTemplate(
            title = "Family Sample Ration Card",
            category = DocumentCategory.RATION_CARD,
            fileType = "pdf",
            fileName = "sample_ration_card.pdf",
            maskedId = "RC-DEMO-4402",
            notes = "Synthetic household ration card sample.",
            issueOffsetDays = -730L,
            expiryOffsetDays = 900L
        ),
        SampleDocumentTemplate(
            title = "$memberName - Health Policy Sample",
            category = DocumentCategory.MEDICAL,
            fileType = "pdf",
            fileName = "sample_medical_policy.pdf",
            maskedId = "MED-DEMO-771",
            notes = "Synthetic health insurance certificate.",
            issueOffsetDays = -350L,
            expiryOffsetDays = 15L
        )
    )

    fun renderSampleBytes(template: SampleDocumentTemplate, memberName: String): ByteArray {
        return if (template.fileType == "pdf") {
            renderSamplePdf(template, memberName)
        } else {
            renderSamplePng(template, memberName)
        }
    }

    private fun renderSamplePng(template: SampleDocumentTemplate, memberName: String): ByteArray {
        val width = 1080
        val height = 680
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Navy banking card background
        canvas.drawColor(Color.parseColor("#F8FAFC"))
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#172554")
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 40f, 40f, cardPaint)

        // Sky blue top stripe
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, 150f), 40f, 40f, stripePaint)
        canvas.drawRect(40f, 110f, width - 40f, 150f, stripePaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MY FAMILY DOC • SAMPLE ONLY", 80f, 112f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(template.category.labelEn, 80f, 240f, titlePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
            textSize = 34f
        }
        canvas.drawText("Holder: $memberName", 80f, 320f, bodyPaint)
        canvas.drawText("Demo Ref: ${template.maskedId}", 80f, 385f, bodyPaint)

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("SYNTHETIC TEST DOCUMENT • NOT A REAL GOVT ID", 80f, 560f, watermarkPaint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun renderSamplePdf(template: SampleDocumentTemplate, memberName: String): ByteArray {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val headerPaint = Paint().apply { color = Color.parseColor("#172554") }
        canvas.drawRect(0f, 0f, 595f, 120f, headerPaint)

        val accentPaint = Paint().apply { color = Color.parseColor("#38BDF8") }
        canvas.drawRect(0f, 120f, 595f, 132f, accentPaint)

        val whiteTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MY FAMILY DOC — SAMPLE RECORD", 36f, 64f, whiteTitle)

        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            textSize = 14f
        }
        canvas.drawText("Encrypted Vault Synthetic Document", 36f, 92f, subtitle)

        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(template.title, 36f, 190f, darkPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 15f
        }
        canvas.drawText("Category: ${template.category.labelEn}", 36f, 240f, bodyPaint)
        canvas.drawText("Member: $memberName", 36f, 275f, bodyPaint)
        canvas.drawText("Demo Number: ${template.maskedId}", 36f, 310f, bodyPaint)
        canvas.drawText("Notes: ${template.notes}", 36f, 345f, bodyPaint)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
        }
        canvas.drawRoundRect(RectF(36f, 410f, 559f, 510f), 16f, 16f, boxPaint)

        val noticePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#172554")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("SAMPLE DOCUMENT FOR DEVELOPMENT & TESTING ONLY", 56f, 455f, noticePaint)
        canvas.drawText("Does not contain real Aadhaar, PAN, or banking credentials.", 56f, 480f, bodyPaint)

        pdf.finishPage(page)
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    fun computeTimestamps(template: SampleDocumentTemplate, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val issue = nowMillis + (template.issueOffsetDays * DAY_MS)
        val expiry = if (template.expiryOffsetDays == 0L) 0L else nowMillis + (template.expiryOffsetDays * DAY_MS)
        return issue to expiry
    }
}
