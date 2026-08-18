package com.miharuniwa.tkb.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.miharuniwa.tkb.ui.theme.PrimaryDark
import kotlin.math.max
import kotlin.math.min

enum class DragHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, CENTER }

@Composable
fun ImageCropper(
    bitmap: Bitmap,
    onCrop: (Bitmap) -> Unit,
    onShare: ((Bitmap) -> Unit)? = null,
    onCancel: () -> Unit
) {
    var cropRect by remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val boxWidth = constraints.maxWidth.toFloat()
                val boxHeight = constraints.maxHeight.toFloat()
                
                if (boxWidth == 0f || boxHeight == 0f) return@BoxWithConstraints
                
                val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val boxRatio = boxWidth / boxHeight
                
                val drawWidth: Float
                val drawHeight: Float
                if (imgRatio > boxRatio) {
                    drawWidth = boxWidth
                    drawHeight = boxWidth / imgRatio
                } else {
                    drawHeight = boxHeight
                    drawWidth = boxHeight * imgRatio
                }
                
                val offsetX = (boxWidth - drawWidth) / 2f
                val offsetY = (boxHeight - drawHeight) / 2f
                
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(with(LocalDensity.current) { drawWidth.toDp() }, with(LocalDensity.current) { drawHeight.toDp() }),
                        contentScale = ContentScale.Fit
                    )
                    
                    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
                    
                    Canvas(
                         modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                             detectDragGestures(
                                 onDragStart = { offset ->
                                     val nx = (offset.x - offsetX) / drawWidth
                                     val ny = (offset.y - offsetY) / drawHeight
                                     val threshold = 32.dp.toPx() / max(drawWidth, drawHeight)
                                     
                                     activeHandle = when {
                                         Math.abs(nx - cropRect.left) < threshold && Math.abs(ny - cropRect.top) < threshold -> DragHandle.TOP_LEFT
                                         Math.abs(nx - cropRect.right) < threshold && Math.abs(ny - cropRect.top) < threshold -> DragHandle.TOP_RIGHT
                                         Math.abs(nx - cropRect.left) < threshold && Math.abs(ny - cropRect.bottom) < threshold -> DragHandle.BOTTOM_LEFT
                                         Math.abs(nx - cropRect.right) < threshold && Math.abs(ny - cropRect.bottom) < threshold -> DragHandle.BOTTOM_RIGHT
                                         Math.abs(nx - cropRect.left) < threshold && ny > cropRect.top && ny < cropRect.bottom -> DragHandle.LEFT
                                         Math.abs(nx - cropRect.right) < threshold && ny > cropRect.top && ny < cropRect.bottom -> DragHandle.RIGHT
                                         Math.abs(ny - cropRect.top) < threshold && nx > cropRect.left && nx < cropRect.right -> DragHandle.TOP
                                         Math.abs(ny - cropRect.bottom) < threshold && nx > cropRect.left && nx < cropRect.right -> DragHandle.BOTTOM
                                         nx > cropRect.left && nx < cropRect.right && ny > cropRect.top && ny < cropRect.bottom -> DragHandle.CENTER
                                         else -> DragHandle.NONE
                                     }
                                 },
                                 onDrag = { _, dragAmount -> 
                                     if (activeHandle == DragHandle.NONE) return@detectDragGestures
                                     val dNx = dragAmount.x / drawWidth
                                     val dNy = dragAmount.y / drawHeight
                                     
                                     var newL = cropRect.left
                                     var newT = cropRect.top
                                     var newR = cropRect.right
                                     var newB = cropRect.bottom
                                     
                                     if (activeHandle == DragHandle.CENTER) {
                                         // Cap the translation delta so the bounding box preserves its exact width and height
                                         val cappedDNx = dNx.coerceIn(-cropRect.left, 1f - cropRect.right)
                                         val cappedDNy = dNy.coerceIn(-cropRect.top, 1f - cropRect.bottom)
                                         
                                         newL += cappedDNx
                                         newR += cappedDNx
                                         newT += cappedDNy
                                         newB += cappedDNy
                                     } else {
                                         when (activeHandle) {
                                             DragHandle.TOP_LEFT -> { newL += dNx; newT += dNy }
                                             DragHandle.TOP_RIGHT -> { newR += dNx; newT += dNy }
                                             DragHandle.BOTTOM_LEFT -> { newL += dNx; newB += dNy }
                                             DragHandle.BOTTOM_RIGHT -> { newR += dNx; newB += dNy }
                                             DragHandle.LEFT -> { newL += dNx }
                                             DragHandle.RIGHT -> { newR += dNx }
                                             DragHandle.TOP -> { newT += dNy }
                                             DragHandle.BOTTOM -> { newB += dNy }
                                             else -> {}
                                         }
                                     }
                                     
                                     val minSize = 0.05f
                                     if (activeHandle != DragHandle.CENTER) {
                                         if (newR - newL < minSize) { if (activeHandle in listOf(DragHandle.LEFT, DragHandle.TOP_LEFT, DragHandle.BOTTOM_LEFT)) newL = newR - minSize else newR = newL + minSize }
                                         if (newB - newT < minSize) { if (activeHandle in listOf(DragHandle.TOP, DragHandle.TOP_LEFT, DragHandle.TOP_RIGHT)) newT = newB - minSize else newB = newT + minSize }
                                     }
                                     
                                     newL = max(0f, min(newL, 1f))
                                     newT = max(0f, min(newT, 1f))
                                     newR = max(0f, min(newR, 1f))
                                     newB = max(0f, min(newB, 1f))
                                     
                                     cropRect = Rect(newL, newT, newR, newB)
                                 },
                                 onDragEnd = { activeHandle = DragHandle.NONE },
                                 onDragCancel = { activeHandle = DragHandle.NONE }
                             )
                         }
                    ) {
                        val cl = offsetX + cropRect.left * drawWidth
                        val ct = offsetY + cropRect.top * drawHeight
                        val cr = offsetX + cropRect.right * drawWidth
                        val cb = offsetY + cropRect.bottom * drawHeight
                        
                        val overlay = Color.Black.copy(alpha = 0.6f)
                        drawRect(overlay, Offset.Zero, Size(size.width, ct)) // top
                        drawRect(overlay, Offset(0f, cb), Size(size.width, size.height - cb)) // bottom
                        drawRect(overlay, Offset(0f, ct), Size(cl, cb - ct)) // left
                        drawRect(overlay, Offset(cr, ct), Size(size.width - cr, cb - ct)) // right
                        
                        drawRect(Color.White, topLeft = Offset(cl, ct), size = Size(cr - cl, cb - ct), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                        
                        val cornerLen = 20.dp.toPx()
                        val thick = 4.dp.toPx()
                        
                        drawRect(Color.White, Offset(cl - thick/2, ct - thick/2), Size(cornerLen, thick))
                        drawRect(Color.White, Offset(cl - thick/2, ct - thick/2), Size(thick, cornerLen))
                        
                        drawRect(Color.White, Offset(cr - cornerLen + thick/2, ct - thick/2), Size(cornerLen, thick))
                        drawRect(Color.White, Offset(cr - thick/2, ct - thick/2), Size(thick, cornerLen))
                        
                        drawRect(Color.White, Offset(cl - thick/2, cb - thick/2), Size(cornerLen, thick))
                        drawRect(Color.White, Offset(cl - thick/2, cb - cornerLen + thick/2), Size(thick, cornerLen))
                        
                        drawRect(Color.White, Offset(cr - cornerLen + thick/2, cb - thick/2), Size(cornerLen, thick))
                        drawRect(Color.White, Offset(cr - thick/2, cb - cornerLen + thick/2), Size(thick, cornerLen))
                    }
                }
            }
        }
        
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Hủy", color = Color.White) }
                
                if (onShare != null) {
                    IconButton(
                        onClick = {
                            val x = (cropRect.left * bitmap.width).toInt()
                            val y = (cropRect.top * bitmap.height).toInt()
                            val w = ((cropRect.right - cropRect.left) * bitmap.width).toInt()
                            val h = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt()
                            try {
                                val cropped = Bitmap.createBitmap(bitmap, max(0, x), max(0, y), min(w, bitmap.width - x), min(h, bitmap.height - y))
                                onShare(cropped)
                            } catch (e: Exception) {
                                onCancel()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Chia sẻ", tint = Color.White)
                    }
                }
            }
            
                Button(
                    onClick = { 
                        val x = (cropRect.left * bitmap.width).toInt()
                        val y = (cropRect.top * bitmap.height).toInt()
                        val w = ((cropRect.right - cropRect.left) * bitmap.width).toInt()
                        val h = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt()
                        
                        try {
                            val cropped = Bitmap.createBitmap(bitmap, max(0, x), max(0, y), min(w, bitmap.width - x), min(h, bitmap.height - y))
                            onCrop(cropped)
                        } catch (e: Exception) {
                            onCancel()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark, contentColor = Color(0xFF003258))
                ) {
                    Text("Lưu Tải xuống")
                }
        }
    }
}
