package com.guardnet.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.guardnet.vpn.data.TrafficDatabase
import com.guardnet.vpn.data.TrafficLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private lateinit var database: TrafficDatabase
    
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TrafficDatabase.getDatabase(this)
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        onStartVpn = { checkVpnPermissionAndStart() },
                        onStopVpn = { stopVpnService() },
                        database = database
                    )
                }
            }
        }
    }
    
    private fun checkVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "VPN Service Started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        stopService(intent)
        Toast.makeText(this, "VPN Service Stopped", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    database: TrafficDatabase
) {
    val context = LocalContext.current
    var isVpnActive by remember { mutableStateOf(false) }
    var totalPackets by remember { mutableStateOf(0) }
    var totalBytes by remember { mutableStateOf(0L) }
    var recentLogs by remember { mutableStateOf<List<TrafficLog>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load data periodically
    LaunchedEffect(Unit) {
        while (true) {
            totalPackets = database.trafficLogDao().getTotalPacketCount()
            totalBytes = database.trafficLogDao().getTotalBytes()
            recentLogs = database.trafficLogDao().getRecentLogs(20)
            delay(2000)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NetGuard-Sentinel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { /* Refresh */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))
                    )
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16213E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isVpnActive) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "VPN Status",
                            tint = if (isVpnActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isVpnActive) "VPN ACTIVE" else "VPN INACTIVE",
                            color = if (isVpnActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onStartVpn,
                                enabled = !isVpnActive,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start VPN")
                            }
                            Button(
                                onClick = onStopVpn,
                                enabled = isVpnActive,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop VPN")
                            }
                        }
                    }
                }
            }
            
            // Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total Packets",
                        value = totalPackets.toString(),
                        icon = Icons.Default.DataUsage,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Data",
                        value = formatBytes(totalBytes),
                        icon = Icons.Default.Storage,
                        color = Color(0xFF9C27B0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Recent Activity
            item {
                Text(
                    text = "Recent Activity",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            items(recentLogs) { log ->
                TrafficLogCard(log)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF16213E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun TrafficLogCard(log: TrafficLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F3460)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.type,
                    color = when(log.type) {
                        "DNS" -> Color(0xFF4CAF50)
                        "IP" -> Color(0xFF2196F3)
                        "DISCONNECT" -> Color(0xFFF44336)
                        else -> Color.White
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date(log.timestamp)),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.data,
                color = Color.White,
                fontSize = 12.sp
            )
            if (log.destinationIp.isNotEmpty()) {
                Text(
                    text = "Destination: ${log.destinationIp}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

@Composable
fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF2196F3),
    tertiary = Color(0xFF9C27B0),
    background = Color(0xFF0F0F1A),
    surface = Color(0xFF1A1A2E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)
