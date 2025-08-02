package io.shubham0204.smollmandroid.ui.screens.sms_analysis

import android.content.Context
import io.shubham0204.smollmandroid.ui.theme.SmolLMAndroidTheme
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import org.koin.android.ext.android.inject
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.getKoin

@AndroidEntryPoint
class SmsAnalysisActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SmsAnalysisActivity", "Activity created")
        
        val sender = intent.getStringExtra("sms_sender") ?: "Unknown"
        val body = intent.getStringExtra("sms_body") ?: ""
        val timestamp = intent.getLongExtra("sms_timestamp", System.currentTimeMillis())
        val messageId = intent.getStringExtra("message_id")
        val existingIsSmishing = intent.getBooleanExtra("sms_is_smishing", false)
        val existingExplanation = intent.getStringExtra("sms_explanation")
        val existingTips = intent.getStringExtra("sms_tips")
        
        Log.d("SmsAnalysisActivity", "Received data - Sender: $sender, Body: $body, MessageId: $messageId")
        
        // Check if we have existing analysis results
        val hasExistingResults = existingExplanation != null && existingTips != null
        
        setContent {
            SmolLMAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsAnalysisScreen(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        messageId = messageId,
                        context = this,
                        hasExistingResults = hasExistingResults,
                        existingIsSmishing = existingIsSmishing,
                        existingExplanation = existingExplanation,
                        existingTips = existingTips,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen(
    sender: String,
    body: String,
    timestamp: Long,
    messageId: String?,
    context: Context,
    hasExistingResults: Boolean = false,
    existingIsSmishing: Boolean = false,
    existingExplanation: String? = null,
    existingTips: String? = null,
    onBackPressed: () -> Unit,
) {
    val viewModel: SmsListViewModel = koinViewModel(parameters = { parametersOf(context) })

    var analysisStatus by remember { mutableStateOf(
        if (hasExistingResults) AnalysisStatus.COMPLETED else AnalysisStatus.PROCESSING
    ) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(
        if (hasExistingResults) {
            if (existingIsSmishing) AnalysisResult.SMISHING else AnalysisResult.BENIGN
        } else null
    ) }
    var smsAnalysisResult by remember { mutableStateOf<SmsAnalysisResult?>(
        if (hasExistingResults) {
            SmsAnalysisResult(
                isSmishing = existingIsSmishing,
                explanation = existingExplanation ?: "",
                tips = existingTips ?: ""
            )
        } else null
    ) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val modelInfo by remember { mutableStateOf<String?>(null) }
    var explanation by remember { mutableStateOf<String?>(existingExplanation) }
    
    val scope = rememberCoroutineScope()
    
    // Observe SmsMessageManager for updates
    LaunchedEffect(messageId) {
        if (messageId != null) {
            SmsMessageManager.messagesFlow.collect { messages ->
                val currentMessage = messages.find { it.id == messageId }
                currentMessage?.let { message ->
                    if (message.isSmishing != null) {
                        Log.d("SmsAnalysisActivity", "Received update for message $messageId: isSmishing=${message.isSmishing}")
                        smsAnalysisResult = SmsAnalysisResult(
                            isSmishing = message.isSmishing,
                            explanation = message.explanation ?: "",
                            tips = message.tips ?: ""
                        )
                        analysisResult = if (message.isSmishing) AnalysisResult.SMISHING else AnalysisResult.BENIGN
                        explanation = message.explanation
                        analysisStatus = AnalysisStatus.COMPLETED
                        Log.d("SmsAnalysisActivity", "Analysis status updated to COMPLETED from flow")
                    }
                }
            }
        }
    }
    
    // Check for existing analysis results and start analysis if needed
    LaunchedEffect(Unit) {

        if (hasExistingResults) {
            Log.d("SmsAnalysisActivity", "Using existing analysis results from intent")
            return@LaunchedEffect
        }
        
        // Check if we have results in SmsMessageManager
        messageId?.let { id ->
            val existingMessage = SmsMessageManager.getMessageById(id)
            if (existingMessage?.isSmishing != null) {
                Log.d("SmsAnalysisActivity", "Found existing analysis in SmsMessageManager")
                smsAnalysisResult = SmsAnalysisResult(
                    isSmishing = existingMessage.isSmishing,
                    explanation = existingMessage.explanation ?: "",
                    tips = existingMessage.tips ?: ""
                )
                analysisResult = if (existingMessage.isSmishing) AnalysisResult.SMISHING else AnalysisResult.BENIGN
                explanation = existingMessage.explanation
                analysisStatus = AnalysisStatus.COMPLETED
                Log.d("SmsAnalysisActivity", "Analysis status updated to COMPLETED from SmsMessageManager")
                return@LaunchedEffect
            }
        }
        
        Log.d("SmsAnalysisActivity", "No existing analysis found, starting new AI analysis for SMS: $body")

        try {

            val result = viewModel.analyzeSms(
                context = context,
                smsText = body
            )
            if (result != null) {

                // Update SmsMessageManager with analysis results
                messageId?.let { id ->
                    SmsMessageManager.updateMessageAnalysis(
                        messageId = id,
                        isSmishing = result.isSmishing,
                        explanation = result.explanation,
                        tips = result.tips
                    )
                }

                Log.d("SmsAnalysisActivity", "Analysis completed: ${result.isSmishing}, Explanation: ${result.explanation}, Tips: ${result.tips}")
                smsAnalysisResult = result
                analysisResult = if (result.isSmishing) AnalysisResult.SMISHING else AnalysisResult.BENIGN
                explanation = result.explanation
                analysisStatus = AnalysisStatus.COMPLETED
                Log.d("SmsAnalysisActivity", "Analysis status updated to COMPLETED")
            } else {
                Log.e("SmsAnalysisActivity", "Analysis returned null result")
                analysisStatus = AnalysisStatus.ERROR
                errorMessage = "Failed to analyze message. Please try again."
            }
        } catch (e: Exception) {
            Log.e("SmsAnalysisActivity", "Error during analysis", e)
            analysisStatus = AnalysisStatus.ERROR
            errorMessage = "Analysis failed: ${e.message}"
        }
        viewModel.unloadModel()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SMS Content Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "From: $sender",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Message:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ).padding(12.dp)
                    )
                }
            }
            
            // Model Info Card (if available)
            modelInfo?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            when (analysisStatus) {
                AnalysisStatus.PROCESSING -> {
                    // Analysis Status Card (only show when processing)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Analysis Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Analyzing message with AI...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                AnalysisStatus.COMPLETED -> {
                    analysisResult?.let { result ->
                        // Show analysis result with details
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Analysis Result Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (result) {
                                                AnalysisResult.BENIGN -> Icons.Filled.CheckCircle
                                                AnalysisResult.SMISHING -> Icons.Filled.Warning
                                            },
                                            contentDescription = null,
                                            tint = when (result) {
                                                AnalysisResult.BENIGN -> Color.Green
                                                AnalysisResult.SMISHING -> Color.Red
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        
                                        Text(
                                            text = when (result) {
                                                AnalysisResult.BENIGN -> "Message is safe"
                                                AnalysisResult.SMISHING -> "Potential smishing detected"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when (result) {
                                                AnalysisResult.BENIGN -> Color.Green
                                                AnalysisResult.SMISHING -> Color.Red
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Show details for smishing messages
                            if (result == AnalysisResult.SMISHING) {
                                // Why this may be malicious card
                                explanation?.let { exp ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Why this may be a malicious message",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            
                                            Text(
                                                text = exp,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                                
                                // What to do card
                                smsAnalysisResult?.tips?.let { tips ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "What to do",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            
                                            Text(
                                                text = tips,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                AnalysisStatus.ERROR -> {
                    // Error Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Analysis failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            }
                            
                            errorMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class AnalysisStatus {
    PROCESSING,
    COMPLETED,
    ERROR
}

enum class AnalysisResult {
    BENIGN,
    SMISHING
} 