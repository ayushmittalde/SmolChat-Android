package io.shubham0204.smollmandroid.ui.screens.sms_analysis
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import io.shubham0204.smollmandroid.ui.screens.sms_analysis.SmsAnalysisActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers


private const val TAG = "AGSmsReceiver"

class SmsReceiver : BroadcastReceiver() {

    init {
        Log.d(TAG, "SmsReceiver initialized")
        
    }
    
    companion object {
        private var isAnalyzing = false
        private val analysisQueue = mutableListOf<AnalysisTask>()
        
        data class AnalysisTask(
            val messageId: String,
            val sender: String,
            val body: String,
            val context: Context
        )
        
        fun logReceiverStatus(context: Context) {
            Log.d(TAG, "SmsReceiver status check - App package: ${context.packageName}")
        }
        
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast received with action: ${intent.action}")
        
        when (intent.action) {
            "android.provider.Telephony.SMS_RECEIVED" -> {
                Log.d(TAG, "SMS received")
                
                val bundle = intent.extras
                if (bundle != null) {
                    Log.d(TAG, "Bundle contains keys: ${bundle.keySet()}")
                    
                    val pdus = bundle["pdus"] as Array<*>?
                    if (pdus != null) {
                        Log.d(TAG, "PDUs found: ${pdus.size}")
                        
                        val messages = arrayOfNulls<SmsMessage>(pdus.size)
                        var sender = ""
                        var messageBody = ""
                        
                        for (i in pdus.indices) {
                            val pdu = pdus[i] as ByteArray
                            val message = SmsMessage.createFromPdu(pdu)
                            messages[i] = message
                            
                            if (i == 0) {
                                sender = message.originatingAddress ?: ""
                            }
                            messageBody += message.messageBody
                        }
                        
                        Log.d(TAG, "SMS from: $sender, body: $messageBody")
                        SmsMessageManager.addMessage(sender, messageBody)
                    } else {
                        Log.w(TAG, "No PDUs found in bundle")
                    }
                } else {
                    Log.w(TAG, "No extras bundle found")
                }
            }

            else -> {
                Log.d(TAG, "Received broadcast with different action: ${intent.action}")
            }
        }
    }
    
} 