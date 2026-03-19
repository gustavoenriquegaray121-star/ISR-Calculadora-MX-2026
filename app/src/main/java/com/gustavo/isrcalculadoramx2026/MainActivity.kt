package com.gustavo.isrcalculadoramx2026

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
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
    private var bruto = 0.0
    private var deduccionesManual = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Cambiar del tema Splash al tema normal de la app
        setTheme(R.style.Theme_ISRCalculadoraMX2026)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvEmote.alpha = 0f
        binding.tvResultado.text = ""

        binding.btnCalcular.setOnClickListener { calcularISR() }

        binding.btnUpgradePremium.setOnClickListener {
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(this,
                "💎 Premium desbloqueado — $99/mes o $699/año",
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
            Toast.makeText(this,
                "👑 Súper Premium desbloqueado — $149/mes o $1,299/año",
                Toast.LENGTH_LONG).show()
            if (ultimoNeto > 0) {
                binding.chartGrafica.visibility = View.VISIBLE
                dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                generarPDFProfesional(ultimoISR, ultimoIMSS, ultimoNeto)
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

        var detalle = calcularISRDetalle(gravable)
        detalle = aplicarSubsidio(detalle, gravable)

        val neto = bruto - detalle.isr - imss - deduccionesManual
        val netoRedondeado = (neto * 100).roundToInt() / 100.0

        ultimoISR = detalle.isr
        ultimoIMSS = imss
        ultimoNeto = netoRedondeado

        binding.tvResultado.text = """
            💰 Sueldo Neto: ${String.format("%,.2f", netoRedondeado)} MXN
            🔥 ISR: ${String.format("%,.2f", detalle.isr)} MXN
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

        mostrarTipFiscal(bruto)

        if (isPremium) {
            binding.chartGrafica.visibility = View.VISIBLE
            dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
        }
    }

    private fun mostrarTipFiscal(sueldoBruto: Double) {
        binding.cardTipFiscal.visibility = View.VISIBLE
        val tip = when {
            sueldoBruto > 60000 -> "🚀 Tip: Estás en el tramo superior de ISR. Considera un PPR para deducir hasta el 10% anual legalmente."
            sueldoBruto > 25000 -> "💡 Tip: Tus gastos de lentes graduados y transporte escolar son deducibles. ¡Pide factura XML!"
            else -> "✨ Tip: Los honorarios médicos y dentales pagados con tarjeta son deducibles en tu declaración anual."
        }
        binding.tvTipTexto.text = tip
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
        val entries = listOf(
            PieEntry(isr.toFloat(), "ISR"),
            PieEntry(imss.toFloat(), "IMSS"),
            PieEntry(neto.toFloat(), "Neto")
        )
        val dataSet = PieDataSet(entries, "Desglose")
        dataSet.colors = listOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF8F00"),
            Color.parseColor("#00C853")
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f
        binding.chartGrafica.data = PieData(dataSet)
        binding.chartGrafica.setEntryLabelColor(Color.WHITE)
        binding.chartGrafica.description.isEnabled = false
        binding.chartGrafica.legend.textColor = Color.WHITE
        binding.chartGrafica.setDrawHoleEnabled(true)
        binding.chartGrafica.holeRadius = 50f
        binding.chartGrafica.transparentCircleRadius = 55f
        binding.chartGrafica.setHoleColor(Color.TRANSPARENT)
        binding.chartGrafica.invalidate()
    }

    private fun generarPDFGenerico(isr: Double, imss: Double, neto: Double) {
        try {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint()
            paint.textSize = 18f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("ISR Calculadora MX 2026 - Reporte Premium", 40f, 60f, paint)
            paint.textSize = 14f
            paint.isFakeBoldText = false
            var y = 100f
            canvas.drawText("Sueldo Bruto: ${String.format("%,.2f", bruto)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("IMSS (2.375%): ${String.format("%,.2f", imss)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("ISR: ${String.format("%,.2f", isr)} MXN", 40f, y, paint); y += 25f
            canvas.drawText("Subsidio SAT 2026: $536.22 MXN", 40f, y, paint); y += 35f
            paint.isFakeBoldText = true
            paint.textSize = 16f
            canvas.drawText("SUELDO NETO: ${String.format("%,.2f", neto)} MXN", 40f, y, paint); y += 30f
            paint.isFakeBoldText = false
            paint.textSize = 11f
            canvas.drawText("Cálculo estimado — no sustituye declaración oficial ante el SAT", 40f, y, paint)
            doc.finishPage(page)
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "ISR_Premium_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            Toast.makeText(this, "✅ PDF Premium guardado", Toast.LENGTH_LONG).show()
            doc.close()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generarPDFProfesional(isr: Double, imss: Double, neto: Double) {
        try {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint()
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
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
            var y = 120f
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
            canvas.drawText("• Subsidio al empleo SAT 2026: $536.22 MXN", 60f, y, paint); y += 35f
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
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "ISR_SuperPremium_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            Toast.makeText(this, "✅ PDF Profesional guardado", Toast.LENGTH_LONG).show()
            doc.close()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
