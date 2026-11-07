package nl.contrastlezer.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.contrastlezer.R

private const val CODEBOOK_URL = "https://simondewaal1980.github.io/contrastlezer/"

/**
 * Volledig scherm met informatie over de app: naam, versie, privacy en een
 * verwijzing naar het codeboek. De versie komt uit het APK-manifest, zodat
 * dit scherm automatisch klopt bij een nieuwe release.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }

    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFFFFD500), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(52.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (versionName.isBlank()) {
                    stringResource(R.string.about_version_unknown)
                } else {
                    stringResource(R.string.about_version, versionName)
                },
                color = Color(0xFFFFD500),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.about_description),
                    color = Color(0xFFEDEDED),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color(0x33FFFFFF))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.about_privacy_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_privacy),
                    color = Color(0xFFEDEDED),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color(0x33FFFFFF))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.about_tech_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_tech),
                    color = Color(0xFFEDEDED),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(16.dp))

                val uriHandler = LocalUriHandler.current
                Text(
                    text = stringResource(R.string.about_codebook),
                    color = Color(0xFFFFD500),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching { uriHandler.openUri(CODEBOOK_URL) }
                        }
                        .padding(vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onClose,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD500),
                    contentColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.close),
                    color = Color.Black,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

/** Leest de versienaam uit het manifest (bijv. "0.02 Alpha"). */
private fun appVersionName(context: Context): String =
    try {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        info.versionName.orEmpty()
    } catch (t: PackageManager.NameNotFoundException) {
        ""
    }
