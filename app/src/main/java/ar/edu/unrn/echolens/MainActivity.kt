package ar.edu.unrn.echolens

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.edu.unrn.echolens.ui.theme.EcholensTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SttBleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcholensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AudioScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioScreen(viewModel: SttBleViewModel, modifier: Modifier = Modifier) {
    // CORRECCIÓN 1: Usamos los nuevos nombres de variables
    val uiText by viewModel.uiText.collectAsState()
    val isRecordingMic by viewModel.isRecordingMic.collectAsState()

    // Manejo de permisos
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎧 EchoLens Live",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Caja de texto de transcripción
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                // CORRECCIÓN 2: Usamos 'uiText' en lugar de 'transcription'
                Text(
                    text = uiText,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Start
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Botón de Grabar
        if (hasPermission) {
            Button(
                // CORRECCIÓN 3: Llamamos a 'toggleMicRecording'
                onClick = { viewModel.toggleMicRecording() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingMic) Color.Red else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(width = 200.dp, height = 60.dp)
            ) {
                Text(
                    text = if (isRecordingMic) "⏹ DETENER" else "🎤 HABLAR",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón extra para probar BLE si lo necesitas (Opcional)
            Button(onClick = { viewModel.initBle(ar.edu.unrn.echolens.App.context); viewModel.startBleStreaming() }) {
                Text("🔗 Conectar Lentes (BLE)")
            }

        } else {
            Text("⚠️ Se requiere permiso de micrófono")
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Dar Permiso")
            }
        }
    }
}
// Un pequeño hack para obtener contexto global si el botón BLE lo pide,
// o simplemente pasa el contexto desde el activity.
object App { lateinit var context: android.content.Context }