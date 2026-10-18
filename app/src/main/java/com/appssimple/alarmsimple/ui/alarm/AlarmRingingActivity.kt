package com.appssimple.alarmsimple.ui.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.domain.model.QuestType
import com.appssimple.alarmsimple.quest.MathProblem
import com.appssimple.alarmsimple.quest.MathQuestEngine
import com.appssimple.alarmsimple.quest.MathSubmitResult
import com.appssimple.alarmsimple.service.AlarmFiringService
import com.appssimple.alarmsimple.ui.theme.AlarmSimpleTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    @Inject lateinit var alarmDao: AlarmDao

    private lateinit var audioManager: AudioManager
    private var nfcAdapter: NfcAdapter? = null
    private var expectedNfcTagId: String? = null
    private var expectedBarcode: String? = null
    private var isVolumeLockEnabled = false

    private val isSirenState = mutableStateOf(false)
    private val questTypeState = mutableStateOf(QuestType.NONE)
    private val isQuestActiveState = mutableStateOf(false)
    private val questSuccessTrigger = mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Camera quest will show its own fallback if denied */ }

    companion object {
        const val ACTION_SIREN_TRIGGERED = "com.appssimple.alarmsimple.ACTION_SIREN_TRIGGERED"
    }

    private val sirenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SIREN_TRIGGERED) {
                isSirenState.value = true
                isVolumeLockEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenDisplay()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val alarmId = intent.getLongExtra(AlarmFiringService.EXTRA_ALARM_ID, -1L)

        ContextCompat.registerReceiver(
            this,
            sirenReceiver,
            IntentFilter(ACTION_SIREN_TRIGGERED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            AlarmSimpleTheme {
                val coroutineScope = rememberCoroutineScope()
                var currentAlarm by remember { mutableStateOf<com.appssimple.alarmsimple.data.db.entity.AlarmEntity?>(null) }

                LaunchedEffect(alarmId) {
                    if (alarmId != -1L) {
                        currentAlarm = alarmDao.getAlarmById(alarmId)
                        currentAlarm?.let {
                            isVolumeLockEnabled = it.volumeLockEnabled
                            questTypeState.value = runCatching { QuestType.valueOf(it.questType) }
                                .getOrDefault(QuestType.NONE)
                            if (questTypeState.value == QuestType.NFC) {
                                expectedNfcTagId = it.questData
                            } else if (questTypeState.value == QuestType.BARCODE) {
                                expectedBarcode = it.questData
                                if (ContextCompat.checkSelfPermission(
                                        this@AlarmRingingActivity,
                                        Manifest.permission.CAMERA
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(questSuccessTrigger.value) {
                    if (questSuccessTrigger.value) {
                        dismissAndFinish()
                    }
                }

                LaunchedEffect(questTypeState.value) {
                    if (questTypeState.value == QuestType.NFC) {
                        enableNfcReaderMode()
                    }
                }

                AlarmRingingScreenContent(
                    isSiren = isSirenState.value,
                    questType = questTypeState.value,
                    isQuestActive = isQuestActiveState.value,
                    expectedBarcode = expectedBarcode,
                    onStartQuestNow = {
                        isQuestActiveState.value = true
                    },
                    onGive5Minutes = {
                        isQuestActiveState.value = true
                        val intent = Intent(this@AlarmRingingActivity, AlarmFiringService::class.java).apply {
                            action = AlarmFiringService.ACTION_GIVE_5_MINUTES
                        }
                        startService(intent)
                    },
                    onDismiss = {
                        dismissAndFinish()
                    },
                    onSnooze = {
                        val intent = Intent(this@AlarmRingingActivity, AlarmFiringService::class.java).apply {
                            action = AlarmFiringService.ACTION_SNOOZE
                        }
                        startService(intent)
                        finish()
                    },
                    onQuestCompleted = {
                        dismissAndFinish()
                    }
                )
            }
        }
    }

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        if (questTypeState.value == QuestType.NFC) {
            enableNfcReaderMode()
        }
    }

    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        runCatching { adapter.enableReaderMode(this, this, flags, null) }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val id = tag?.id ?: return
        val hex = id.joinToString(":") { "%02X".format(it) }
        if (expectedNfcTagId == null || hex.equals(expectedNfcTagId, ignoreCase = true)) {
            runOnUiThread {
                questSuccessTrigger.value = true
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isVolumeLockEnabled || isSirenState.value) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                    }
                    return true // Consume volume events completely
                }
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            // Запрещаем закрывать экран будильника назад, если есть квест или сирена
            if (questTypeState.value != QuestType.NONE || isSirenState.value) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Если пользователь нажал Home / свайпнул вверх свернуть приложение во время активного квеста
        if (questTypeState.value != QuestType.NONE || isSirenState.value) {
            val reopenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(AlarmFiringService.EXTRA_ALARM_ID, intent.getLongExtra(AlarmFiringService.EXTRA_ALARM_ID, -1L))
            }
            startActivity(reopenIntent)
        }
    }

    private fun dismissAndFinish() {
        val intent = Intent(this, AlarmFiringService::class.java).apply {
            action = AlarmFiringService.ACTION_DISMISS
        }
        startService(intent)
        finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(sirenReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun AlarmRingingScreenContent(
    isSiren: Boolean,
    questType: QuestType,
    isQuestActive: Boolean,
    expectedBarcode: String?,
    onStartQuestNow: () -> Unit,
    onGive5Minutes: () -> Unit,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onQuestCompleted: () -> Unit
) {
    val backgroundColor = if (isSiren) Color(0xFF8B0000) else MaterialTheme.colorScheme.background

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                if (isSiren) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Siren",
                        tint = Color.Yellow,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.siren_mode),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = "Alarm",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.channel_alarm_name),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Quest section if active or Siren mode
            if (isQuestActive || isSiren) {
                when (questType) {
                    QuestType.MATH -> {
                        MathQuestView(onCompleted = onQuestCompleted)
                    }
                    QuestType.BARCODE -> {
                        BarcodeQuestView(expectedBarcode = expectedBarcode, onCompleted = onQuestCompleted)
                    }
                    QuestType.NFC -> {
                        Text(
                            text = "Приложите NFC-метку, чтобы выключить будильник",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isSiren) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    QuestType.NONE -> {
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            } else {
                // Initial alarm ringing actions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (questType != QuestType.NONE) {
                        Button(
                            onClick = onStartQuestNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Выполнить задание", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onGive5Minutes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(stringResource(R.string.give_5_minutes), fontSize = 16.sp)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(stringResource(R.string.dismiss), fontSize = 18.sp)
                        }

                        OutlinedButton(
                            onClick = onSnooze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(stringResource(R.string.snooze_minutes, 5), fontSize = 16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun MathQuestView(onCompleted: () -> Unit) {
    val mathEngine = remember { MathQuestEngine() }
    var currentProblem by remember { mutableStateOf(mathEngine.nextProblem()) }
    var answerInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableIntStateOf(15) }

    LaunchedEffect(currentProblem) {
        remainingSeconds = 15
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        val result = mathEngine.submitAnswer(-999999) // trigger timeout
        if (result == MathSubmitResult.TimeoutReset) {
            statusMessage = "Время вышло! Начинаем сначала…"
            currentProblem = mathEngine.nextProblem()
            answerInput = ""
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Задача ${mathEngine.solvedCount + 1} из ${mathEngine.totalProblems}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = currentProblem.expression,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$remainingSeconds с",
                color = if (remainingSeconds < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = answerInput,
                onValueChange = { answerInput = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (statusMessage.isNotEmpty()) {
                Text(
                    statusMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val ans = answerInput.toIntOrNull()
                    if (ans != null) {
                        val result = mathEngine.submitAnswer(ans)
                        when (result) {
                            MathSubmitResult.AllSolved -> onCompleted()
                            MathSubmitResult.CorrectNext -> {
                                statusMessage = ""
                                currentProblem = mathEngine.currentProblem ?: mathEngine.nextProblem()
                                answerInput = ""
                            }
                            MathSubmitResult.WrongAnswerReset -> {
                                statusMessage = "Неверно! Начинаем сначала…"
                                currentProblem = mathEngine.nextProblem()
                                answerInput = ""
                            }
                            MathSubmitResult.TimeoutReset -> {
                                statusMessage = "Время вышло! Начинаем сначала…"
                                currentProblem = mathEngine.nextProblem()
                                answerInput = ""
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ответить")
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeQuestView(
    expectedBarcode: String?,
    onCompleted: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var completed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            cameraExecutor.shutdown()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                if (completed) return@addOnSuccessListener
                                                for (b in barcodes) {
                                                    val raw = b.rawValue
                                                    if (expectedBarcode == null || raw == expectedBarcode) {
                                                        completed = true
                                                        onCompleted()
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

            Text(
                text = "Наведите камеру на штрихкод",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            )
        }
    }
}
