package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.utils.ReceiptData
import com.example.ui.utils.ReceiptGenerator
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Receipt

/**
 * Interactive Receipt Preview Dialog strictly replicating the minimalist design aesthetic
 * and layout structure shown in the reference ticket image:
 * - Scalloped ticket cutouts with dashed perforated separator
 * - Prominent business name & date header
 * - Conditional customer mobile (collapsed if empty)
 * - Itemized list with item, qty x rate, and subtotal
 * - Shaded gray bounding box for totals (tax, discount, partial payment only shown if > 0)
 * - Dynamic Footer: UPI QR Code when DUE, or centered "Thank you for your business!" when Paid
 * - Immediate native Android ACTION_SEND sharing
 * - Exact source aspect ratio matching the exported image 1:1 with ContentScale.Fit (object-fit: contain)
 */
@Composable
fun ReceiptPreviewDialog(
    receipt: ReceiptData,
    onDismiss: () -> Unit,
    onRecordPaymentClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1. Pre-generate exact receipt Bitmap matching the exported file 1:1
    val receiptBitmap = remember(receipt) {
        ReceiptGenerator.createReceiptBitmap(receipt)
    }
    val imageBitmap = remember(receiptBitmap) {
        receiptBitmap.asImageBitmap()
    }
    val (sourceWidth, sourceHeight) = remember(receipt) {
        ReceiptGenerator.calculateReceiptDimensions(receipt)
    }
    val sourceAspectRatio = remember(sourceWidth, sourceHeight) {
        sourceWidth.toFloat() / sourceHeight.toFloat()
    }

    // Pre-render QR bitmap if status == DUE
    val qrImageBitmap: ImageBitmap? = remember(receipt.isDue, receipt.upiId, receipt.grandTotal) {
        if (receipt.isDue) {
            val upiPayload = "upi://pay?pa=${receipt.upiId.trim()}&pn=${android.net.Uri.encode(receipt.businessName.trim())}&am=${String.format(Locale.US, "%.2f", receipt.grandTotal)}&cu=INR&tn=${android.net.Uri.encode("Receipt " + receipt.receiptNumber)}"
            ReceiptGenerator.drawQrCodeMatrix(upiPayload, 260).asImageBitmap()
        } else {
            null
        }
    }

    var isFullScreenLightbox by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        var animateIn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animateIn = true
        }

        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.90f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = 360f
            ),
            label = "receiptModalScale"
        )
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "receiptModalAlpha"
        )
        val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 0.78f else 0.0f,
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "receiptScrimAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Top Action Bar over dark scrim
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Receipt Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "#${receipt.receiptNumber}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("dismiss_receipt_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Container: Renders with exact source aspect ratio
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // EXACT SOURCE ASPECT RATIO CONTAINER with ContentScale.Fit (object-fit: contain)
                    // Proportions 100% identical to the exported/shared receipt image
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(sourceAspectRatio)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                                .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x15000000), spotColor = Color(0x25000000))
                                .clickable { isFullScreenLightbox = true },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = imageBitmap,
                                contentDescription = "Receipt Preview - #${receipt.receiptNumber}",
                                contentScale = ContentScale.Fit, // object-fit: contain (no cropping, no distortion)
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap image to view full screen & zoom",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action(s)
                if (onRecordPaymentClick != null && receipt.isDue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onRecordPaymentClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("receipt_record_payment_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, com.example.ui.theme.CreditGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = com.example.ui.theme.CreditGreen
                            )
                        ) {
                            Text(
                                text = "Record Payment",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                ReceiptGenerator.generateAndShareReceipt(context, receipt)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("share_receipt_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0F172A)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share Receipt",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Single Primary Action: Share Receipt Button anchored at bottom
                    Button(
                        onClick = {
                            ReceiptGenerator.generateAndShareReceipt(context, receipt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("share_receipt_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Receipt",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    // ============================================================
    // FULL SCREEN LIGHTBOX / ZOOM DIALOG
    // ============================================================
    if (isFullScreenLightbox) {
        Dialog(
            onDismissRequest = { isFullScreenLightbox = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Receipt #${receipt.receiptNumber}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { isFullScreenLightbox = false },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val lightboxScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(lightboxScrollState),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(sourceAspectRatio)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = imageBitmap,
                                contentDescription = "Full Receipt",
                                contentScale = ContentScale.Fit, // object-fit: contain
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            ReceiptGenerator.generateAndShareReceipt(context, receipt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share This Receipt", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
