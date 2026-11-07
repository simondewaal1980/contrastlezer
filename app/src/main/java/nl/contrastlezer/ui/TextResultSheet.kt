package nl.contrastlezer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.contrastlezer.R
import nl.contrastlezer.image.Palette
import nl.contrastlezer.ocr.SpeechReader

/**
 * Volledig scherm met de herkende tekst in zeer groot contrast. De kleuren
 * volgen de gekozen kleurmodus, zodat de tekst net zo leesbaar is als het
 * hoog-contrast camerabeeld. De tekst kan worden voorgelezen.
 */
@Composable
fun TextResultSheet(
    text: String,
    palette: Palette,
    speech: SpeechReader,
    onClose: () -> Unit,
) {
    val foreground = Color(palette.foreground)
    val background = Color(palette.background)
    val speaking by speech.speaking.collectAsState()

    BackHandler(onBack = onClose)

    DisposableEffect(speech) {
        onDispose { speech.stop() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.ocr_title),
                color = foreground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Text(
                    text = text,
                    color = foreground,
                    fontSize = 32.sp,
                    lineHeight = 44.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LargeSheetButton(
                    icon = if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                    label = stringResource(if (speaking) R.string.stop_speaking else R.string.speak),
                    foreground = foreground,
                    background = background,
                    onClick = {
                        if (speaking) speech.stop() else speech.speak(text)
                    },
                    modifier = Modifier.weight(1f),
                )
                LargeSheetButton(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.close),
                    foreground = foreground,
                    background = background,
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LargeSheetButton(
    icon: ImageVector,
    label: String,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = foreground,
            contentColor = background,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 20.sp,
        )
    }
}
