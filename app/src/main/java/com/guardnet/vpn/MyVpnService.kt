package com.guardnet.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardnet.vpn.data.TrafficDatabase
import com.guardnet.vpn.data.TrafficLog
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MyVpnService : VpnService() {
    
    companion object {
        private const val TAG = "MyVpnService"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_MTU = 1500
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var serverChannel: java.nio.channels.DatagramChannel? = null
    private val packetCount = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private lateinit var database: TrafficDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        database = TrafficDatabase.getDatabase(this)
        createNotificationChannel()
        Log.d(TAG, "VPN Service Created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (!isRunning.get()) {
            startVpn()
        }
        
        return START_STICKY
    }
    
    private fun startVpn() {
        try {
            // Build VPN interface
            val builder = Builder()
            builder.setSession("NetGuard-Sentinel")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(VPN_MTU)
                .setBlocking(true)
                .setUnderlyingNetworks(null)
            
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                isRunning.set(true)
                logEvent("VPN_START", "VPN Service started successfully", "")
                
                // Start packet processing
                serviceScope.launch {
                    processPackets()
                }
                
                // Start network monitoring
                serviceScope.launch {
                    monitorNetworkState()
                }
                
                Log.d(TAG, "VPN Interface established")
            } else {
                logEvent("VPN_ERROR", "Failed to establish VPN interface", "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            logEvent("VPN_ERROR", "Error: ${e.message}", "")
        }
    }
    
    private suspend fun processPackets() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteBuffer.allocate(VPN_MTU)
        
        try {
            while (isRunning.get()) {
                buffer.clear()
                val length = inputStream.channel.read(buffer)
                
                if (length > 0) {
                    buffer.flip()
                    val packetData = ByteArray(length)
                    buffer.get(packetData)
                    
                    totalBytes.addAndGet(length.toLong())
                    packetCount.incrementAndGet()
                    
                    // Parse and log packet information
                    analyzePacket(packetData)
                    
                    // Forward packet (echo back for monitoring)
                    outputStream.write(packetData)
                }
                
                delay(1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packets", e)
        }
    }
    
    private fun analyzePacket(packetData: ByteArray) {
        try {
            if (packetData.size < 20) return
            
            // Parse IP header (simplified)
            val version = (packetData[0].toInt() shr 4) and 0x0F
            if (version != 4) return
            
            val headerLength = (packetData[0].toInt() and 0x0F) * 4
            if (packetData.size < headerLength) return
            
            // Extract source and destination IPs
            val srcIp = "${packetData[12].toInt() and 0xFF}.${packetData[13].toInt() and 0xFF}.${packetData[14].toInt() and 0xFF}.${packetData[15].toInt() and 0xFF}"
            val destIp = "${packetData[16].toInt() and 0xFF}.${packetData[17].toInt() and 0xFF}.${packetData[18].toInt() and 0xFF}.${packetData[19].toInt() and 0xFF}"
            
            val protocol = packetData[9].toInt() and 0xFF
            
            // Log IP packet
            serviceScope.launch {
                logEvent(
                    "IP",
                    "Packet: $srcIp -> $destIp (Protocol: ${getProtocolName(protocol)})",
                    destIp
                )
            }
            
            // Check for DNS packets (UDP port 53)
            if (protocol == 17 && packetData.size > headerLength + 8) { // UDP
                val srcPort = ((packetData[headerLength].toInt() and 0xFF) shl 8) or (packetData[headerLength + 1].toInt() and 0xFF)
                val destPort = ((packetData[headerLength + 2].toInt() and 0xFF) shl 8) or (packetData[headerLength + 3].toInt() and 0xFF)
                
                if (srcPort == 53 || destPort == 53) {
                    // Parse DNS query
                    val dnsData = packetData.drop(headerLength + 8).toByteArray()
                    if (dnsData.size > 12) {
                        val domainName = parseDomainName(dnsData, 12)
                        if (domainName.isNotEmpty()) {
                            serviceScope.launch {
                                logEvent(
                                    "DNS",
                                    "DNS Query: $domainName -> $destIp",
                                    destIp
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing packet", e)
        }
    }
    
    private fun parseDomainName(data: ByteArray, offset: Int): String {
        val domainParts = mutableListOf<String>()
        var pos = offset
        
        while (pos < data.size) {
            val length = data[pos].toInt() and 0xFF
            if (length == 0) break
            
            if (length >= 192) {
                // Compression pointer
                pos += 1
                continue
            }
            
            pos++
            if (pos + length > data.size) break
            
            val part = String(data.sliceArray(pos until pos + length))
            domainParts.add(part)
            pos += length
        }
        
        return if (domainParts.isNotEmpty()) domainParts.joinToString(".") else ""
    }
    
    private fun getProtocolName(protocol: Int): String {
        return when (protocol) {
            1 -> "ICMP"
            6 -> "TCP"
            17 -> "UDP"
            else -> "Unknown($protocol)"
        }
    }
    
    private suspend fun monitorNetworkState() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        
        while (isRunning.get()) {
            val activeNetwork = connectivityManager.activeNetworkInfo
            val isConnected = activeNetwork?.isConnectedOrConnecting == true
            
            if (!isConnected) {
                logEvent("DISCONNECT", "Internet connection lost", "")
                
                // Wait until reconnected
                while (isRunning.get() && !isConnected) {
                    delay(5000)
                    val currentState = connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
                    if (currentState) {
                        logEvent("RECONNECT", "Internet connection restored", "")
                        break
                    }
                }
            }
            
            delay(30000) // Check every 30 seconds
        }
    }
    
    private suspend fun logEvent(type: String, data: String, destinationIp: String) {
        withContext(Dispatchers.IO) {
            try {
                val log = TrafficLog(
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    data = data,
                    destinationIp = destinationIp
                )
                database.trafficLogDao().insertLog(log)
                Log.d(TAG, "Logged: $type - $data")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging event", e)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NetGuard-Sentinel VPN Monitoring"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetGuard-Sentinel")
            .setContentText("VPN Active - Monitoring Network Traffic")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        serviceScope.cancel()
        
        try {
            vpnInterface?.close()
            serverChannel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
        
        logEvent("VPN_STOP", "VPN Service stopped", "")
        Log.d(TAG, "VPN Service Destroyed")
    }
    
    override fun onRevoke() {
        super.onRevoke()
        stopSelf()
    }
}
