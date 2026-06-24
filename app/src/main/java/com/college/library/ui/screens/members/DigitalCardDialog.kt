package com.college.library.ui.screens.members

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.college.library.data.model.Member
import com.college.library.ui.theme.Gold
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import coil.compose.AsyncImage
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PageRange
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.PorterDuffXfermode
import android.graphics.PorterDuff
import android.net.Uri

@Composable
fun DigitalCardDialog(
    member: Member,
    onDismiss: () -> Unit,
    context: Context
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Digital Library Card",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The Card layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF0F1E3D), Color(0xFF070B19))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    // Gold border accent inside the card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent, shape = RoundedCornerShape(12.dp))
                    )

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "GOVT DEGREE COLLEGE",
                                    color = Gold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "LIBRARY MEMBERSHIP CARD",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            // Smart chip mock
                            Box(
                                modifier = Modifier
                                    .size(24.dp, 18.dp)
                                    .background(Gold.copy(alpha = 0.9f), shape = RoundedCornerShape(4.dp))
                            )
                        }

                        // Profile details
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Gold),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!member.photoUri.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = member.photoUri,
                                            contentDescription = "Profile Pic",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Profile Pic",
                                            tint = Gold,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = member.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: ${member.memberId}",
                                    color = Gold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${member.memberType} • ${member.department}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Footer (Barcode + Expiry)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.width(150.dp)) {
                                BarcodeWidget(
                                    text = member.memberId,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                )
                                Text(
                                    text = member.memberId,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "EXPIRY: ${member.expiryDate}",
                                color = Gold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { shareCardPdf(context, member) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Gold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { printCardPdf(context, member) },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Print Card", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeWidget(text: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val seed = text.hashCode().toLong()
        val random = Random(seed)

        var currentX = 2f
        val barWidth = 3f

        // Draw guard bars on left
        drawRect(color = Color.White, topLeft = Offset(currentX, 0f), size = Size(barWidth, height))
        currentX += barWidth + 2f
        drawRect(color = Color.White, topLeft = Offset(currentX, 0f), size = Size(barWidth, height))
        currentX += barWidth + 4f

        while (currentX < width - 10f) {
            val isBar = random.nextBoolean()
            val spacing = random.nextInt(3) + 2f
            val w = if (random.nextBoolean()) barWidth * 2f else barWidth

            if (isBar) {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(currentX, 0f),
                    size = Size(w, height)
                )
            }
            currentX += w + spacing
        }

        // Draw guard bars on right
        drawRect(color = Color.White, topLeft = Offset(width - 8f, 0f), size = Size(barWidth, height))
        drawRect(color = Color.White, topLeft = Offset(width - 3f, 0f), size = Size(barWidth, height))
    }
}

fun shareCardPdf(context: Context, member: Member) {
    try {
        val pdfDocument = PdfDocument()
        // standard ID card size in points: 243 x 153 points (approx 3.375 x 2.125 inches)
        // Let's make it 340 x 210 for better rendering resolution
        val pageInfo = PdfDocument.PageInfo.Builder(340, 210, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paint setup
        val paint = Paint()

        // Background gradient simulation
        paint.color = 0xFF070B19.toInt() // Dark Blue
        canvas.drawRect(0f, 0f, 340f, 210f, paint)

        // Navy Blue Accent band on top
        paint.color = 0xFF0F1E3D.toInt()
        canvas.drawRect(0f, 0f, 340f, 50f, paint)

        // Border
        paint.color = 0xFFD4AF37.toInt() // Gold
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(4f, 4f, 336f, 206f, paint)

        // Texts
        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("GOVT DEGREE COLLEGE", 16f, 24f, paint)

        paint.textSize = 8f
        paint.color = 0xCCD4AF37.toInt() // Light Gold
        canvas.drawText("LIBRARY MEMBERSHIP CARD", 16f, 38f, paint)

        // Member Details
        paint.color = 0xFFFFFFFF.toInt() // White
        paint.textSize = 14f
        canvas.drawText(member.name, 80f, 85f, paint)

        paint.textSize = 10f
        paint.color = 0xFFD4AF37.toInt() // Gold
        canvas.drawText("ID: ${member.memberId}", 80f, 102f, paint)

        paint.color = 0xCCFFFFFF.toInt() // Semi-white
        paint.isFakeBoldText = false
        canvas.drawText("${member.memberType} • ${member.department}", 80f, 118f, paint)
        canvas.drawText("EXPIRY: ${member.expiryDate}", 80f, 134f, paint)

        // Draw profile picture
        if (!member.photoUri.isNullOrBlank()) {
            try {
                val imageUri = Uri.parse(member.photoUri)
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, false)
                    val output = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    val canvasCircle = AndroidCanvas(output)
                    val p = Paint().apply { isAntiAlias = true }
                    canvasCircle.drawARGB(0, 0, 0, 0)
                    canvasCircle.drawCircle(24f, 24f, 24f, p)
                    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvasCircle.drawBitmap(scaledBitmap, 0f, 0f, p)
                    
                    canvas.drawBitmap(output, 18f, 81f, paint)
                } else {
                    drawMockPerson(canvas, paint)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                drawMockPerson(canvas, paint)
            }
        } else {
            drawMockPerson(canvas, paint)
        }

        // Barcode Drawing
        paint.color = 0xFFFFFFFF.toInt()
        val seed = member.memberId.hashCode().toLong()
        val random = Random(seed)
        var startX = 16f
        val barcodeY = 160f
        val barcodeHeight = 22f
        val lineW = 2f

        // Draw start guard
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 1f
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 2f

        while (startX < 200f) {
            val isBar = random.nextBoolean()
            val spacing = random.nextInt(2) + 2f
            val w = if (random.nextBoolean()) lineW * 2f else lineW
            if (isBar) {
                canvas.drawRect(startX, barcodeY, startX + w, barcodeY + barcodeHeight, paint)
            }
            startX += w + spacing
        }

        // Draw end guard
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 1f
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint)

        // Barcode text
        paint.textSize = 7f
        paint.isFakeBoldText = false
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawText(member.memberId, 16f, 194f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "library_card_${member.memberId}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Library Membership Card"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun drawMockPerson(canvas: android.graphics.Canvas, paint: Paint) {
    paint.color = 0x33FFFFFF.toInt()
    canvas.drawCircle(42f, 105f, 24f, paint)
    paint.color = 0xFFD4AF37.toInt()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawCircle(42f, 105f, 24f, paint)

    paint.style = Paint.Style.FILL
    paint.color = 0xFFD4AF37.toInt()
    canvas.drawCircle(42f, 100f, 8f, paint)
    canvas.drawRect(32f, 112f, 52f, 122f, paint)
}

fun printCardPdf(context: Context, member: Member) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(340, 210, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Background gradient simulation
        paint.color = 0xFF070B19.toInt()
        canvas.drawRect(0f, 0f, 340f, 210f, paint)

        paint.color = 0xFF0F1E3D.toInt()
        canvas.drawRect(0f, 0f, 340f, 50f, paint)

        paint.color = 0xFFD4AF37.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(4f, 4f, 336f, 206f, paint)

        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("GOVT DEGREE COLLEGE", 16f, 24f, paint)

        paint.textSize = 8f
        paint.color = 0xCCD4AF37.toInt()
        canvas.drawText("LIBRARY MEMBERSHIP CARD", 16f, 38f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 14f
        canvas.drawText(member.name, 80f, 85f, paint)

        paint.textSize = 10f
        paint.color = 0xFFD4AF37.toInt()
        canvas.drawText("ID: ${member.memberId}", 80f, 102f, paint)

        paint.color = 0xCCFFFFFF.toInt()
        paint.isFakeBoldText = false
        canvas.drawText("${member.memberType} • ${member.department}", 80f, 118f, paint)
        canvas.drawText("EXPIRY: ${member.expiryDate}", 80f, 134f, paint)

        // Draw profile picture
        if (!member.photoUri.isNullOrBlank()) {
            try {
                val imageUri = Uri.parse(member.photoUri)
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, false)
                    val output = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    val canvasCircle = AndroidCanvas(output)
                    val p = Paint().apply { isAntiAlias = true }
                    canvasCircle.drawARGB(0, 0, 0, 0)
                    canvasCircle.drawCircle(24f, 24f, 24f, p)
                    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvasCircle.drawBitmap(scaledBitmap, 0f, 0f, p)
                    
                    canvas.drawBitmap(output, 18f, 81f, paint)
                } else {
                    drawMockPerson(canvas, paint)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                drawMockPerson(canvas, paint)
            }
        } else {
            drawMockPerson(canvas, paint)
        }

        // Barcode
        paint.color = 0xFFFFFFFF.toInt()
        val seed = member.memberId.hashCode().toLong()
        val random = Random(seed)
        var startX = 16f
        val barcodeY = 160f
        val barcodeHeight = 22f
        val lineW = 2f

        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 1f
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 2f

        while (startX < 200f) {
            val isBar = random.nextBoolean()
            val spacing = random.nextInt(2) + 2f
            val w = if (random.nextBoolean()) lineW * 2f else lineW
            if (isBar) {
                canvas.drawRect(startX, barcodeY, startX + w, barcodeY + barcodeHeight, paint)
            }
            startX += w + spacing
        }

        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint); startX += lineW + 1f
        canvas.drawRect(startX, barcodeY, startX + lineW, barcodeY + barcodeHeight, paint)

        paint.textSize = 7f
        paint.isFakeBoldText = false
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawText(member.memberId, 16f, 194f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "library_card_${member.memberId}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val builder = PrintDocumentInfo.Builder("Library Card - ${member.name}")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                callback?.onLayoutFinished(builder.build(), true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                var input: FileInputStream? = null
                var output: FileOutputStream? = null
                try {
                    input = FileInputStream(file)
                    output = FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.toString())
                } finally {
                    input?.close()
                    output?.close()
                }
            }
        }
        printManager.print("Library Card - ${member.name}", printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

