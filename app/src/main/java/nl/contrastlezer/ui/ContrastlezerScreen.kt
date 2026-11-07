package nl.contrastlezer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.contrastlezer.R
import nl.contrastlezer.camera.CameraController
import nl.contrastlezer.camera.CapturedPhoto
import nl.contrastlezer.image.GallerySaver
import nl.contrastlezer.image.HighContrastPalettes
import nl.contrastlezer.image.Palette
import nl.contrastlezer.ocr.SpeechReader
import nl.contrastlezer.ocr.TextScanner
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Een frame uit de camera. Nieuwe instanties zorgen dat Compose opnieuw tekent. */
private class LiveFrame(val bitmap: Bitmap)

@Composable
fun ContrastlezerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (!result) deniedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasCameraPermission(context)) {
                granted = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        CameraContent()
    } else {
        PermissionScreen(denied = deniedOnce, onRequest = {
            launcher.launch(Manifest.permission.CAMERA)
        })
    }
}

@Composable
private fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(0.6f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var frozen by remember { mutableStateOf<CapturedPhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf<String?>(null) }
    var speech by remember { mutableStateOf<SpeechReader?>(null) }
    var autoSpeak by rememberSaveable { mutableStateOf(false) }
    var stabilization by rememberSaveable { mutableStateOf(true) }
    var hardwareStabilization by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var liveFrame by remember { mutableStateOf<LiveFrame?>(null) }

    val controller = remember { CameraController(context) }
    val textScanner = remember { TextScanner() }

    val palette = HighContrastPalettes[paletteIndex]
    val threshold = sensitivityToThreshold(sensitivity) * 255f
    val cameraFailedMessage = stringResource(R.string.camera_failed)
    val captureFailedMessage = stringResource(R.string.capture_failed)
    val savedMessage = stringResource(R.string.saved)
    val saveFailedMessage = stringResource(R.string.save_failed)
    val noTextMessage = stringResource(R.string.ocr_empty)
    val ocrFailedMessage = stringResource(R.string.ocr_failed)
    val ttsFailedMessage = stringResource(R.string.tts_failed)

    DisposableEffect(controller) {
        controller.onFrame = { bitmap ->
            liveFrame = LiveFrame(bitmap)
        }
        onDispose {
            controller.onFrame = null
            controller.release()
        }
    }

    DisposableEffect(textScanner) {
        onDispose { textScanner.close() }
    }

    DisposableEffect(Unit) {
        onDispose { speech?.close() }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3500)
            notice = null
        }
    }

    LaunchedEffect(lensFacing) {
        try {
            val info = controller.start(lifecycleOwner, lensFacing)
            hasFlash = info.hasFlash
            hardwareStabilization = info.hardwareStabilization
            maxZoomRatio = info.maxZoomRatio
            zoom = zoom.coerceIn(1f, max(info.maxZoomRatio, 1f))
            if (torchOn && !info.hasFlash) torchOn = false
            controller.setZoom(zoom)
            controller.setTorch(torchOn && info.hasFlash)
        } catch (t: Throwable) {
            notice = cameraFailedMessage
        }
    }

    LaunchedEffect(zoom) { controller.setZoom(zoom) }
    LaunchedEffect(torchOn) { controller.setTorch(torchOn) }
    LaunchedEffect(paletteIndex, threshold) {
        controller.palette = palette
        controller.threshold = threshold
    }
    LaunchedEffect(stabilization) { controller.stabilization = stabilization }
    LaunchedEffect(frozen) { controller.paused = frozen != null }

    fun ensureSpeechReader(): SpeechReader {
        speech?.let { return it }
        val reader = SpeechReader(context)
        reader.onError = { notice = ttsFailedMessage }
        speech = reader
        return reader
    }

    fun snap(onResult: (CapturedPhoto) -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val photo = controller.capturePhoto(palette, threshold)
                onResult(photo)
            } catch (t: Throwable) {
                notice = captureFailedMessage
            } finally {
                busy = false
            }
        }
    }

    fun recognizeText(photo: CapturedPhoto) {
        if (scanning) return
        scope.launch {
            scanning = true
            try {
                // Eerst het originele beeld: ML Kit is daarop getraind. Lukt het
                // daar niet, probeer dan het hoog-contrast-beeld; juist bij
                // lastige kleuren staat de tekst daar duidelijker op.
                var text = textScanner.scan(photo.original)
                if (text.isBlank()) {
                    text = textScanner.scan(photo.processed)
                }
                if (text.isBlank()) {
                    notice = noTextMessage
                } else {
                    val reader = ensureSpeechReader()
                    scannedText = text
                    if (autoSpeak) reader.speak(text)
                }
            } catch (t: Throwable) {
                notice = ocrFailedMessage
            } finally {
                scanning = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val frame = liveFrame
        if (frame != null) {
            Image(
                bitmap = frame.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD500))
            }
        }

        // Knijpen om te vergroten en dubbeltik om terug te zetten.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { zoom = 1f })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        zoom = (zoom * zoomChange)
                            .coerceIn(1f, max(controller.maxZoomRatio, 1f))
                    }
                },
        )

        frozen?.let { photo ->
            FrozenOverlay(photo.processed)
        }

        notice?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xEE1F1F1F),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (busy || scanning) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD500),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (zoom > 1.01f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (notice != null) 64.dp else 12.dp),
                shape = CircleShape,
                color = Color(0xCC000000),
            ) {
                Text(
                    text = stringResource(
                        R.string.zoom_value,
                        String.format(Locale.US, "%.1f", zoom),
                    ),
                    color = Color(0xFFFFD500),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showSettings) {
                SettingsPanel(
                    sensitivity = sensitivity,
                    onSensitivity = { sensitivity = it },
                    zoom = zoom,
                    onZoom = { zoom = it },
                    maxZoom = maxZoomRatio,
                    stabilization = stabilization,
                    onStabilization = { stabilization = it },
                    hardwareStabilization = hardwareStabilization,
                    autoSpeak = autoSpeak,
                    onAutoSpeak = { autoSpeak = it },
                    hasFlash = hasFlash,
                    torchOn = torchOn,
                    onTorch = { torchOn = !torchOn },
                    onSwitchCamera = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                        torchOn = false
                    },
                    onOpenAbout = { showAbout = true },
                )
                Spacer(Modifier.height(8.dp))
            }

            PaletteRow(
                selectedIndex = paletteIndex,
                onSelect = { paletteIndex = it },
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                ControlButton(
                    icon = if (frozen != null) Icons.Rounded.PlayArrow else Icons.Rounded.AcUnit,
                    label = stringResource(if (frozen != null) R.string.resume else R.string.freeze),
                    active = frozen != null,
                    enabled = !busy,
                    onClick = {
                        if (frozen != null) {
                            frozen = null
                        } else {
                            // Een echte foto geeft een scherper en rustiger
                            // beeld dan het laatste live-frame.
                            snap { photo -> frozen = photo }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ControlButton(
                    icon = Icons.Rounded.TextFields,
                    label = stringResource(R.string.read_text),
                    enabled = frozen != null && !scanning && !busy,
                    onClick = { frozen?.let { recognizeText(it) } },
                    modifier = Modifier.weight(1f),
                )
                ControlButton(
                    icon = Icons.Rounded.PhotoCamera,
                    label = stringResource(R.string.photo),
                    enabled = !busy,
                    onClick = {
                        val frozenPhoto = frozen
                        if (frozenPhoto != null) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        GallerySaver.save(context, frozenPhoto.processed)
                                    }
                                    notice = savedMessage
                                } catch (t: Throwable) {
                                    notice = saveFailedMessage
                                }
                            }
                        } else {
                            snap { photo ->
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            GallerySaver.save(context, photo.processed)
                                        }
                                        notice = savedMessage
                                    } catch (t: Throwable) {
                                        notice = saveFailedMessage
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ControlButton(
                    icon = Icons.Rounded.Tune,
                    label = stringResource(R.string.settings),
                    active = showSettings,
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        scannedText?.let { text ->
            val reader = speech
            if (reader != null) {
                TextResultSheet(
                    text = text,
                    palette = palette,
                    speech = reader,
                    onClose = {
                        reader.stop()
                        scannedText = null
                    },
                )
            }
        }

        if (showAbout) {
            AboutScreen(onClose = { showAbout = false })
        }
    }
}

@Composable
private fun FrozenOverlay(image: Bitmap) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val imageBitmap = remember(image) { image.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(image) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                )
            }
            .pointerInput(image) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val newScale = (scale * zoomChange).coerceIn(1f, 8f)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                }
            },
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = stringResource(R.string.frozen_content_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        FrozenZoomControls(
            scale = scale,
            onZoomIn = { scale = (scale * 1.5f).coerceAtMost(8f) },
            onZoomOut = {
                val newScale = (scale / 1.5f).coerceAtLeast(1f)
                scale = newScale
                if (newScale <= 1f) offset = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xCC000000),
        ) {
            Text(
                text = stringResource(R.string.frozen_hint),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Grote knoppen zodat vergroten ook zonder knijpgebaar lukt. */
@Composable
private fun FrozenZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ZoomButton(
            icon = Icons.Rounded.Add,
            description = stringResource(R.string.zoom_in),
            enabled = scale < 7.99f,
            onClick = onZoomIn,
        )
        ZoomButton(
            icon = Icons.Rounded.Remove,
            description = stringResource(R.string.zoom_out),
            enabled = scale > 1.01f,
            onClick = onZoomOut,
        )
    }
}

@Composable
private fun ZoomButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xDD1F1F1F) else Color(0x881F1F1F))
            .border(1.dp, Color(0x66FFFFFF), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color(0xFF777777),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = null,
            tint = Color(0xFFFFD500),
            modifier = Modifier.size(84.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (denied) R.string.permission_denied else R.string.permission_body,
            ),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = Color(0xFFF2F2F2),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD500),
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_button),
                color = Color.Black,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PaletteRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighContrastPalettes.forEachIndexed { index, palette ->
            PaletteChip(
                palette = palette,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun PaletteChip(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(palette.background))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color(0xFFFFD500) else Color(0x66FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = palette.name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Aa",
            color = Color(palette.foreground),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFFFFD500) else Color(0xCC1F1F1F))
                .border(1.dp, Color(0x44FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color.Black else Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (enabled) Color(0xFFEDEDED) else Color(0xFF777777),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsPanel(
    sensitivity: Float,
    onSensitivity: (Float) -> Unit,
    zoom: Float,
    onZoom: (Float) -> Unit,
    maxZoom: Float,
    stabilization: Boolean,
    onStabilization: (Boolean) -> Unit,
    hardwareStabilization: Boolean,
    autoSpeak: Boolean,
    onAutoSpeak: (Boolean) -> Unit,
    hasFlash: Boolean,
    torchOn: Boolean,
    onTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Surface(
        color = Color(0xE61A1A1A),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stabilization),
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = stringResource(R.string.stabilization_description),
                        color = Color(0xFFBDBDBD),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = stabilization,
                    onCheckedChange = onStabilization,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (hardwareStabilization) {
                Text(
                    text = stringResource(R.string.hardware_stabilization),
                    color = Color(0xFF9CCC65),
                    fontSize = 12.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_speak),
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = stringResource(R.string.auto_speak_description),
                        color = Color(0xFFBDBDBD),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = autoSpeak,
                    onCheckedChange = onAutoSpeak,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            SliderRow(
                label = stringResource(R.string.sensitivity),
                valueText = "${(sensitivity * 100).roundToInt()}%",
                value = sensitivity,
                onValueChange = onSensitivity,
                range = 0f..1f,
            )

            if (maxZoom > 1.05f) {
                ZoomRow(
                    zoom = zoom,
                    onZoom = onZoom,
                    maxZoom = maxZoom,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasFlash) {
                    WideButton(
                        icon = Icons.Rounded.FlashOn,
                        label = stringResource(R.string.torch),
                        active = torchOn,
                        onClick = onTorch,
                        modifier = Modifier.weight(1f),
                    )
                }
                WideButton(
                    icon = Icons.Rounded.Cameraswitch,
                    label = stringResource(R.string.switch_camera),
                    onClick = onSwitchCamera,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            WideButton(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.about),
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ZoomRow(
    zoom: Float,
    onZoom: (Float) -> Unit,
    maxZoom: Float,
) {
    val upper = min(maxZoom, 10f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.zoom),
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.zoom_value,
                String.format(Locale.US, "%.1f", zoom),
            ),
            color = Color(0xFFFFD500),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomButton(
            icon = Icons.Rounded.Remove,
            description = stringResource(R.string.zoom_out),
            enabled = zoom > 1.01f,
            onClick = { onZoom((zoom - ZOOM_STEP).coerceAtLeast(1f)) },
        )
        Slider(
            value = zoom,
            onValueChange = onZoom,
            valueRange = 1f..upper,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        )
        ZoomButton(
            icon = Icons.Rounded.Add,
            description = stringResource(R.string.zoom_in),
            enabled = zoom < upper - 0.01f,
            onClick = { onZoom((zoom + ZOOM_STEP).coerceAtMost(upper)) },
        )
    }
}

/** Brede knop met icoon en tekst, voor minder vaak gebruikte functies. */
@Composable
private fun WideButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0xFFFFD500) else Color(0xCC1F1F1F))
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color.Black else Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (active) Color.Black else Color(0xFFEDEDED),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            color = Color(0xFFFFD500),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
    )
}

/** Stapgrootte van de vergroot- en verkleinknoppen. */
private const val ZOOM_STEP = 0.5f

/** Hogere gevoeligheid betekent een lagere drempel (0..1). */
private fun sensitivityToThreshold(sensitivity: Float): Float {
    return 0.25f - 0.20f * sensitivity.coerceIn(0f, 1f)
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}
