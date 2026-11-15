package org.yumigram.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoNoteBubble(
    mediaPath: String?,
    durationSeconds: Int? = 15,
    isOutgoing: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf((durationSeconds ?: 15) * 1000L) }

    val hasRealVideo = remember(mediaPath) {
        if (!mediaPath.isNullOrEmpty()) {
            val f = File(mediaPath)
            f.exists() && f.length() > 0
        } else false
    }

    val exoPlayer = remember(mediaPath) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            if (!mediaPath.isNullOrEmpty()) {
                val uri = if (mediaPath.startsWith("/") || mediaPath.startsWith("file://")) {
                    Uri.fromFile(File(mediaPath))
                } else {
                    Uri.parse(mediaPath)
                }
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && exoPlayer.duration > 0) {
                    totalDurationMs = exoPlayer.duration
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    currentPositionMs = 0L
                    exoPlayer.seekTo(0)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            delay(100)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val displaySecs = remember(currentPositionMs, totalDurationMs, isPlaying) {
        val ms = if (isPlaying || currentPositionMs > 0) currentPositionMs else totalDurationMs
        val secs = (ms / 1000).toInt()
        String.format("%d:%02d", secs / 60, secs % 60)
    }

    // Border brush: neon cyan / blue / violet gradient that animates when playing
    val borderBrush = remember(isPlaying, angle) {
        if (isPlaying) {
            Brush.sweepGradient(
                listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF2979FF),
                    Color(0xFF7C4DFF),
                    Color(0xFF00E5FF)
                )
            )
        } else {
            Brush.sweepGradient(
                listOf(
                    Color(0xFF29B6F6),
                    Color(0xFF0288D1),
                    Color(0xFF29B6F6)
                )
            )
        }
    }

    Column(
        modifier = modifier.width(200.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        // The Telegram Round Video Note Player
        Box(
            modifier = Modifier
                .size(196.dp)
                .rotate(if (isPlaying) angle * 0.05f else 0f)
                .border(3.5.dp, borderBrush, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasRealVideo) {
                // Real Video Frame Rendering via AndroidView PlayerView!
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Fallback circular backdrop when loading or offline
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF1E88E5), Color(0xFF0D47A1), Color(0xFF001026))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Кружечок",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            // Play overlay icon when paused
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Відтворити",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Duration pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.padding(end = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displaySecs,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}
