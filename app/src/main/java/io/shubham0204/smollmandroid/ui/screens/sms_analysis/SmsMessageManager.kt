package io.shubham0204.smollmandroid.ui.screens.sms_analysis

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SmsMessageManager {
    private const val TAG = "SmsMessageManager"
    
    private val _messages = mutableListOf<SmsMessage>()
    private val _messagesFlow = MutableStateFlow<List<SmsMessage>>(emptyList())
    
    val messages: List<SmsMessage> get() = _messages.toList()
    val messagesFlow: StateFlow<List<SmsMessage>> = _messagesFlow.asStateFlow()
    
    init {
        // Add sample messages for demonstration
        addSampleMessages()
    }
    
    private fun addSampleMessages() {
        val sampleMessages = listOf(
            SmsMessage(
                id = "sample_1",
                sender = "Bank of America",
                body = "Your account has been temporarily suspended. Please verify your identity at https://secure-boa-verify.com",
                timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
                isSmishing = true,
                explanation = "This message contains suspicious elements typical of smishing attacks: urgent action required, suspicious URL, and request for personal information.",
                tips = "• Never click on links in suspicious SMS messages\n• Banks never ask for personal information via SMS\n• Contact your bank directly using official channels"
            ),
            SmsMessage(
                id = "sample_2",
                sender = "Amazon",
                body = "Your order #12345 has been shipped and will arrive tomorrow. Track your package at amazon.com/track",
                timestamp = System.currentTimeMillis() - 7200000, // 2 hours ago
                isSmishing = false,
                explanation = "This appears to be a legitimate order notification from Amazon with a standard tracking link.",
                tips = "• This message follows typical Amazon notification patterns\n• The URL appears to be legitimate\n• No urgent action or personal information requested"
            )
        )
        
        _messages.addAll(sampleMessages)
        _messagesFlow.value = _messages.toList()
        Log.d(TAG, "Added ${sampleMessages.size} sample messages")
    }
    
    fun addMessage(sender: String, body: String): String {
        val messageId = SmsMessage.createId()
        val newMessage = SmsMessage(
            id = messageId,
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis()
        )
        
        _messages.add(0, newMessage) // Add to the top of the list
        _messagesFlow.value = _messages.toList()
        
        Log.d(TAG, "Added new message from $sender: ${body.take(50)}...")
        return messageId
    }
    
    fun updateMessageAnalysis(messageId: String, isSmishing: Boolean, explanation: String, tips: String) {
        val index = _messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val updatedMessage = _messages[index].copy(
                isSmishing = isSmishing,
                explanation = explanation,
                tips = tips
            )
            _messages[index] = updatedMessage
            _messagesFlow.value = _messages.toList()
            Log.d(TAG, "Updated analysis for message $messageId: isSmishing=$isSmishing")
        } else {
            Log.w(TAG, "Message with ID $messageId not found for analysis update")
        }
    }
    
    fun clearMessages() {
        _messages.clear()
        _messagesFlow.value = emptyList()
        Log.d(TAG, "Cleared all messages")
    }
    
    fun getMessageById(id: String): SmsMessage? {
        return _messages.find { it.id == id }
    }
} 