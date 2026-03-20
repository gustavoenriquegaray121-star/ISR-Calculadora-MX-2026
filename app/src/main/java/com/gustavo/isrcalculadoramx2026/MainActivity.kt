package com.gustavo.isrcalculadoramx2026

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

        // --- LÓGICA PREMIUM (699) ---
        binding.btnUpgradePremium.setOnClickListener {
            if (isPremium) {
                if (ultimoNeto > 0) {
                    generarPDFGenerico(ultimoISR, ultimoIMSS, ultimoNeto)
                } else {
                    Toast.makeText(this, "😅 Primero calcula un sueldo", Toast.LENGTH_SHORT).show()
                }
            } else {
                isPremium = true
                binding.adView.visibility = View.GONE
                binding.cardPremium.visibility = View.GONE
                
                // Si el usuario compra Premium, el botón dorado de abajo se actualiza a precio de "Upgrade"
                binding.btnUpgradeSuperPremium.text = "👑 ¡MEJORA A SÚPER PREMIUM!\nSOLO $200 adicionales"
                
                binding.btnUpgradePremium.text = "GENERAR PDF PREMIUM"
                Toast.makeText(this, "💎 Premium activado — \$699/año", Toast.LENGTH_LONG).show()
                
                if (ultimoNeto > 0) {
                    binding.chartGrafica.visibility = View.VISIBLE
                    dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                }
            }
        }

        // --- LÓGICA SÚPER PREMIUM (EL BOTÓN CAMALEÓN CON UPGRADE) ---
        binding.btnUpgradeSuperPremium.setOnClickListener {
            if (!isSuperPremium) {
                // PRIMER CLICK: ACTIVAR EL MODO (Ya sea desde cero o desde upgrade)
                val mensaje = if (isPremium) "¡Upgrade completado por $200!" else "👑 Súper Premium activado — $899/año"
                
                isSuperPremium = true
                isPremium = true
                
                // Limpieza total de ventas
                binding.adView.visibility = View.GONE
                binding.cardPremium.visibility = View.GONE
                
                // Mostramos los campos de personalización
                binding.etNombre.visibility = View.VISIBLE
                binding.etDespacho.visibility = View.VISIBLE
                
                // TRANSFORMACIÓN DEL BOTÓN FINAL
                binding.btnUpgradeSuperPremium.text = "GENERAR Y ENVIAR PDF PERSONALIZADO"
                binding.btnUpgradeSuperPremium.setBackgroundColor(Color.parseColor("#D4AF37")) 
                
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                
                if (ultimoNeto > 0) {
                    binding.chartGrafica.visibility = View.VISIBLE
                    dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                }
            } else {
                // SEGUNDO CLICK: YA ES EL DUEÑO DEL MUNDO, ENVIAR PDF
                if (ultimoNeto > 0) {
                    mostrarDialogPDF()
                } else {
                    Toast.makeText(this, "😅 Primero calcula un sueldo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarDialogPDF() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_premium)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val btnShare = dialog.findViewById<Button>(R.id.btn_share_pdf)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save_pdf)
        btnShare.setOnClickListener {
            generarYCompartirPDF(ultimoISR, ultimoIMSS, ultimoNeto)
            dialog.dismiss()
        }
        btnSave.setOnClickListener {
            generarPDFProfesional(ultimoISR, ultimoIMSS, ultimoNeto)
            dialog.dismiss()
        }
        dialog.show()
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
        val detalleDespues = aplicarSubsidio(detalleAntes, gravable)
        ultimoSubsidioAplicado = detalleDespues.isr < detalleAntes.isr

        val neto = bruto - detalleDespues.isr - imss - deduccionesManual
        val netoRedondeado = (neto * 100).roundToInt() / 100.0

        ultimoISR = detalleDespues.isr
        ultimoIMSS = imss
        ultimoNeto = netoRedondeado

        binding.tvResultado.text = """
            💰 Sueldo Neto: ${String.format("%,.2f", netoRedondeado)} MXN
            🔥 ISR: ${String.format("%,.2f", detalleDespues.isr)} MXN
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
                return ISRDetalle(marginal + tramo.cuota, tramo.limInf, excedente, tramo.tasa, marginal, tramo.cuota)
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
        dataSet.sliceSpace = 4f
        dataSet.selectionShift = 8f
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueLinePart1OffsetPercentage = 80f
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.5f
        dataSet.valueLineColor = Color.WHITE

        binding.chartGrafica.data = PieData(dataSet)
        binding.chartGrafica.setUsePercentValues(true)
        binding.chartGrafica.setDrawHoleEnabled(false)
        binding.chartGrafica.setEntryLabelColor(Color.WHITE)
        binding.chartGrafica.setEntryLabelTextSize(11f)
        binding.chartGrafica.description.isEnabled = false
        binding.chartGrafica.legend.textColor = Color.WHITE
        binding.chartGrafica.legend.textSize = 12f
        binding.chartGrafica.setExtraOffsets(24f, 24f, 24f, 24f)
        binding.chartGrafica.invalidate()
    }

    private fun dibujarBarrasPDF(canvas: android.graphics.Canvas, paint: Paint, isr: Double, imss: Double, neto: Double, startY: Float) {
        val total = isr + imss + neto
        val maxBarWidth = 250f
        val barHeight = 22f
        val barLeft = 40f
        val montoX = 310f
        val pctX = 460f
        var y = startY

        data class Fila(val label: String, val valor: Double, val color: Int)
        val filas = mutableListOf<Fila>()
        if (isr > 0) filas.add(Fila("ISR Retenido", isr, Color.parseColor("#C8A000")))
        if (imss > 0) filas.add(Fila("IMSS (2.375%)", imss, Color.parseColor("#E07000")))
        filas.add(Fila("Sueldo Neto", neto, Color.parseColor("#007A3D")))

        paint.textSize = 10f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#004D39")
        canvas.drawText("CONCEPTO", barLeft, y, paint)
        canvas.drawText("MONTO", montoX, y, paint)
        canvas.drawText("% DEL BRUTO", pctX, y, paint)
        y += 8f
        paint.color = Color.parseColor("#D4AF37")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawLine(barLeft, y, 555f, y, paint)
        y += 14f
        paint.style = Paint.Style.FILL

        for (fila in filas) {
            val pct = if (total > 0) (fila.valor / total * 100) else 0.0
            val barWidth = (fila.valor / total * maxBarWidth).toFloat()
            paint.color = Color.DKGRAY
            paint.textSize = 11f
            paint.isFakeBoldText = false
            canvas.drawText(fila.label, barLeft, y + barHeight * 0.7f, paint)
            paint.color = fila.color
            val barRect = RectF(barLeft, y + barHeight + 2f, barLeft + barWidth, y + barHeight + 2f + 10f)
            canvas.drawRoundRect(barRect, 4f, 4f, paint)
            paint.color = Color.parseColor("#E8E8E8")
            val barBgRect = RectF(barLeft + barWidth, y + barHeight + 2f, barLeft + maxBarWidth, y + barHeight + 2f + 10f)
            canvas.drawRoundRect(barBgRect, 4f, 4f, paint)
            paint.color = Color.parseColor("#222222")
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("$${String.format("%,.2f", fila.valor)}", montoX, y + barHeight * 0.7f, paint)
            paint.color = Color.parseColor("#555555")
            paint.isFakeBoldText = false
            canvas.drawText("${String.format("%.1f", pct)}%", pctX, y + barHeight * 0.7f, paint)
            y += barHeight + 18f
        }
    }

    private fun dibujarHeaderPDF(canvas: android.graphics.Canvas, paint: Paint, titulo: String, fecha: String, nombreUsuario: String, despacho: String) {
        val headerHeight = if (despacho.isNotEmpty()) 125f else 110f
        paint.color = Color.parseColor("#004D39")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 595f, headerHeight, paint)
        paint.color = Color.parseColor("#D4AF37")
        canvas.drawRect(0f, headerHeight - 5f, 595f, headerHeight + 2f, paint)
        paint.color = Color.parseColor("#D4AF37")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.style = Paint.Style.FILL
        canvas.drawText(titulo, 40f, 40f, paint)
        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("ISR Calculadora MX 2026  •  Tablas SAT vigentes", 40f, 60f, paint)
        canvas.drawText("Fecha: $fecha", 40f, 76f, paint)
        if (despacho.isNotEmpty()) {
            paint.textSize = 12f
            paint.isFakeBoldText = true
            paint.color = Color.parseColor("#FFD700")
            canvas.drawText("Preparado por: $despacho", 40f, 94f, paint)
            paint.isFakeBoldText = false
            paint.textSize = 11f
            paint.color = Color.WHITE
            canvas.drawText("Titular: $nombreUsuario", 40f, 110f, paint)
        } else {
            canvas.drawText("Titular: $nombreUsuario", 40f, 94f, paint)
        }
    }

    private fun dibujarFooterPDF(canvas: android.graphics.Canvas, paint: Paint) {
        paint.color = Color.parseColor("#004D39")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 800f, 595f, 842f, paint)
        paint.color = Color.parseColor("#D4AF37")
        canvas.drawRect(0f, 797f, 595f, 801f, paint)
        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Generado por ISR Calculadora MX 2026  •  Cálculo estimado — no sustituye declaración oficial ante el SAT", 20f, 818f, paint)
        canvas.drawText("Basado en tablas SAT 2026 oficiales  •  Versión Súper Premium", 20f, 832f, paint)
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
            val despacho = binding.etDespacho.text.toString().trim()
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            dibujarHeaderPDF(canvas, paint, "REPORTE PROFESIONAL ISR 2026", fecha, nombreUsuario, despacho)
            var y = if (despacho.isNotEmpty()) 145f else 130f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("DATOS DE ENTRADA", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 16f
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Sueldo Bruto Mensual:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", bruto)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("Deducciones Manuales:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", deduccionesManual)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("Cuota IMSS Obrera (2.375%):", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", imss)} MXN", 380f, y, paint); y += 30f
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("CÁLCULO ISR", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 16f
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("ISR Calculado:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", isr)} MXN", 380f, y, paint); y += 20f
            if (ultimoSubsidioAplicado) {
                paint.isFakeBoldText = false
                canvas.drawText("Subsidio al Empleo SAT 2026:", 50f, y, paint)
                paint.isFakeBoldText = true
                paint.color = Color.parseColor("#00796B")
                canvas.drawText("-\$536.22 MXN", 380f, y, paint)
                paint.color = Color.DKGRAY; y += 20f
            }
            y += 10f
            paint.color = Color.parseColor("#004D39")
            paint.style = Paint.Style.FILL
            val rect = RectF(35f, y, 560f, y + 50f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.color = Color.parseColor("#D4AF37")
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("SUELDO NETO FINAL:", 50f, y + 22f, paint)
            canvas.drawText("${String.format("%,.2f", neto)} MXN", 350f, y + 22f, paint)
            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("Ingreso disponible después de impuestos y deducciones", 50f, y + 40f, paint)
            y += 70f
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("DESGLOSE VISUAL", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 14f
            paint.style = Paint.Style.FILL
            dibujarBarrasPDF(canvas, paint, isr, imss, neto, y)
            dibujarFooterPDF(canvas, paint)
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
            val despacho = binding.etDespacho.text.toString().trim()
            val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            dibujarHeaderPDF(canvas, paint, "REPORTE PREMIUM ISR 2026", fecha, nombreUsuario, despacho)
            var y = if (despacho.isNotEmpty()) 145f else 130f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("RESUMEN FISCAL", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 16f
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Sueldo Bruto:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", bruto)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("IMSS (2.375%):", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", imss)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("ISR Retenido:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", isr)} MXN", 380f, y, paint); y += 20f
            if (ultimoSubsidioAplicado) {
                paint.isFakeBoldText = false
                canvas.drawText("Subsidio al Empleo:", 50f, y, paint)
                paint.isFakeBoldText = true
                paint.color = Color.parseColor("#00796B")
                canvas.drawText("-\$536.22 MXN", 380f, y, paint)
                paint.color = Color.DKGRAY; y += 20f
            }
            y += 15f
            paint.color = Color.parseColor("#004D39")
            paint.style = Paint.Style.FILL
            val rect = RectF(35f, y, 560f, y + 50f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.color = Color.parseColor("#D4AF37")
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("SUELDO NETO:", 50f, y + 22f, paint)
            canvas.drawText("${String.format("%,.2f", neto)} MXN", 350f, y + 22f, paint)
            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("Ingreso disponible después de impuestos", 50f, y + 40f, paint)
            dibujarFooterPDF(canvas, paint)
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
            val despacho = binding.etDespacho.text.toString().trim()
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            dibujarHeaderPDF(canvas, paint, "REPORTE PROFESIONAL ISR 2026", fecha, nombreUsuario, despacho)
            var y = if (despacho.isNotEmpty()) 145f else 130f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("DATOS DE ENTRADA", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 16f
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Sueldo Bruto Mensual:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", bruto)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("Deducciones Manuales:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", deduccionesManual)} MXN", 380f, y, paint); y += 20f
            paint.isFakeBoldText = false
            canvas.drawText("Cuota IMSS Obrera (2.375%):", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", imss)} MXN", 380f, y, paint); y += 30f
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("CÁLCULO ISR", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 16f
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("ISR Calculado:", 50f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("${String.format("%,.2f", isr)} MXN", 380f, y, paint); y += 20f
            if (ultimoSubsidioAplicado) {
                paint.isFakeBoldText = false
                canvas.drawText("Subsidio al Empleo SAT 2026:", 50f, y, paint)
                paint.isFakeBoldText = true
                paint.color = Color.parseColor("#00796B")
                canvas.drawText("-\$536.22 MXN", 380f, y, paint)
                paint.color = Color.DKGRAY; y += 20f
            }
            y += 10f
            paint.color = Color.parseColor("#004D39")
            paint.style = Paint.Style.FILL
            val rect = RectF(35f, y, 560f, y + 50f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.color = Color.parseColor("#D4AF37")
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("SUELDO NETO FINAL:", 50f, y + 22f, paint)
            canvas.drawText("${String.format("%,.2f", neto)} MXN", 350f, y + 22f, paint)
            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("Ingreso disponible después de impuestos y deducciones", 50f, y + 40f, paint)
            y += 70f
            paint.color = Color.parseColor("#004D39")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("DESGLOSE VISUAL", 40f, y, paint); y += 22f
            paint.color = Color.parseColor("#D4AF37")
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, 555f, y, paint); y += 14f
            paint.style = Paint.Style.FILL
            dibujarBarrasPDF(canvas, paint, isr, imss, neto, y)
            dibujarFooterPDF(canvas, paint)
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
