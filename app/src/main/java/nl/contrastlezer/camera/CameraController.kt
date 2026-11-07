package nl.contrastlezer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import nl.contrastlezer.image.FrameProcessor
import nl.contrastlezer.image.HighContrastPalettes
import nl.contrastlezer.image.Palette
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Informatie die de UI nodig heeft nadat de camera is gestart. */
data class CameraStartInfo(
    val maxZoomRatio: Float,
    val hasFlash: Boolean,
)

/**
 * Beheert de camera en de live beeldverwerking.
 *
 * De camera levert RGBA-frames via ImageAnalysis. Elk frame wordt op een
 * achtergrondthread omgezet naar hoog contrast en als Bitmap aan de UI gegeven.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val renderer = FrameRenderer()

    /** Actuele kleurkeuze; mag vanuit de UI-thread worden gezet. */
    @Volatile
    var palette: Palette = HighContrastPalettes[0]

    /** Actuele drempel in 0..255. */
    @Volatile
    var threshold: Float = 30f

    /** Tijdelijk geen frames verwerken (bijvoorbeeld bij een bevroren beeld). */
    @Volatile
    var paused: Boolean = false

    /** Wordt op de achtergrondthread aangeroepen met elk verwerkt frame. */
    @Volatile
    var onFrame: ((Bitmap) -> Unit)? = null

    val maxZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f

    suspend fun start(lifecycleOwner: LifecycleOwner, lensFacing: Int): CameraStartInfo {
        val provider = cameraProvider ?: withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }.also { cameraProvider = it }

        return withContext(Dispatchers.Main) {
            provider.unbindAll()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()

            val analysisUseCase = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { image -> handleFrame(image) }
                }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val selector = try {
                CameraSelector.Builder().requireLensFacing(lensFacing).build()
            } catch (t: Throwable) {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                analysisUseCase,
                capture,
            )

            camera = boundCamera
            imageCapture = capture
            analysis = analysisUseCase

            CameraStartInfo(
                maxZoomRatio = maxZoomRatio,
                hasFlash = boundCamera.cameraInfo.hasFlashUnit(),
            )
        }
    }

    fun release() {
        analysis?.clearAnalyzer()
        analysisExecutor.shutdown()
    }

    fun setZoom(ratio: Float) {
        val camera = camera ?: return
        camera.cameraControl.setZoomRatio(ratio.coerceIn(1f, max(maxZoomRatio, 1f)))
    }

    fun setTorch(enabled: Boolean) {
        val camera = camera ?: return
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(enabled)
        }
    }

    private fun handleFrame(image: ImageProxy) {
        try {
            if (paused) return
            val upright = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val oriented = if (rotation != 0) rotate(upright, rotation) else upright
            renderer.render(oriented, palette, threshold) { bitmap ->
                onFrame?.invoke(bitmap)
            }
            if (oriented !== upright) oriented.recycle()
            upright.recycle()
        } catch (t: Throwable) {
            // Eén mislukt frame mag de preview niet stoppen.
        } finally {
            image.close()
        }
    }

    /**
     * Maakt een foto en zet die om naar de gekozen hoog-contrast kleuren.
     * Het resultaat is verkleind tot [MAX_EDGE] pixels.
     */
    suspend fun captureProcessed(palette: Palette, threshold: Float): Bitmap {
        val raw = takePicture()
        return withContext(Dispatchers.Default) {
            val width = raw.width
            val height = raw.height
            val pixels = IntArray(width * height)
            raw.getPixels(pixels, 0, width, 0, 0, width, height)
            FrameProcessor.process(pixels, width, height, palette, threshold)
            val output = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            raw.recycle()
            output
        }
    }

    private suspend fun takePicture(): Bitmap = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("Camera is nog niet gestart"))
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = decodeJpeg(image)
                        if (continuation.isActive) {
                            continuation.resume(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    } catch (t: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(t)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    private fun decodeJpeg(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_EDGE) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalStateException("Foto kon niet worden gedecodeerd")

        return applyOrientation(decoded, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Houdt twee uitvoerbuffers bij zodat het scherm altijd een compleet beeld heeft. */
    private class FrameRenderer {
        private val buffers = arrayOfNulls<Bitmap>(2)
        private var next = 0
        private var scratch: IntArray = IntArray(0)

        fun render(
            source: Bitmap,
            palette: Palette,
            threshold: Float,
            onFrame: (Bitmap) -> Unit,
        ) {
            val width = source.width
            val height = source.height
            if (scratch.size != width * height) {
                scratch = IntArray(width * height)
            }
            source.getPixels(scratch, 0, width, 0, 0, width, height)
            FrameProcessor.process(scratch, width, height, palette, threshold)

            var output = buffers[next]
            if (output == null || output.width != width || output.height != height) {
                output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                buffers[next] = output
            }
            output.setPixels(scratch, 0, width, 0, 0, width, height)
            next = 1 - next
            onFrame(output)
        }
    }

    companion object {
        /** Maximale lange zijde van verwerkte foto's. */
        const val MAX_EDGE = 1600
    }
}
