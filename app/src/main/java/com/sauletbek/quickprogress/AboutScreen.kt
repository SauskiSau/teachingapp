package com.sauletbek.quickprogress

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter) // Можно заменить на Center если хочешь ровно по центру
                .padding(top = 48.dp) // ⬅️ отступ сверху всей страницы
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("💡 " + stringResource(R.string.about_app), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Text("📱 ${stringResource(R.string.app_name)}")
            Text("📦 ${stringResource(R.string.version)}: $versionName")
            Text("👨‍🔬 ${stringResource(R.string.developer)}: Sauletbek Lab")
            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))
            Text("📬 ${stringResource(R.string.contacts)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text("📧 Email: info@sauletbeklab.space")
            Text("📸 Instagram: @sauletbeklab")
            Text("🌐 Website: https://sauletbeklab.space")

            Spacer(modifier = Modifier.height(24.dp))
            Divider()

            Spacer(modifier = Modifier.height(16.dp))
            Text("💖 ${stringResource(R.string.support_project_question)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val paypalUrl = "https://www.paypal.me/SauletbekSovet"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
                    context.startActivity(intent)
                }
            ) {
                Icon(Icons.Default.Payment, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "PayPal")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.qr_kaspi),
                contentDescription = "Kaspi QR",
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth()
            )
            Text(text = stringResource(R.string.kaspi_qr_note))

            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.back))
            }
        }
    }
}

