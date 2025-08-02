package io.shubham0204.smollmandroid.ui.screens.sms_analysis

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.shubham0204.smollmandroid.llm.ModelsRepository
import io.shubham0204.smollmandroid.llm.SmolLMManager
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollmandroid.data.Chat
import io.shubham0204.smollmandroid.ui.components.createAlertDialog
import android.content.Context
import io.shubham0204.smollmandroid.R
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import kotlinx.coroutines.Dispatchers
import kotlin.math.pow
import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

private const val TAG = "SmsViewModel"

data class SmsAnalysisResult(
    val isSmishing: Boolean,
    val explanation: String,
    val tips: String = "",
    val confidence: Float = 0.0f,
    val result: Boolean = false
)


sealed class SmsAnalysisScreenUIEvent {
    data object Idle : SmsAnalysisScreenUIEvent()

    sealed class DialogEvents {

        data class ToggleSelectModelListDialog(
            val visible: Boolean,
        ) : SmsAnalysisScreenUIEvent()

        data class ToggleMoreOptionsPopup(
            val visible: Boolean,
        ) : SmsAnalysisScreenUIEvent()

    }
}

@KoinViewModel
class SmsListViewModel(val context: Context,val modelsRepository: ModelsRepository,val smolLMManager: SmolLMManager) : ViewModel() {

    enum class ModelLoadingState {
        NOT_LOADED, // model loading not started
        IN_PROGRESS, // model loading in-progress
        SUCCESS, // model loading finished successfully
        FAILURE, // model loading failed
    }

    // UI state variables
    private val _smsAnalysisChat = MutableStateFlow(
        Chat(
            id = -1L,
            name = "SMS Analysis",
            llmModelId = -1L,
            chatTemplate = "",
            systemPrompt = "",
            minP = 0.05f,
            temperature = 0.2f,
            contextSize = 2048,
            nThreads = 8,
            useMmap = true,
            useMlock = false,
            contextSizeConsumed = 0,
        )
    )

    private var activityManager: ActivityManager

    // UI state variables
    val smsAnalysisChat: StateFlow<Chat> = _smsAnalysisChat.asStateFlow()

    private val _modelLoadState = MutableStateFlow(ModelLoadingState.NOT_LOADED)
    val modelLoadState: StateFlow<ModelLoadingState> = _modelLoadState

    private val _uiEvent = MutableStateFlow(SmsAnalysisScreenUIEvent.Idle)
    val uiEvent: StateFlow<SmsAnalysisScreenUIEvent> = _uiEvent

    private val _showSelectModelListDialogState = MutableStateFlow(false)
    val showSelectModelListDialogState: StateFlow<Boolean> = _showSelectModelListDialogState

    private val _showMoreOptionsPopupState = MutableStateFlow(false)
    val showMoreOptionsPopupState: StateFlow<Boolean> = _showMoreOptionsPopupState

    private val _showRAMUsageLabel = MutableStateFlow(false)
    val showRAMUsageLabel: StateFlow<Boolean> = _showRAMUsageLabel

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    val messages: List<SmsMessage> get() = SmsMessageManager.messages

    init {
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    fun addMessage(sender: String, body: String) {
        SmsMessageManager.addMessage(sender, body)
    }


    override fun onCleared() {
        super.onCleared()
        unloadModel()
    }

    
    fun updateMessageAnalysis(messageId: String, isSmishing: Boolean, explanation: String, tips: String) {
        SmsMessageManager.updateMessageAnalysis(messageId, isSmishing, explanation, tips)
    }
    
    fun clearMessages() {
        SmsMessageManager.clearMessages()
    }

    fun deleteModel(modelId: Long) {
        modelsRepository.deleteModel(modelId)
        if (_smsAnalysisChat.value.llmModelId == modelId) {
            _smsAnalysisChat.value = _smsAnalysisChat.value.copy(llmModelId = -1)
        }
    }

    fun getModelName(): String{
        val chat = _smsAnalysisChat.value
        val model = modelsRepository.getModelFromId(chat.llmModelId)
        if (model != null) {
            return model.name
        }
        else{
            return "Model not found"
        }
    }

    fun loadModel(onComplete: (ModelLoadingState) -> Unit = {}) {
            val chat = _smsAnalysisChat.value
            val model = modelsRepository.getModelFromId(chat.llmModelId)
            if (chat.llmModelId == -1L || model == null) {
                _showSelectModelListDialogState.value = true
            } else {
                _modelLoadState.value = ModelLoadingState.IN_PROGRESS
                smolLMManager.load(
                    chat,
                    model.path,
                    SmolLM.InferenceParams(
                        chat.minP,
                        chat.temperature,
                        false,
                        chat.contextSize.toLong(),
                        chat.chatTemplate,
                        chat.nThreads,
                        chat.useMmap,
                        chat.useMlock,
                    ),
                    onError = { e ->
                        _modelLoadState.value = ModelLoadingState.FAILURE
                        onComplete(ModelLoadingState.FAILURE)
                        createAlertDialog(
                            dialogTitle = context.getString(R.string.dialog_err_title),
                            dialogText = context.getString(R.string.dialog_err_text, e.message),
                            dialogPositiveButtonText = context.getString(R.string.dialog_err_change_model),
                            onPositiveButtonClick = {
                                onEvent(
                                    SmsAnalysisScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(
                                        visible = true,
                                    ),
                                )
                            },
                            dialogNegativeButtonText = context.getString(R.string.dialog_err_close),
                            onNegativeButtonClick = {},
                        )
                    },
                    onSuccess = {
                        _modelLoadState.value = ModelLoadingState.SUCCESS
                        onComplete(ModelLoadingState.SUCCESS)
                    },
                )
            }
        }

    suspend fun analyzeSms(context: Context, smsText: String, ):SmsAnalysisResult? = withContext(Dispatchers.IO){
        try {

            Log.d(TAG, "Starting SMS analysis with model")
            val prompt = createSmishingDetectionPrompt(smsText)
            // Log the final prompt for debugging
            Log.d(TAG, "Final prompt length: ${prompt.length} characters")
            Log.d(TAG, "Final prompt: $prompt")
            // Verify prompt is not empty
            if (prompt.trim().isEmpty()) {
                Log.e(TAG, "Prompt is empty, cannot proceed with analysis")
                return@withContext null
            }

            var analysisResult: SmsAnalysisResult? = null
            var isCompleted = false
            var fullResponse = ""

            // Run the inference


            fullResponse = smolLMManager.getSMSAnalysis(prompt).toString()
            if (fullResponse == "") {
                Log.e(TAG, "Failed to get SMS analysis result")
                return@withContext null
            }

            // We dont need to reset the session , store chat is false while model initialization

            analysisResult = parseAnalysisResult(fullResponse)
            Log.d(TAG, "Analysis result parsed: $analysisResult")
            isCompleted = true

            return@withContext analysisResult
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS analysis", e)
            null
        }
    }

    private fun createSmishingDetectionPrompt(smsText: String): String {
        return """
## GOAL ##
Classify SMS messages as 'smishing' or 'benign' based solely on **intent to deceive or defraud**, not on emotion, tone, or urgency.
$smsText
""".trim()
    }

    /**
     * Parse the AI response to extract classification, explanation, and tips
     * This is a robust parser that handles various model output formats
     */
    private fun parseAnalysisResult(response: String): SmsAnalysisResult {
        Log.d(TAG, "Parsing response: '$response'")

        try {
            // Replace literal \n with actual newlines and split properly
            val cleanResponse = response.replace("\\n", "\n")
            val lines = cleanResponse.split("\n")
            Log.d(TAG, "Split into ${lines.size} lines")
            Log.d(TAG, "Lines: ${lines.take(5)}") // Log first 5 lines for debugging
            var classification = ""
            var explanation = ""
            var tips = ""
            var inTipsSection = false
            var tipsLines = mutableListOf<String>()

            for (line in lines) {
                val trimmedLine = line.trim()
                Log.d(TAG, "Processing line: '$trimmedLine'")
                when {
                    // Handle classification in various formats
                    trimmedLine.startsWith("CLASSIFICATION:", ignoreCase = true) -> {
                        classification = trimmedLine.substringAfter(":").trim().lowercase()
                        Log.d(TAG, "Found classification: '$classification'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## CLASSIFICATION:", ignoreCase = true) -> {
                        classification = trimmedLine.substringAfter("## CLASSIFICATION:").trim().lowercase()
                        Log.d(TAG, "Found classification: '$classification'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## Classification:", ignoreCase = true) -> {
                        classification = trimmedLine.substringAfter("## Classification:").trim().lowercase()
                        Log.d(TAG, "Found classification: '$classification'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## classification:", ignoreCase = true) -> {
                        classification = trimmedLine.substringAfter("## classification:").trim().lowercase()
                        Log.d(TAG, "Found classification: '$classification'")
                        inTipsSection = false
                    }

                    // Handle explanation in various formats
                    trimmedLine.startsWith("EXPLANATION:", ignoreCase = true) -> {
                        explanation = trimmedLine.substringAfter(":").trim()
                        Log.d(TAG, "Found explanation: '$explanation'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## EXPLANATION:", ignoreCase = true) -> {
                        explanation = trimmedLine.substringAfter("## EXPLANATION:").trim()
                        Log.d(TAG, "Found explanation: '$explanation'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## Explanation:", ignoreCase = true) -> {
                        explanation = trimmedLine.substringAfter("## Explanation:").trim()
                        Log.d(TAG, "Found explanation: '$explanation'")
                        inTipsSection = false
                    }
                    trimmedLine.startsWith("## explanation:", ignoreCase = true) -> {
                        explanation = trimmedLine.substringAfter("## explanation:").trim()
                        Log.d(TAG, "Found explanation: '$explanation'")
                        inTipsSection = false
                    }

                    // Handle tips section start
                    trimmedLine.startsWith("TIPS:", ignoreCase = true) -> {
                        inTipsSection = true
                        val tipsContent = trimmedLine.substringAfter(":").trim()
                        if (tipsContent.isNotEmpty()) {
                            tipsLines.add(tipsContent)
                        }
                        Log.d(TAG, "Found tips section")
                    }
                    trimmedLine.startsWith("## TIPS:", ignoreCase = true) -> {
                        inTipsSection = true
                        val tipsContent = trimmedLine.substringAfter("## TIPS:").trim()
                        if (tipsContent.isNotEmpty()) {
                            tipsLines.add(tipsContent)
                        }
                        Log.d(TAG, "Found tips section")
                    }
                    trimmedLine.startsWith("## Tips:", ignoreCase = true) -> {
                        inTipsSection = true
                        val tipsContent = trimmedLine.substringAfter("## Tips:").trim()
                        if (tipsContent.isNotEmpty()) {
                            tipsLines.add(tipsContent)
                        }
                        Log.d(TAG, "Found tips section")
                    }
                    trimmedLine.startsWith("## tips:", ignoreCase = true) -> {
                        inTipsSection = true
                        val tipsContent = trimmedLine.substringAfter("## tips:").trim()
                        if (tipsContent.isNotEmpty()) {
                            tipsLines.add(tipsContent)
                        }
                        Log.d(TAG, "Found tips section")
                    }

                    // Collect all lines in tips section as raw text
                    inTipsSection && trimmedLine.isNotEmpty() && !trimmedLine.startsWith("##") -> {
                        Log.d(TAG, "In tips section, processing line: '$trimmedLine'")
                        tipsLines.add(trimmedLine)
                        Log.d(TAG, "Added to tips lines: '$trimmedLine'")
                    }
                }
            }

            // Join all tips lines into a single string
            tips = tipsLines.joinToString(" ").trim()

            // Check if classification contains "smishing" (case insensitive)
            val isSmishing = classification.contains("smishing", ignoreCase = true)
            Log.d(TAG, "Final parsed values:")
            Log.d(TAG, "  Classification: '$classification'")
            Log.d(TAG, "  Explanation: '$explanation'")
            Log.d(TAG, "  Tips: '$tips'")
            Log.d(TAG, "  IsSmishing: $isSmishing")

            // If no tips found, create a fallback tip based on the explanation
            if (tips.isEmpty()) {
                tips = createFallbackTipsText(isSmishing, explanation)
            }

            return SmsAnalysisResult(
                isSmishing = isSmishing,
                explanation = explanation.ifEmpty {
                    if (isSmishing) "Message contains suspicious content"
                    else "Message appears to be legitimate"
                },
                tips = tips
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing analysis result", e)
            return SmsAnalysisResult(
                isSmishing = false,
                explanation = "Unable to analyze message",
                tips = createFallbackTipsText(false, "Unable to analyze message")
            )
        }
    }

    /**
     * Create fallback tips text based on classification and explanation
     */
    private fun createFallbackTipsText(isSmishing: Boolean, explanation: String): String {
        return if (isSmishing) {
            "Don't click on suspicious links. Never share personal information via SMS. Be wary of urgent or threatening messages."
        } else {
            "Message appears to be legitimate. Continue with normal communication. No action required."
        }
    }

    fun onEvent(event: SmsAnalysisScreenUIEvent) {
        when (event) {
            is SmsAnalysisScreenUIEvent.DialogEvents.ToggleSelectModelListDialog -> {
                _showSelectModelListDialogState.value = event.visible
            }

            is SmsAnalysisScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup -> {
                _showMoreOptionsPopupState.value = event.visible
            }

            else -> {}
        }
    }

    fun toggleRAMUsageLabelVisibility() {
        _showRAMUsageLabel.value = !_showRAMUsageLabel.value
    }

    /**
     * Clears the resources occupied by the model only
     * if the inference is not in progress.
     */
    fun unloadModel(): Boolean =
        if (!smolLMManager.isInferenceOn) {
            smolLMManager.close()
            _modelLoadState.value = ModelLoadingState.NOT_LOADED
            true
        } else {
            false
        }

    fun updateChat(chat: Chat) {
        _smsAnalysisChat.value = chat
        loadModel()
    }

    fun updateChatLLMParams(
        modelId: Long,
        chatTemplate: String,
    ) {
        _smsAnalysisChat.value =
            _smsAnalysisChat.value.copy(llmModelId = modelId, chatTemplate = chatTemplate)
    }


    /**
     * Get the current memory usage of the device.
     * This method returns the memory consumed (in GBs) and the total
     * memory available on the device (in GBs)
     */
    fun getCurrentMemoryUsage(): Pair<Float, Float> {
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = (memoryInfo.totalMem) / 1024.0.pow(3.0)
        val usedMemory = (memoryInfo.availMem) / 1024.0.pow(3.0)
        return Pair(usedMemory.toFloat(), totalMemory.toFloat())
    }

    @SuppressLint("StringFormatMatches")
    fun showContextLengthUsageDialog() {
        _smsAnalysisChat.value?.let { chat ->
            createAlertDialog(
                dialogTitle = context.getString(R.string.dialog_ctx_usage_title),
                dialogText =
                    context.getString(
                        R.string.dialog_ctx_usage_text,
                        chat.contextSizeConsumed,
                        chat.contextSize,
                    ),
                dialogPositiveButtonText = context.getString(R.string.dialog_ctx_usage_close),
                onPositiveButtonClick = {},
                dialogNegativeButtonText = null,
                onNegativeButtonClick = null,
            )
        }
    }


} 