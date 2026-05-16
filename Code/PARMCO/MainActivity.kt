package com.example.parmco

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Builds
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parmco.ui.theme.PARMCOTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.*

// Standard UUID for Bluetooth SPP (Serial Port Profile) communication
private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// MAC address of the Raspberry Pi 4 Bluetooth adapter
private const val RP4_MAC_ADDRESS = "2C:CF:67:53:61:32"

/**
 * Sealed class representing all possible motor control commands
 * Each command has a string representation sent via Bluetooth and a display label
 */
sealed class MotorCommand(val commandString: String, val label: String) {
    // Start the motor (enables motor driver)
    data object Start : MotorCommand("START", "Start Motor")

    // Stop the motor (disables motor driver)
    data object Stop : MotorCommand("STOP", "Stop Motor")

    // Set motor direction to forward
    data object Forward : MotorCommand("FORWARD", "Forward")

    // Set motor direction to reverse
    data object Reverse : MotorCommand("REVERSE", "Reverse")

    // Set PWM duty cycle for manual speed control (0-100%)
    data class SetPWM(val value: Int) : MotorCommand("$value", "Set Speed $value")

    // Set target RPM for automatic speed control (P-controller)
    data class SetTargetRPM(val value: Int) : MotorCommand("TARGET:$value", "Set Target RPM $value")
}

/**
 * Main Activity for PARMCO motor control application
 * Handles Bluetooth connection, motor control, and UI rendering
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    // Lazy initialization of BluetoothManager system service
    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    // Lazy initialization of BluetoothAdapter for device connectivity
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    // Connection status - triggers UI recomposition when changed
    var isConnected by mutableStateOf(false)
        private set

    // Current motor speed/duty cycle (0-100 for manual, 0-6500 for auto mode)
    var currentSpeed by mutableIntStateOf(0)
        private set

    // Current motor RPM reading from IR sensor
    var rpmValue by mutableIntStateOf(0)
        private set

    // Control mode: false = manual (PWM), true = automatic (RPM target)
    var isAutomaticMode by mutableStateOf(false)
        private set

    // Party mode unlock status (unlocked via secret game)
    var partyModeUnlocked by mutableStateOf(false)
        private set

    // Party mode active status (causes sinusoidal motor pulsing)
    var isPartyMode by mutableStateOf(false)
        private set

    // Active Bluetooth socket for communication
    private var btSocket: BluetoothSocket? = null

    // Thread for reading incoming data from Raspberry Pi
    private var readThread: ReadThread? = null

    // Handler for posting UI updates from background threads
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Permission launcher for requesting Bluetooth permissions
     * Required permissions vary by Android version
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if required permissions were granted based on Android version
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (granted) {
            checkAndEnableBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Launcher for Bluetooth enable request
     * Prompts user to enable Bluetooth if disabled
     */
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            connectToRaspberryPi()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PARMCOTheme {
                // State for showing/hiding the secret game
                var showGame by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                /**
                 * Party mode effect - creates sinusoidal motor speed variation
                 * Runs continuously while party mode is active and device is connected
                 */
                LaunchedEffect(isPartyMode, isConnected) {
                    if (isPartyMode && isConnected) {
                        launch {
                            var time = 0f
                            // Start motor before pulsing
                            writeToBluetooth(MotorCommand.Start)
                            delay(100)

                            // Continuously modulate motor speed with sine wave
                            while (isActive && isPartyMode && isConnected) {
                                // Generate duty cycle between 50-100% using sine wave
                                val duty = (sin(time) * 25 + 75).toInt().coerceIn(50, 100)
                                writeToBluetooth(MotorCommand.SetPWM(duty))
                                time += 0.5f
                                delay(80)
                            }

                            // Stop motor when party mode ends
                            if (isConnected) {
                                writeToBluetooth(MotorCommand.Stop)
                            }
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showGame) {
                        // Display secret game screen for unlocking party mode
                        SecretGameScreen(
                            onComplete = {
                                partyModeUnlocked = true
                                showGame = false
                                Toast.makeText(this@MainActivity, "🎉 PARTY MODE UNLOCKED! 🎉", Toast.LENGTH_LONG).show()
                            },
                            onBack = {
                                showGame = false
                            }
                        )
                    } else {
                        // Main motor control interface
                        MotorControlApp(
                            isConnected = isConnected,
                            currentSpeed = currentSpeed,
                            currentRPM = rpmValue,
                            isAutomaticMode = isAutomaticMode,
                            partyModeUnlocked = partyModeUnlocked,
                            isPartyMode = isPartyMode,
                            onConnectClick = {
                                if (isConnected) {
                                    disconnectFromRaspberryPi()
                                } else {
                                    requestBluetoothPermissions()
                                }
                            },
                            onSendCommand = ::writeToBluetooth,
                            onSpeedChange = { speed ->
                                currentSpeed = speed
                            },
                            onModeToggle = { automatic ->
                                isAutomaticMode = automatic
                                currentSpeed = 0 // Reset speed when switching modes
                            },
                            onPartyModeToggle = { enabled ->
                                isPartyMode = enabled
                            },
                            onSecretUnlock = {
                                if (!partyModeUnlocked) {
                                    showGame = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Request required Bluetooth permissions based on Android version
     * Android 12+ requires BLUETOOTH_CONNECT/SCAN
     * Older versions require ACCESS_FINE_LOCATION
     */
    private fun requestBluetoothPermissions() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        permissionLauncher.launch(permissionsToRequest)
    }

    /**
     * Check if Bluetooth is enabled, prompt user to enable if not
     * Proceeds to connection if already enabled
     */
    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            connectToRaspberryPi()
        }
    }

    /**
     * Disconnect from Raspberry Pi and clean up resources
     */
    private fun disconnectFromRaspberryPi() {
        closeBluetoothResources()
    }

    /**
     * Establish Bluetooth RFCOMM connection to Raspberry Pi
     * Attempts standard connection first, falls back to insecure connection if needed
     * Runs on background thread to avoid blocking UI
     */
    @SuppressLint("MissingPermission")
    private fun connectToRaspberryPi() {
        // Validate MAC address format
        if (!BluetoothAdapter.checkBluetoothAddress(RP4_MAC_ADDRESS)) {
            mainHandler.post {
                Toast.makeText(this, "Invalid MAC address", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Get remote Bluetooth device by MAC address
        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(RP4_MAC_ADDRESS)
        } catch (e: IllegalArgumentException) {
            mainHandler.post {
                Toast.makeText(this, "Invalid MAC address", Toast.LENGTH_LONG).show()
            }
            return
        }

        if (device == null) {
            mainHandler.post {
                Toast.makeText(this, "Device not found", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Clean up any existing connections
        closeBluetoothResources()

        // Perform connection on background thread
        thread {
            try {
                // Cancel any ongoing device discovery to improve connection reliability
                bluetoothAdapter?.cancelDiscovery()

                try {
                    // Attempt standard secure RFCOMM connection
                    btSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                    btSocket?.connect()
                } catch (e: IOException) {
                    // Fallback: try insecure connection using reflection
                    // Some devices require insecure connection method
                    try {
                        val method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        @Suppress("UNCHECKED_CAST")
                        btSocket = method.invoke(device, 1) as BluetoothSocket?
                        if (btSocket == null) {
                            throw IOException("Failed to create socket")
                        }
                        btSocket?.connect()
                    } catch (e2: Exception) {
                        throw e2
                    }
                }

                // Verify connection and start read thread
                if (btSocket?.isConnected == true) {
                    mainHandler.post {
                        isConnected = true
                        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                    }
                    readThread = ReadThread(btSocket!!)
                    readThread?.start()
                } else {
                    throw IOException("Connection failed")
                }

            } catch (e: InvocationTargetException) {
                handleConnectionFailure(e.targetException?.message ?: "Connection error")
            } catch (e: Exception) {
                handleConnectionFailure(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Handle connection failure by updating UI and cleaning up resources
     */
    private fun handleConnectionFailure(message: String) {
        mainHandler.post {
            isConnected = false
            Toast.makeText(this, "Connection failed", Toast.LENGTH_LONG).show()
            closeBluetoothResources()
        }
    }

    /**
     * Send a motor command to Raspberry Pi via Bluetooth
     * Commands are sent as newline-terminated strings
     * @param command The MotorCommand to send
     */
    fun writeToBluetooth(command: MotorCommand) {
        if (!isConnected || btSocket == null) {
            // Suppress toast for continuous commands (PWM/RPM updates)
            if (command !is MotorCommand.SetPWM && command !is MotorCommand.SetTargetRPM) {
                Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val outputStream: OutputStream? = btSocket?.outputStream
        val message = command.commandString + "\n" // Newline for protocol framing
        try {
            outputStream?.write(message.toByteArray())
        } catch (e: IOException) {
            // Connection lost - clean up and notify user
            mainHandler.post {
                Toast.makeText(this, "Connection lost", Toast.LENGTH_LONG).show()
            }
            closeBluetoothResources()
        }
    }

    /**
     * Close all Bluetooth resources and reset state
     * Called on disconnect or connection failure
     */
    private fun closeBluetoothResources() {
        // Stop and clean up read thread
        readThread?.cancel()
        readThread = null

        // Close socket
        try {
            btSocket?.close()
            btSocket = null
        } catch (e: IOException) {
            Log.e("Bluetooth", "Could not close socket: ${e.message}")
        }

        // Reset UI state on main thread
        mainHandler.post {
            isConnected = false
            currentSpeed = 0
            rpmValue = 0
            isPartyMode = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeBluetoothResources()
    }

    /**
     * Background thread for reading incoming RPM data from Raspberry Pi
     * Parses newline-delimited integer RPM values
     */
    private inner class ReadThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val buffer = ByteArray(1024)
        private val readBuffer = StringBuilder()

        override fun run() {
            while (true) {
                try {
                    // Read available bytes from input stream
                    val bytes = mmInStream.read(buffer)
                    if (bytes == -1) {
                        throw IOException("End of stream")
                    }
                    val incomingMessage = String(buffer, 0, bytes)
                    readBuffer.append(incomingMessage)

                    // Process complete lines (newline-delimited)
                    while (readBuffer.contains("\n")) {
                        val lineEnd = readBuffer.indexOf("\n")
                        val line = readBuffer.substring(0, lineEnd).trim()
                        readBuffer.delete(0, lineEnd + 1)

                        // Parse RPM value and update UI
                        try {
                            val rpm = line.toInt()
                            mainHandler.post {
                                rpmValue = rpm
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("Bluetooth", "Invalid data: $line")
                        }
                    }
                } catch (e: IOException) {
                    // Connection lost - clean up
                    closeBluetoothResources()
                    break
                }
            }
        }

        /**
         * Cancel the read thread and close socket
         */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("Bluetooth", "Close failed: ${e.message}")
            }
        }
    }
}

// Custom color palette for dark theme UI
private val DarkBackground = Color(0xFF0A0E1A)
private val CardBackground = Color(0xFF1A1F35)
private val AccentBlue = Color(0xFF00D9FF)
private val AccentPurple = Color(0xFF9D4EDD)
private val AccentGreen = Color(0xFF00F5A0)
private val AccentRed = Color(0xFFFF4757)
private val AccentYellow = Color(0xFFFFA502)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB4B9C9)

/**
 * Main motor control UI composable
 * Displays connection status, RPM, control mode, speed dial, and motor controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorControlApp(
    isConnected: Boolean,
    currentSpeed: Int,
    currentRPM: Int,
    isAutomaticMode: Boolean,
    partyModeUnlocked: Boolean,
    isPartyMode: Boolean,
    onConnectClick: () -> Unit,
    onSendCommand: (MotorCommand) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onModeToggle: (Boolean) -> Unit,
    onPartyModeToggle: (Boolean) -> Unit,
    onSecretUnlock: () -> Unit
) {
    // Track motor direction (forward/reverse)
    var isForward by remember { mutableStateOf(true) }

    // Send direction command when direction changes or connection established
    LaunchedEffect(isForward, isConnected) {
        if (isConnected && !isPartyMode) {
            onSendCommand(if (isForward) MotorCommand.Forward else MotorCommand.Reverse)
        }
    }

    // Main container with gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        Color(0xFF0D1123),
                        Color(0xFF050810)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App title
            Text(
                text = "MOTOR CONTROL",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Connection status and RPM display row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionStatusCard(
                    isConnected = isConnected,
                    onConnectClick = onConnectClick,
                    modifier = Modifier.weight(1.5f).height(100.dp)
                )

                RPMDisplayCard(
                    currentRPM = currentRPM,
                    onSecretUnlock = onSecretUnlock,
                    partyModeUnlocked = partyModeUnlocked,
                    modifier = Modifier.weight(1f).height(100.dp)
                )
            }

            // Control mode toggle (Manual/Automatic)
            ModeToggleCard(
                isAutomaticMode = isAutomaticMode,
                onModeToggle = onModeToggle,
                enabled = isConnected && !isPartyMode,
                modifier = Modifier.fillMaxWidth().height(85.dp)
            )

            // Party mode card (only shown when unlocked)
            if (partyModeUnlocked) {
                PartyModeCard(
                    isPartyMode = isPartyMode,
                    onToggle = onPartyModeToggle,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth().height(75.dp)
                )
            }

            // Central rotary speed dial
            RotarySpeedDial(
                currentSpeed = currentSpeed,
                onSpeedChange = onSpeedChange,
                enabled = isConnected && !isPartyMode,
                onSendCommand = onSendCommand,
                isAutomaticMode = isAutomaticMode,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Direction toggle and start/stop controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DirectionToggleCard(
                    isForward = isForward,
                    onToggle = { isForward = it },
                    enabled = isConnected && !isPartyMode,
                    modifier = Modifier.weight(1f)
                )

                StartStopControl(
                    onSendCommand = onSendCommand,
                    enabled = isConnected && !isPartyMode,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Secret challenge game for unlocking party mode
 * Timing-based game where user must tap when dot is in green zone
 * Difficulty increases with each successful level
 */
@Composable
fun SecretGameScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var gameStarted by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0f) } // Dot position (0-1000)
    var direction by remember { mutableStateOf(1) } // Movement direction (+1 or -1)
    var score by remember { mutableStateOf(0) } // Current score/level
    val targetScore = 5 // Score needed to win

    // Calculate difficulty: speed increases and zone shrinks with each level
    val speed = 4f + (score * 2.5f)
    val zoneWidth = 0.22f - (score * 0.05f)

    // Animate dot movement
    LaunchedEffect(gameStarted, score) {
        while (gameStarted && score < targetScore) {
            delay(16) // ~60fps
            position += direction * speed
            // Bounce at edges
            if (position >= 1000f || position <= 0f) {
                direction *= -1
            }
        }

        // Victory - trigger completion after delay
        if (score >= targetScore) {
            delay(1500)
            onComplete()
        }
    }

    // Calculate target zone position (centered)
    val zoneStart = 0.5f - (zoneWidth / 2f)
    val zoneEnd = 0.5f + (zoneWidth / 2f)

    // Game UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0E2E),
                        Color(0xFF0D0520),
                        Color(0xFF050810)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = if (score >= targetScore) "🎉 VICTORY! 🎉" else "SECRET CHALLENGE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (score >= targetScore) AccentYellow else AccentPurple,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Instructions/status
            Text(
                text = if (!gameStarted) {
                    "Tap when the dot is in the green zone!\nScore $targetScore points to unlock.\n⚠️ WARNING: Gets MUCH harder each level!"
                } else if (score >= targetScore) {
                    "Unlocking Party Mode..."
                } else {
                    "Level ${score + 1} / $targetScore"
                },
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Game canvas - moving dot and target zone
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .border(2.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Calculate zone boundaries
                    val targetZoneStart = size.width * zoneStart
                    val targetZoneEnd = size.width * zoneEnd

                    // Draw target zone (green area)
                    drawRect(
                        color = AccentGreen.copy(alpha = 0.2f),
                        topLeft = Offset(targetZoneStart, 0f),
                        size = Size(targetZoneEnd - targetZoneStart, size.height)
                    )

                    // Draw zone boundaries
                    drawLine(
                        color = AccentGreen,
                        start = Offset(targetZoneStart, 0f),
                        end = Offset(targetZoneStart, size.height),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = AccentGreen,
                        start = Offset(targetZoneEnd, 0f),
                        end = Offset(targetZoneEnd, size.height),
                        strokeWidth = 3f
                    )

                    // Calculate dot position
                    val dotX = (position / 1000f) * size.width
                    val dotY = size.height / 2f

                    // Draw dot glow
                    drawCircle(
                        color = AccentBlue.copy(alpha = 0.3f),
                        radius = 28f,
                        center = Offset(dotX, dotY)
                    )

                    // Draw dot
                    drawCircle(
                        color = AccentBlue,
                        radius = 18f,
                        center = Offset(dotX, dotY)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Tap button - starts game or checks position
            Button(
                onClick = {
                    if (!gameStarted) {
                        gameStarted = true
                    } else if (score < targetScore) {
                        // Check if dot is in target zone
                        val dotPosition = position / 1000f
                        if (dotPosition in zoneStart..zoneEnd) {
                            score++ // Success
                        } else {
                            score = maxOf(0, score - 1) // Penalty (min 0)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple.copy(alpha = 0.3f),
                    contentColor = AccentPurple
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = score < targetScore
            ) {
                Text(
                    text = if (!gameStarted) "START" else "TAP!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Back button (only shown before victory)
            if (score < targetScore) {
                TextButton(onClick = onBack) {
                    Text("Back to Controls", color = TextSecondary)
                }
            }
        }
    }
}

/**
 * Party mode control card
 * Toggles sinusoidal motor pulsing effect
 */
@Composable
fun PartyModeCard(
    isPartyMode: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulsing animation when party mode is active
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = modifier
            .pointerInput(enabled) {
                detectTapGestures {
                    if (enabled) {
                        onToggle(!isPartyMode)
                    }
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPartyMode) AccentYellow.copy(alpha = 0.2f) else CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Party emoji and label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (isPartyMode) Modifier.scale(pulseScale) else Modifier
            ) {
                Text(
                    text = "🎉",
                    fontSize = 28.sp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "PARTY MODE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPartyMode) AccentYellow else TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isPartyMode) "ACTIVE - Motor pulsing!" else "Tap to activate",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Play/stop icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (isPartyMode) AccentYellow.copy(alpha = 0.3f) else Color(0xFF1A1F35),
                        CircleShape
                    )
                    .border(
                        2.dp,
                        if (isPartyMode) AccentYellow else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPartyMode) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (enabled) (if (isPartyMode) AccentYellow else TextSecondary) else TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Control mode toggle card
 * Switches between Manual (PWM) and Automatic (RPM target) modes
 */
@Composable
fun ModeToggleCard(
    isAutomaticMode: Boolean,
    onModeToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CONTROL MODE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(10.dp))

            // Manual and Auto buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeButton(
                    text = "MANUAL",
                    isSelected = !isAutomaticMode,
                    onClick = { if (enabled) onModeToggle(false) },
                    enabled = enabled,
                    color = AccentBlue,
                    modifier = Modifier.weight(1f)
                )

                ModeButton(
                    text = "AUTO",
                    isSelected = isAutomaticMode,
                    onClick = { if (enabled) onModeToggle(true) },
                    enabled = enabled,
                    color = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual mode selection button
 * Used in mode toggle card for Manual/Auto selection
 */
@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Scale animation when selected
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> CardBackground.copy(alpha = 0.3f)
            isSelected -> color.copy(alpha = 0.2f)
            else -> CardBackground
        },
        animationSpec = tween(300),
        label = "bg"
    )

    // Border color animation
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isSelected -> color
            else -> color.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "border"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .scale(animatedScale)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(enabled) {
                detectTapGestures { if (enabled) onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (enabled) (if (isSelected) color else TextSecondary) else TextSecondary.copy(alpha = 0.3f)
        )
    }
}

/**
 * Rotary speed dial control
 * Central UI element for setting motor speed/RPM
 * Responds to drag gestures for intuitive control
 * Displays as circular gauge with rotating needle
 */
@Composable
fun RotarySpeedDial(
    currentSpeed: Int,
    onSpeedChange: (Int) -> Unit,
    enabled: Boolean,
    onSendCommand: (MotorCommand) -> Unit,
    isAutomaticMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var updateJob by remember { mutableStateOf<Job?>(null) }

    // Speed range depends on control mode
    val maxValue = if (isAutomaticMode) 6500f else 100f

    // Dial angle configuration (270° sweep starting at 135°)
    val angleRange = 270f
    val startAngle = 135f
    val endAngle = startAngle + angleRange

    // Calculate target angle based on current speed
    val targetAngle by remember(currentSpeed, isAutomaticMode) {
        derivedStateOf { (currentSpeed / maxValue) * angleRange + startAngle }
    }

    // Smooth needle animation
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "angle"
    )

    // Drag gesture handling for speed adjustment
    val interactionModifier = if (enabled) Modifier.pointerInput(isAutomaticMode) {
        detectDragGestures(
            onDragStart = { updateJob?.cancel() },
            onDragEnd = {
                // Send final command when drag ends
                updateJob?.cancel()
                if (currentSpeed > 0) {
                    val command = if (isAutomaticMode) {
                        MotorCommand.SetTargetRPM(currentSpeed)
                    } else {
                        MotorCommand.SetPWM(currentSpeed)
                    }
                    onSendCommand(command)
                    // Send twice for reliability
                    coroutineScope.launch {
                        delay(150)
                        onSendCommand(command)
                    }
                }
            },
            onDrag = { change, _ ->
                change.consume()
                val center = Offset(size.width / 2f, size.height / 2f)

                // Calculate angle from drag position
                val angle = atan2(change.position.y - center.y, change.position.x - center.x)
                var angleDeg = angle * 180f / PI.toFloat()
                if (angleDeg < 0) angleDeg += 360f

                // Handle angle wrapping (convert to continuous 0-360+ range)
                var continuousAngle = angleDeg
                if (continuousAngle in 0f..45f) continuousAngle += 360f

                // Clamp to valid dial range
                if (continuousAngle !in startAngle..endAngle) {
                    continuousAngle = if (abs(continuousAngle - startAngle) < abs(continuousAngle - endAngle)) {
                        startAngle
                    } else {
                        endAngle
                    }
                }

                // Convert angle to speed value
                val normalizedAngle = (continuousAngle - startAngle).coerceIn(0f, angleRange)
                val newSpeed = ((normalizedAngle / angleRange) * maxValue).toInt().coerceIn(0, maxValue.toInt())

                // Update speed and send command with debouncing
                if (newSpeed != currentSpeed) {
                    onSpeedChange(newSpeed)
                    if (newSpeed > 0) {
                        updateJob?.cancel()
                        updateJob = coroutineScope.launch {
                            delay(10) // Debounce
                            val command = if (isAutomaticMode) {
                                MotorCommand.SetTargetRPM(newSpeed)
                            } else {
                                MotorCommand.SetPWM(newSpeed)
                            }
                            onSendCommand(command)
                        }
                    }
                }
            }
        )
    } else Modifier

    Card(
        modifier = modifier.then(interactionModifier),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Draw gauge background, progress arc, tick marks, and needle
            Canvas(modifier = Modifier.fillMaxSize(0.92f)) {
                val radius = size.minDimension / 2f
                val strokeWidth = 14.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)

                // Background arc (gray)
                drawArc(
                    color = Color(0xFF1A1F35),
                    startAngle = startAngle,
                    sweepAngle = angleRange,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc color (blue for manual, purple for auto)
                val dialColor = if (isAutomaticMode) AccentPurple else AccentBlue
                val progressSweep = (currentSpeed / maxValue) * angleRange

                // Draw progress arc with gradient
                if (enabled && currentSpeed > 0) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                dialColor.copy(alpha = 0.3f),
                                dialColor,
                                dialColor
                            ),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        blendMode = BlendMode.Plus
                    )
                }

                // Draw tick marks (11 ticks, major every 5)
                for (i in 0..10) {
                    val angle = startAngle + (i / 10f) * angleRange
                    val angleRad = Math.toRadians(angle.toDouble()).toFloat()
                    val tickLength = if (i % 5 == 0) 10.dp.toPx() else 5.dp.toPx()
                    val outerRadius = radius - strokeWidth / 2f
                    val innerRadius = outerRadius - tickLength

                    drawLine(
                        color = TextSecondary.copy(alpha = 0.3f),
                        start = Offset(
                            center.x + innerRadius * cos(angleRad),
                            center.y + innerRadius * sin(angleRad)
                        ),
                        end = Offset(
                            center.x + outerRadius * cos(angleRad),
                            center.y + outerRadius * sin(angleRad)
                        ),
                        strokeWidth = if (i % 5 == 0) 2.5f else 1.5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw needle
                val needleLength = radius * 0.65f
                val needleEnd = Offset(
                    center.x + needleLength * cos(Math.toRadians(animatedAngle.toDouble())).toFloat(),
                    center.y + needleLength * sin(Math.toRadians(animatedAngle.toDouble())).toFloat()
                )

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(dialColor.copy(alpha = 0.5f), dialColor),
                        start = center,
                        end = needleEnd
                    ),
                    start = center,
                    end = needleEnd,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Draw center hub (outer circle)
                drawCircle(
                    color = dialColor,
                    radius = 14.dp.toPx(),
                    center = center
                )

                // Draw center hub (inner circle)
                drawCircle(
                    color = CardBackground,
                    radius = 8.dp.toPx(),
                    center = center
                )
            }

            // Speed value display in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 8.dp)
            ) {
                Text(
                    text = if (isAutomaticMode) "TARGET RPM" else "DUTY CYCLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (isAutomaticMode) "$currentSpeed" else "$currentSpeed%",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isAutomaticMode) AccentPurple else AccentBlue
                )
            }
        }
    }
}

/**
 * RPM display card showing actual measured motor RPM
 * Also serves as secret trigger (5 taps) to unlock party mode
 */
@Composable
fun RPMDisplayCard(
    currentRPM: Int,
    onSecretUnlock: () -> Unit,
    partyModeUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth RPM value animation
    val animatedRPM by animateIntAsState(
        targetValue = currentRPM,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rpm"
    )

    // Secret unlock detection - 5 taps within 3 seconds
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    Card(
        modifier = modifier.pointerInput(partyModeUnlocked) {
            detectTapGestures {
                if (!partyModeUnlocked) {
                    val currentTime = System.currentTimeMillis()
                    // Reset counter if more than 3 seconds elapsed
                    if (currentTime - lastClickTime > 3000) {
                        clickCount = 0
                    }
                    clickCount++
                    lastClickTime = currentTime

                    // Unlock after 5 taps
                    if (clickCount >= 5) {
                        clickCount = 0
                        onSecretUnlock()
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Speedometer icon
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "ACTUAL",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                // RPM value
                Text(
                    text = "$animatedRPM",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = AccentBlue
                )

                Text(
                    text = "RPM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Connection status card
 * Shows current Bluetooth connection state and provides connect/disconnect button
 */
@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Status color animation (green when connected, red when disconnected)
    val statusColor by animateColorAsState(
        targetValue = if (isConnected) AccentGreen else AccentRed,
        animationSpec = tween(500),
        label = "status_color"
    )

    // Pulsing glow animation for status indicator
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status indicator and label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pulsing status dot
                Box(
                    modifier = Modifier.size(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Outer glow
                        drawCircle(
                            color = statusColor.copy(alpha = pulseAlpha * 0.4f),
                            radius = size.width / 2f * 1.5f
                        )
                        // Inner dot
                        drawCircle(
                            color = statusColor,
                            radius = size.width / 2f
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = statusColor,
                    letterSpacing = 0.8.sp
                )
            }

            // Connect/Disconnect button
            Button(
                onClick = onConnectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColor.copy(alpha = 0.15f),
                    contentColor = statusColor
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "DISCONNECT" else "CONNECT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Direction toggle card
 * Allows user to select forward or reverse motor direction
 */
@Composable
fun DirectionToggleCard(
    isForward: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DIRECTION",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )

            // Forward and reverse buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectionButton(
                    icon = Icons.Default.ArrowBack,
                    isSelected = !isForward,
                    onClick = { if (enabled) onToggle(false) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )

                DirectionButton(
                    icon = Icons.Default.ArrowForward,
                    isSelected = isForward,
                    onClick = { if (enabled) onToggle(true) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual direction button (forward or reverse arrow)
 * Used in direction toggle card
 */
@Composable
fun DirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Scale animation when selected
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> CardBackground.copy(alpha = 0.3f)
            isSelected -> AccentBlue.copy(alpha = 0.2f)
            else -> Color(0xFF1A1F35)
        },
        label = "bg"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(
                2.dp,
                if (isSelected && enabled) AccentBlue else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(enabled) {
                detectTapGestures { if (enabled) onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) (if (isSelected) AccentBlue else TextSecondary) else TextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Start/Stop motor control button
 * Large prominent button for starting and stopping motor
 */
@Composable
fun StartStopControl(
    onSendCommand: (MotorCommand) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Track motor running state
    var isRunning by remember { mutableStateOf(false) }

    // Button color animation (green for start, red for stop)
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) AccentRed else AccentGreen,
        animationSpec = tween(400),
        label = "color"
    )

    // Scale animation when running
    val scale by animateFloatAsState(
        targetValue = if (isRunning) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = modifier
            .height(100.dp)
            .scale(scale)
            .pointerInput(enabled) {
                detectTapGestures {
                    if (enabled) {
                        if (isRunning) {
                            // Stop motor
                            onSendCommand(MotorCommand.Stop)
                            isRunning = false
                        } else {
                            // Start motor
                            onSendCommand(MotorCommand.Start)
                            isRunning = true
                        }
                    }
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) buttonColor.copy(alpha = 0.15f) else CardBackground.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, if (enabled) buttonColor else Color.Transparent, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Play/Stop icon
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (enabled) buttonColor else TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                // Start/Stop label
                Text(
                    text = if (isRunning) "STOP" else "START",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (enabled) buttonColor else TextSecondary.copy(alpha = 0.3f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

