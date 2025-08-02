package io.shubham0204.smollmandroid.ui.screens.sms_analysis

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.os.Bundle
import android.util.Log
import io.shubham0204.smollmandroid.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import compose.icons.FeatherIcons
import compose.icons.feathericons.MoreVertical
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Shield
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.getViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.ViewModel
import android.content.Context
import org.koin.java.KoinJavaComponent.getKoin

private const val LOGTAG = "[SmsActivity]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

class SmsListViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return getKoin().get<SmsListViewModel> { parametersOf(context) } as T
    }
}


class SmsActivity : ComponentActivity() {
    private lateinit var viewModel: SmsListViewModel
    private val SMS_PERMISSION_REQUEST_CODE = 100


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), SMS_PERMISSION_REQUEST_CODE)
        } else {
            // Permission already granted, proceed with SMS handling
            LOGD( "SMS permission already granted")
        }

        viewModel = ViewModelProvider(
            this,
            SmsListViewModelFactory(this) // or applicationContext if needed
        )[SmsListViewModel::class.java]

        LOGD("Activity created")
        
        viewModel.loadModel(onComplete = {LOGD( "Model is completely loaded Ayush")})
        
        setContent {
            val showSelectModelsListDialog by viewModel.showSelectModelListDialogState.collectAsStateWithLifecycle()
            if (showSelectModelsListDialog) {
               SelectModelsList(viewModel = viewModel)
            }

            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "sms-list-ui",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable("sms-list-ui") {
                    SmsListScreenUI(
                        onNavigateToSettings = {
                            navController.navigate("edit-model")
                        }
                    )
                }
                composable("edit-model") {
                    EditModelSettingsScreen(
                        viewModel = viewModel,
                        onBackClicked = { navController.navigateUp() },
                    )
                }
            }
        }
    }
}


@Composable
private fun RAMUsageLabel(viewModel: SmsListViewModel) {
    val showRAMUsageLabel by viewModel.showRAMUsageLabel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var labelText by remember { mutableStateOf("") }
    LaunchedEffect(showRAMUsageLabel) {
        if (showRAMUsageLabel) {
            while (true) {
                val (used, total) = viewModel.getCurrentMemoryUsage()
                labelText = context.getString(R.string.label_device_ram).format(used, total)
                delay(3000L)
            }
        }
    }
    if (showRAMUsageLabel) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            labelText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SelectModelsList(viewModel: SmsListViewModel) {
    val showSelectModelsListDialog by viewModel.showSelectModelListDialogState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (showSelectModelsListDialog) {
        val modelsList by
            viewModel.modelsRepository.getAvailableModels().collectAsState(emptyList())
        SelectModelsList(
            onDismissRequest = {
                viewModel.onEvent(
                    SmsAnalysisScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(
                        visible = false,
                    ),
                )
            },
            modelsList,
            onModelListItemClick = { model ->
                viewModel.updateChatLLMParams(model.id, model.chatTemplate)
                viewModel.loadModel()
                viewModel.onEvent(SmsAnalysisScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(visible = false))
            },
            onModelDeleteClick = { model ->
                viewModel.deleteModel(model.id)
                Toast
                    .makeText(
                        viewModel.context,
                        context.getString(R.string.chat_model_deleted, model.name),
                        Toast.LENGTH_LONG,
                    ).show()
            },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsListScreenUI(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SmsListViewModel = getViewModel(parameters = { parametersOf(context) })
    val messages by SmsMessageManager.messagesFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "SMS Guard Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "SMS Guard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.onEvent(
                                SmsAnalysisScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup(
                                    visible = true,
                                ),
                            )
                        },
                    ) {
                        Icon(
                            FeatherIcons.MoreVertical,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    ModelMoreOptionsPopup(viewModel, onNavigateToSettings)

                }

            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                SmsMessageCard(
                    message = message,
                    onClick = {
                        // Launch SMS analysis activity with existing results
                        val intent = Intent(context, SmsAnalysisActivity::class.java).apply {
                            putExtra("sms_sender", message.sender)
                            putExtra("sms_body", message.body)
                            putExtra("sms_timestamp", message.timestamp)
                            putExtra("message_id", message.id)
                            // Pass existing analysis results if available
                            message.isSmishing?.let { putExtra("sms_is_smishing", it) }
                            message.explanation?.let { putExtra("sms_explanation", it) }
                            message.tips?.let { putExtra("sms_tips", it) }
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
        SelectModelsList(viewModel)
    }

}

@Composable
fun SmsMessageCard(
    message: SmsMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with sender and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Status indicator
                message.isSmishing?.let { isSmishing ->
                    Icon(
                        imageVector = if (isSmishing) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = if (isSmishing) "Smishing detected" else "Safe message",
                        tint = if (isSmishing) Color.Red else Color.Green,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Message body
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Analysis status
            if (message.isSmishing == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Analyzing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - timestamp

    return when {
        diff < 60000 -> "Just now" // Less than 1 minute
        diff < 3600000 -> "${diff / 60000}m ago" // Less than 1 hour
        diff < 86400000 -> "${diff / 3600000}h ago" // Less than 1 day
        else -> {
            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            formatter.format(date)
        }
    }
}
