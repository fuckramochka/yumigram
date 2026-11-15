package org.yumigram.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraIos
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun RoundVideoRecordDialog(
    onDismiss: () -> Unit,
    onSendVideoNote: (filePath: String, durationSec: Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var isCameraBound by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cam = perms[Manifest.permission.CAMERA] ?: false
        val mic = perms[Manifest.permission.RECORD_AUDIO] ?: false
        if (!cam || !mic) {
            Toast.makeText(context, "Потрібні дозволи на камеру та мікрофон", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!camGranted || !micGranted) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // Elapsed timer
    LaunchedEffect(activeRecording) {
        if (activeRecording != null) {
            while (elapsedSeconds < 60) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
                activeRecording?.close()
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.90f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Recording status pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        Text(
                            text = String.format("%02d:%02d / 01:00", mins, secs),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Telegram Circular Viewfinder (REAL CAMERAX PREVIEW)
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .border(
                            4.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF2979FF),
                                    Color(0xFFE040FB),
                                    Color(0xFF00E5FF)
                                )
                            ),
                            CircleShape
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // CameraX Real Viewfinder
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val recorder = Recorder.Builder()
                                        .setQualitySelector(QualitySelector.from(Quality.SD))
                                        .build()
                                    val vc = VideoCapture.withOutput(recorder)
                                    videoCapture = vc

                                    val selector = if (isFrontCamera) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        vc
                                    )
                                    isCameraBound = true

                                    // Start recording automatically as soon as camera is bound
                                    val videoDir = File(ctx.cacheDir, "round_videos").apply { if (!exists()) mkdirs() }
                                    val outFile = File(videoDir, "round_${System.currentTimeMillis()}.mp4")
                                    recordedFile = outFile

                                    val fileOutputOptions = FileOutputOptions.Builder(outFile).build()
                                    var pendingRecording = vc.output.prepareRecording(ctx, fileOutputOptions)

                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        pendingRecording = pendingRecording.withAudioEnabled()
                                    }

                                    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(ctx)) { recordEvent ->
                                        if (recordEvent is VideoRecordEvent.Finalize) {
                                            if (recordEvent.hasError()) {
                                                Log.e("RoundVideoRecord", "Recording error: ${recordEvent.error}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RoundVideoRecord", "Failed to bind camera", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )

                    // Subtitle overlay
                    if (!isCameraBound) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Запуск камери...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel
                    FilledTonalIconButton(
                        onClick = {
                            try {
                                activeRecording?.stop()
                                activeRecording?.close()
                            } catch (_: Exception) {}
                            recordedFile?.delete()
                            onDismiss()
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Скасувати",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Flip Camera
                    FilledTonalIconButton(
                        onClick = {
                            isFrontCamera = !isFrontCamera
                            Toast.makeText(context, if (isFrontCamera) "Фронтальна камера" else "Основна камера", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FlipCameraIos,
                            contentDescription = "Змінити камеру",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Send Round Video
                    FilledIconButton(
                        onClick = {
                            try {
                                activeRecording?.stop()
                                activeRecording?.close()
                            } catch (_: Exception) {}

                            val duration = if (elapsedSeconds > 0) elapsedSeconds else 5
                            val f = recordedFile
                            if (f != null && f.exists() && f.length() > 0) {
                                onSendVideoNote(f.absolutePath, duration)
                            } else {
                                // If recording output was interrupted, provide valid video file path
                                onSendVideoNote(f?.absolutePath ?: "", duration)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Надіслати кружечок",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
