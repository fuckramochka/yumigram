package org.yumigram.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.yumigram.app.media.AudioRecorderManager

@Composable
fun VoiceRecordBar(
    onCancel: () -> Unit,
    onSendVoice: (filePath: String, durationSec: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorderManager(context) }
    var isRecordingStarted by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Real dynamic waveform bars from microphone amplitude
    val waveformAmplitudes = remember { mutableStateListOf(0.2f, 0.3f, 0.15f, 0.4f, 0.25f, 0.5f, 0.35f, 0.2f, 0.4f, 0.3f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = recorder.startRecording()
            isRecordingStarted = started
            if (!started) {
                Toast.makeText(context, "Не вдалося запустити мікрофон", Toast.LENGTH_SHORT).show()
                onCancel()
            }
        } else {
            Toast.makeText(context, "Потрібен дозвіл на запис аудіо", Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val started = recorder.startRecording()
            isRecordingStarted = started
            if (!started) {
                Toast.makeText(context, "Не вдалося ініціалізувати мікрофон", Toast.LENGTH_SHORT).show()
                onCancel()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingStarted) {
                recorder.cancelRecording()
            }
        }
    }

    // Live timer & real amplitude sampling
    LaunchedEffect(isRecordingStarted) {
        if (!isRecordingStarted) return@LaunchedEffect
        var tickCount = 0
        while (isRecordingStarted) {
            delay(100)
            tickCount++
            if (tickCount % 10 == 0) {
                elapsedSeconds++
            }

            // Real physical microphone input amplitude!
            val rawAmp = recorder.getMaxAmplitude()
            val normalized = (rawAmp / 28000f).coerceIn(0.12f, 1.0f)
            if (waveformAmplitudes.size >= 12) {
                waveformAmplitudes.removeAt(0)
            }
            waveformAmplitudes.add(normalized)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Cancel button
            IconButton(
                onClick = {
                    recorder.cancelRecording()
                    isRecordingStarted = false
                    onCancel()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Скасувати запис",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Pulsing dot & time
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = String.format("%02d:%02d", mins, secs),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Real dynamic physical waveform bars reacting to sound!
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                waveformAmplitudes.forEach { amp ->
                    val barHeight = (amp * 32.dp.value).coerceIn(4f, 32f)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.5.dp)
                            .width(3.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Send Voice Message button
            FilledIconButton(
                onClick = {
                    isRecordingStarted = false
                    val (file, duration) = recorder.stopRecording()
                    if (file != null && file.exists() && file.length() > 0) {
                        onSendVoice(file.absolutePath, duration)
                    } else {
                        Toast.makeText(context, "Запис надто короткий", Toast.LENGTH_SHORT).show()
                        onCancel()
                    }
                },
                modifier = Modifier.size(42.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Надіслати голосове",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
