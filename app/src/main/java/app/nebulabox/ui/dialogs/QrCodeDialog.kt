package app.nebulabox.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.util.QrCodeGenerator

@Composable
fun QrCodeDialog(
    profile: Profile,
    uri: String,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(uri) {
        if (uri.isNotBlank()) QrCodeGenerator.generate(uri, 640) else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = profile.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_code),
                            modifier = Modifier
                                .size(240.dp)
                                .padding(12.dp),
                        )
                    }
                } else {
                    Text(stringResource(R.string.cannot_generate_qr_code_for_this_profile))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
