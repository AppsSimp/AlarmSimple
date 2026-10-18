package com.appssimple.alarmsimple.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

import android.content.ContextWrapper
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Диалог привязки NFC-метки к будильнику.
 * Автоматически слушает NFC пока открыт диалог.
 */
@Composable
fun NfcSetupDialog(
    onDismiss: () -> Unit,
    onTagScanned: (tagIdHex: String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var scannedTagId by remember { mutableStateOf<String?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualTagInput by remember { mutableStateOf("") }

    DisposableEffect(activity, nfcAdapter) {
        if (nfcAdapter != null && nfcAdapter.isEnabled && activity != null) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE

            try {
                nfcAdapter.enableReaderMode(activity, { tag: Tag? ->
                    val id = tag?.id ?: return@enableReaderMode
                    val hex = id.joinToString(":") { "%02X".format(it) }
                    
                    // Вибрация подтверждения
                    try {
                        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(120)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    activity.runOnUiThread {
                        scannedTagId = hex
                        onTagScanned(hex)
                    }
                }, flags, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            if (activity != null && nfcAdapter != null) {
                runCatching { nfcAdapter.disableReaderMode(activity) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Привязка NFC-метки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                if (showManualInput) {
                    OutlinedTextField(
                        value = manualTagInput,
                        onValueChange = { manualTagInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ID метки (HEX или текст)") },
                        placeholder = { Text("Например: 04:A1:B2:C3:D4:E5:F6") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (manualTagInput.isNotBlank()) {
                                onTagScanned(manualTagInput.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualTagInput.isNotBlank()
                    ) {
                        Text("Сохранить ID метки")
                    }
                } else {
                    if (nfcAdapter == null) {
                        Text(
                            text = "В этом устройстве отсутствует модуль NFC.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else if (!nfcAdapter.isEnabled) {
                        Text(
                            text = "NFC выключен в настройках телефона.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        }) {
                            Text("Включить NFC")
                        }
                    } else {
                        Text(
                            text = "Поднесите телефон задней крышкой к любой NFC-метке или даже к обычной бесконтактной банковской карте (МИР, Visa, Mastercard, проездной).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Ожидание касания…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showManualInput = !showManualInput }) {
                        Text(if (showManualInput) "Ждать касания" else "Ввести ID вручную")
                    }

                    OutlinedButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

/**
 * Диалог сканирования и привязки штрихкода предмета к будильнику через CameraX и ML Kit.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeSetupDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (barcodeValue: String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isScanned by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualBarcodeInput by remember { mutableStateOf("") }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            cameraExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сканирование штрихкода",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Text(
                    text = "Наведите камеру на любой штрихкод или QR-код (зубная паста, книга, чай и т.д.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (showManualInput) {
                    OutlinedTextField(
                        value = manualBarcodeInput,
                        onValueChange = { manualBarcodeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Цифры штрихкода") },
                        placeholder = { Text("Например: 4601234567890") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (manualBarcodeInput.isNotBlank()) {
                                onBarcodeScanned(manualBarcodeInput.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualBarcodeInput.isNotBlank()
                    ) {
                        Text("Сохранить этот код")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null && !isScanned) {
                                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                                        scanner.process(image)
                                                            .addOnSuccessListener { barcodes ->
                                                                for (b in barcodes) {
                                                                    val raw = b.rawValue
                                                                    if (!raw.isNullOrEmpty() && !isScanned) {
                                                                        isScanned = true
                                                                        
                                                                        // Вибрация подтверждения
                                                                        try {
                                                                            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                            } else {
                                                                                @Suppress("DEPRECATION")
                                                                                vibrator?.vibrate(100)
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                        }

                                                                        (activity ?: (ctx as? Activity))?.runOnUiThread {
                                                                            onBarcodeScanned(raw)
                                                                        }
                                                                        break
                                                                    }
                                                                }
                                                            }
                                                            .addOnCompleteListener { imageProxy.close() }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }
                                            }

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Рамка сканера
                            Box(
                                modifier = Modifier
                                    .size(220.dp, 130.dp)
                                    .align(Alignment.Center)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Нужен доступ к камере", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                                    Text("Разрешить камеру")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showManualInput = !showManualInput }) {
                        Text(if (showManualInput) "Включить камеру" else "Ввести код вручную")
                    }

                    OutlinedButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}
