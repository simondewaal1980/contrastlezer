package nl.contrastlezer.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Herkent tekst op een bitmap met ML Kit. Alles gebeurt op het toestel zelf
 * (offline); er gaan geen beelden naar een server. Het Latin-model van ML Kit
 * dekt ook Nederlandse tekst.
 *
 * Gebruik één exemplaar per scherm en sluit het weer met [close] wanneer het
 * niet meer nodig is.
 */
class TextScanner : AutoCloseable {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Herkent de tekst op [bitmap] en geeft die netjes opgemaakt terug.
     * Geeft een lege string terug als er geen tekst is gevonden.
     */
    suspend fun scan(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            recognizer
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    continuation.resume(OcrTextCleaner.clean(result.text))
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    override fun close() {
        recognizer.close()
    }
}
