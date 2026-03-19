package com.gustavo.isrcalculadoramx2026

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.gustavo.isrcalculadoramx2026.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class TramoISR(
    val limInf: Double,
    val limSup: Double,
    val cuota: Double,
    val tasa: Double
)

data class ISRDetalle(
    val isr: Double,
    val limiteInf: Double,
    val excedente: Double,
    val tasa: Double,
    val marginal: Double,
    val cuotaFija: Double
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isPremium = false
    private var isSuperPremium = false
    private var ultimoISR = 0.0
    private var ultimoIMSS = 0.0
    private var ultimoNeto = 0.0
    private var ultimoSubsidioAplicado = false
    private var bruto = 0.0
    private var deduccionesManual = 0.0

    private val listaTips = listOf(
        "Los honorarios médicos, dentales y de nutrición pagados con tarjeta son deducibles en tu declaración anual.",
        "Las colegiaturas son deducibles. Necesitas el CFDI con el nivel educativo y CURP del alumno.",
        "¿Pagando tu casa? Los intereses reales de créditos hipotecarios son deducciones personales.",
        "Las aportaciones voluntarias a tu AFORE no solo aseguran tu futuro, también reducen tu ISR.",
        "Los gastos por lentes ópticos graduados (hasta $2,500 MXN) son deducibles para ti o tu familia.",
        "Las donaciones a instituciones donatarias autorizadas son deducibles — revisa los topes anuales.",
        "Si trabajas por honorarios, tus herramientas de trabajo (computadora, software) pueden ser deducciones autorizadas.",
        "Estás en el tramo superior de ISR. Considera un PPR para deducir hasta el 10% anual legalmente.",
        "Tus gastos de lentes graduados y transporte escolar son deducibles. ¡Pide factura XML!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ISRCalculadoraMX2026)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        binding.tvEmote.alpha = 0f
        binding.tvResultado.text = ""

        binding.btnCalcular.setOnClickListener { calcularISR() }

        binding.btnUpgradePremium.setOnClickListener {
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(this,
                "💎 Premium desbloqueado — $119 pago único",
                Toast.LENGTH_LONG).show()
            if (ultimoNeto > 0) {
                binding.chartGrafica.visibility = View.VISIBLE
                dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                generarPDFGenerico(ultimoISR, ultimoIMSS, ultimoNeto)
            }
        }

        binding.btnUpgradeSuperPremium.setOnClickListener {
            isSuperPremium = true
            isPremium = true
            binding.adView.visibility = View.GONE
            binding.etNombre.visibility = View.VISIBLE

            val dialog = android.app.Dialog(this)
            dialog.setContentView(R.layout.dialog_premium)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val btnShare = dialog.findViewById<Button>(R.id.btn_share_pdf)
            val btnSave = dialog.findViewById<Button>(R.id.btn_save_pdf)

            btnShare.setOnClickListener {
                if (ultimoNeto > 0) {
                    generarYCompartirPDF(ultimoISR, ultimoIMSS, ultimoNeto)
                } else {
                    Toast.makeText(this, "😅 Primero calcula un sueldo", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            btnSave.setOnClickListener {
                if (ultimoNeto > 0) {
                    generarPDFProfesional(ultimoISR, ultimoIMSS, ultimoNeto)
                } else {
                    Toast.makeText(this, "😅 Primero calcula un sueldo", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            dialog.show()

            if (ultimoNeto > 0) {
                binding.chartGrafica.visibility = View.VISIBLE
                dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
            }
        }
    }

    private fun calcularISR() {
        bruto = binding.etSueldoBruto.text.toString().toDoubleOrNull() ?: 0.0
        deduccionesManual = binding.etDeducciones.text.toString().toDoubleOrNull() ?: 0.0

        if (bruto <= 0) {
            Toast.makeText(this, "😅 Ingresa un sueldo bruto válido", Toast.LENGTH_SHORT).show()
            return
        }

        val imss = if (binding.switchIMSS.isChecked) bruto * 0.02375 else 0.0
        val gravable = bruto - deduccionesManual - imss

        if (gravable <= 0) {
            Toast.makeText(this, "🤔 El ingreso gravable es 0", Toast.LENGTH_SHORT).show()
            return
        }

        val detalleAntes = calcularISRDetalle(gravable)
        val detalleDepues = aplicarSubsidio(detalleAntes, gravable)
        ultimoSubsidioAplicado = detalleDepues.isr < detalleAntes.isr

        val neto = bruto - detalleDepues.isr - imss - deduccionesManual
        val netoRedondeado = (neto * 100).roundToInt() / 100.0

        ultimoISR = detalleDepues.isr
        ultimoIMSS = imss
        ultimoNeto = netoRedondeado

        binding.tvResultado.text = """
            💰 Sueldo Neto: ${String.format("%,.2f", netoRedondeado)} MXN
            🔥 ISR: ${String.format("%,.2f", detalleDepues.isr)} MXN
            🏥 IMSS (2.375%): ${String.format("%,.2f", imss)} MXN
            📋 Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN
            
            Cálculo estimado SAT 2026
        """.trimIndent()

        binding.tvEmote.text = when {
            neto >= bruto * 0.85 -> "🤑 ¡Estás en la cima, jefe!"
            neto >= bruto * 0.70 -> "😎 ¡Sigue rico!"
            neto >= bruto * 0.55 -> "🙂 No está mal, eh"
            neto >= bruto * 0.40 -> "😬 Uy… aprieta el cinturón"
            else -> "😭 El SAT mordió fuerte esta vez"
        }

        binding.tvEmote.animate().alpha(1f).setDuration(600).start()
        mostrarTipFiscal()

        if (isPremium) {
            binding.chartGrafica.visibility = View.VISIBLE
            dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
        }
    }

    private fun mostrarTipFiscal() {
        binding.cardTipFiscal.visibility = View.VISIBLE
        binding.tvTipTexto.text = listaTips.random()
    }

    private fun calcularISRDetalle(gravable: Double): ISRDetalle {
        val tramos = listOf(
            TramoISR(0.01, 10135.11, 0.00, 1.92),
            TramoISR(10135.12, 86022.11, 194.59, 6.40),
            TramoISR(86022.12, 151176.19, 5051.37, 10.88),
            TramoISR(151176.20, 176935.68, 12140.16, 16.00),
            TramoISR(176935.69, 210403.68, 16069.68, 17.92),
            TramoISR(210403.69, 424354.00, 22282.08, 21.36),
            TramoISR(424354.01, 668840.16, 67981.92, 23.52),
            TramoISR(668840.17, 1276926.00, 125485.08, 30.00),
            TramoISR(1276926.01, 1702567.92, 307910.76, 32.00),
            TramoISR(1702567.93, 5107703.88, 444116.28, 34.00),
            TramoISR(5107703.89, Double.MAX_VALUE, 1601862.48, 35.00)
        )

        for (tramo in tramos) {
            if (gravable <= tramo.limSup) {
                val excedente = gravable - tramo.limInf
                val marginal = excedente * (tramo.tasa / 100)
                return ISRDetalle(
                    marginal + tramo.cuota,
                    tramo.limInf, excedente,
                    tramo.tasa, marginal, tramo.cuota
                )
            }
        }
        return ISRDetalle(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    private fun aplicarSubsidio(detalle: ISRDetalle, gravable: Double): ISRDetalle {
        if (gravable > 11492.66) return detalle
        return detalle.copy(isr = max(0.0, detalle.isr - 536.22))
    }

    private fun dibujarGrafica(isr: Double, imss: Double, neto: Double) {
        val entries = mutableListOf<PieEntry>()
        if (isr > 0) entries.add(PieEntry(isr.toFloat(), "ISR"))
        if (imss > 0) entries.add(PieEntry(imss.toFloat(), "IMSS"))
        entries.add(PieEntry(neto.toFloat(), "Neto"))

        val colors = mutableListOf<Int>()
        if (isr > 0) colors.add(Color.parseColor("#FFD700"))
        if (imss > 0) colors.add(Color.parseColor("#FF8F00"))
        colors.add(Color.parseColor("#00C853"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colors
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 13f
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 8f

        binding.chartGrafica.data = PieData(dataSet)
        binding.chartGrafica.setUsePercentValues(true)
        binding.chartGrafica.setDrawHoleEnabled(false)
        binding.chartGrafica.setEntryLabelColor(Color.WHITE)
        binding.chartGrafica.setEntryLabelTextSize(12f)
        binding.chartGrafica.description.isEnabled = false
        binding.chartGrafica.legend.textColor = Color.WHITE
        binding.chartGrafica.legend.textSize = 12f
        binding.chartGrafica.setExtraOffsets(16f, 16f, 16f, 16f)
        binding.chartGrafica.invalidate()
    }

    private fun getNombreArchivo(sufijo: String): String {
        val nombreUsuario = binding.etNombre.text.toString().trim()
        return if (nombreUsuario.isNotEmpty()) {
            "Reporte_ISR_2026_${nombreUsuario.replace(" ", "_")}_$sufijo.pdf"
        } else {
            "Reporte_ISR_2026_$sufijo.pdf"
        }
    }

    private fun generarYCompartirPDF(isr: Double, imss: Double, neto: Double) {
        try {
            val nombreUsuario = binding.etNombre.text.toString().trim().ifEmpty { "Contribuyente" }
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint()
            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("REPORTE PROFESIONAL ISR 2026", 40f, 60f, paint)
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Fecha: $fecha", 40f, 85f, paint)
            paint.strokeWidth = 2f
            canvas.drawLine(40f, 95f, 555f, 95f, paint)
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText("Contribuyente: $nombreUsuario", 40f, 115f, paint)
            paint.isFakeBoldText = false
            var y = 145f
            paint.isFakeBoldText = true
            canvas.drawText("DATOS DE ENTRADA:", 40f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("• Sueldo Bruto: ${String.format("%,.2f", bruto)} MXN", 60f, y, paint); y += 20f
            canvas.drawText("• Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN", 60f, y, paint); y += 20f
            canvas.drawText("• IMSS (2.375%): ${String.format("%,.2f", imss)} MXN", 60f, y, paint); y += 35f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.isFakeBoldText = true
            canvas.drawText("CÁLCULO ISR:", 40f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("• ISR calculado: ${String.format("%,.2f", isr)} MXN", 60f, y, paint); y += 20f
            if (ultimoSubsidioAplicado) {
                canvas.drawText("• Subsidio al empleo SAT 2026 aplicado: -\$536.22 MXN", 60f, y, paint); y += 20f
            }
            y += 15f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("SUELDO NETO FINAL: ${String.format("%,.2f", neto)} MXN", 40f, y, paint); y += 40f
            paint.strokeWidth = 2f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.isFakeBoldText = false
            paint.textSize = 10f
            canvas.drawText("Generado por ISR Calculadora MX 2026 — Versión Súper Premium", 40f, y, paint); y += 15f
            canvas.drawText("Basado en tablas SAT 2026. Cálculo estimado — no sustituye declaración oficial.", 40f, y, paint)
            doc.finishPage(page)
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), getNombreArchivo("Compartir"))
            doc.writeTo(FileOutputStream(file))
            doc.close()
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir reporte ISR"))
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generarPDFGenerico(isr: Double, imss: Double, neto: Double) {
        try {
            val nombreUsuario = binding.etNombre.text.toString().trim().ifEmpty { "Contribuyente" }
            val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint()
            paint.textSize = 18f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("ISR Calculadora MX 2026 — Reporte Premium", 40f, 60f, paint)
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Fecha: $fecha", 40f, 80f, paint)
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText("Contribuyente: $nombreUsuario", 40f, 100f, paint)
            paint.isFakeBoldText = false
            var y = 130f
            canvas.drawText("Sueldo Bruto: ${String.format("%,.2f", bruto)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("IMSS (2.375%): ${String.format("%,.2f", imss)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("ISR: ${String.format("%,.2f", isr)} MXN", 40f, y, paint); y += 25f
            if (ultimoSubsidioAplicado) {
                canvas.drawText("Subsidio al empleo SAT 2026: -\$536.22 MXN", 40f, y, paint); y += 25f
            }
            y += 10f
            paint.isFakeBoldText = true
            paint.textSize = 16f
            canvas.drawText("SUELDO NETO: ${String.format("%,.2f", neto)} MXN", 40f, y, paint); y += 30f
            paint.isFakeBoldText = false
            paint.textSize = 11f
            canvas.drawText("Cálculo estimado — no sustituye declaración oficial ante el SAT", 40f, y, paint)
            doc.finishPage(page)
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), getNombreArchivo("Premium"))
            doc.writeTo(FileOutputStream(file))
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            Toast.makeText(this, "✅ PDF Premium guardado", Toast.LENGTH_LONG).show()
            doc.close()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generarPDFProfesional(isr: Double, imss: Double, neto: Double) {
        try {
            val nombreUsuario = binding.etNombre.text.toString().trim().ifEmpty { "Contribuyente" }
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint()
            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("REPORTE PROFESIONAL ISR 2026", 40f, 60f, paint)
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Fecha: $fecha", 40f, 85f, paint)
            paint.strokeWidth = 2f
            canvas.drawLine(40f, 95f, 555f, 95f, paint)
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText("Contribuyente: $nombreUsuario", 40f, 115f, paint)
            paint.isFakeBoldText = false
            var y = 145f
            paint.isFakeBoldText = true
            canvas.drawText("DATOS DE ENTRADA:", 40f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("• Sueldo Bruto: ${String.format("%,.2f", bruto)} MXN", 60f, y, paint); y += 20f
            canvas.drawText("• Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN", 60f, y, paint); y += 20f
            canvas.drawText("• IMSS (2.375%): ${String.format("%,.2f", imss)} MXN", 60f, y, paint); y += 35f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.isFakeBoldText = true
            canvas.drawText("CÁLCULO ISR:", 40f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("• ISR calculado: ${String.format("%,.2f", isr)} MXN", 60f, y, paint); y += 20f
            if (ultimoSubsidioAplicado) {
                canvas.drawText("• Subsidio al empleo SAT 2026 aplicado: -\$536.22 MXN", 60f, y, paint); y += 20f
            }
            y += 15f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("SUELDO NETO FINAL: ${String.format("%,.2f", neto)} MXN", 40f, y, paint); y += 40f
            paint.strokeWidth = 2f
            canvas.drawLine(40f, y, 555f, y, paint); y += 25f
            paint.isFakeBoldText = false
            paint.textSize = 10f
            canvas.drawText("Generado por ISR Calculadora MX 2026 — Versión Súper Premium", 40f, y, paint); y += 15f
            canvas.drawText("Basado en tablas SAT 2026. Cálculo estimado — no sustituye declaración oficial.", 40f, y, paint)
            doc.finishPage(page)
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), getNombreArchivo("SuperPremium"))
            doc.writeTo(FileOutputStream(file))
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            Toast.makeText(this, "✅ PDF Profesional guardado", Toast.LENGTH_LONG).show()
            doc.close()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
