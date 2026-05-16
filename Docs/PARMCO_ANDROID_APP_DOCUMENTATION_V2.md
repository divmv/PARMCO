# PARMCO Android Application
## Bluetooth Motor Controller with Material 3 Design

**Project:** PARMCO (Precision Android-Raspberry Motor Controller)  
**Developer:** Divya Vemuri  
**Hardware Integration:** Group4 (Jonathan) - Raspberry Pi motor controller  
**Language:** Kotlin  
**Framework:** Jetpack Compose  
**Platform:** Android API 21+ (Android 5.0 Lollipop and above)  
**Documentation Version:** 2.0 (November 2025)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Architecture](#architecture)
4. [Libraries and Dependencies](#libraries-and-dependencies)
5. [User Interface](#user-interface)
6. [Bluetooth Communication](#bluetooth-communication)
7. [State Management](#state-management)
8. [Control Modes](#control-modes)
9. [Installation and Setup](#installation-and-setup)
10. [Building the App](#building-the-app)
11. [Usage Guide](#usage-guide)
12. [Troubleshooting](#troubleshooting)
13. [Code Documentation](#code-documentation)

---

## 🎯 Overview

The PARMCO Android app is a sophisticated Bluetooth motor controller featuring:

- **Material 3 Design**: Modern, beautiful UI following Google's latest design guidelines
- **Real-time RPM Monitoring**: Live speed feedback updated every 300ms
- **Dual Control Modes**: Manual PWM control and Automatic RPM setpoint regulation
- **Interactive Dial Control**: Touch-and-drag circular dial for intuitive speed adjustment
- **Spring Physics Animations**: Smooth, natural-feeling UI transitions and feedback
- **Bluetooth RFCOMM**: Reliable wireless communication with Raspberry Pi controller

### What This App Does

1. **Connects** to Raspberry Pi motor controller via Bluetooth
2. **Sends commands** for motor direction (forward/reverse) and speed control
3. **Receives real-time RPM data** from IR sensor measurements
4. **Provides intuitive UI** with visual feedback and animated controls
5. **Supports two control modes**:
   - **Manual**: Direct PWM control (0-100%) with instant response
   - **Automatic**: Set target RPM, let P-controller on Pi regulate speed

---

## ✨ Features

### Core Functionality

✅ **Automatic Bluetooth Discovery**: Finds paired Raspberry Pi device automatically  
✅ **One-tap Connection**: Simple connect/disconnect with visual status  
✅ **Real-time RPM Display**: Live speed monitoring with animated updates  
✅ **Circular Dial Control**: Touch-and-drag interface for speed adjustment  
✅ **Direction Control**: Easy forward/reverse toggle buttons  
✅ **Emergency Stop**: Large, prominent STOP button for safety  
✅ **Dual Control Modes**: Switch between Manual PWM and Auto RPM modes  
✅ **Visual Feedback**: Color-coded status indicators and button states  

### User Experience

✅ **Material 3 Design**: Modern aesthetic with dynamic color theming  
✅ **Spring Physics**: Natural-feeling animations on all interactions  
✅ **Haptic Feedback**: Tactile confirmation of button presses  
✅ **Responsive Layout**: Adapts to different screen sizes  
✅ **Dark Gradient Background**: Sleek purple-to-indigo gradient  
✅ **Clear Typography**: Easy-to-read fonts with proper hierarchy  

### Advanced Features

✅ **Easter Egg Mode**: Secret "Party Mode" activated by tapping RPM 7 times  
✅ **Permission Handling**: Automatic Bluetooth permission requests  
✅ **Error Recovery**: Graceful handling of connection failures  
✅ **Connection State Management**: Reliable reconnection after disconnects  

---

## 🏗️ Architecture

### System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      Android Application                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                 MainActivity.kt                       │   │
│  │  - Bluetooth connection management                   │   │
│  │  - Permission handling                               │   │
│  │  - State management (RPM, mode, connection)         │   │
│  │  - Command sending (direction, speed, mode)         │   │
│  │  - Data receiving (RPM updates from Pi)             │   │
│  └──────────────────────┬───────────────────────────────┘   │
│                         │                                     │
│                         │ Composes UI                        │
│                         ▼                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              UI Components (@Composable)              │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │  • ConnectionStatusCard()                            │   │
│  │  • MotorControlCard()                                │   │
│  │     - CircularSpeedDial()                            │   │
│  │     - DirectionControls()                            │   │
│  │     - StartStopButton()                              │   │
│  │  • RPMDisplayCard()                                  │   │
│  │  • ControlModeSelector()                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            │ Bluetooth RFCOMM
                            │ SPP Profile (Serial Port)
                            │
                    ┌───────▼────────┐
                    │                 │
                    │  Raspberry Pi   │
                    │  Motor Control  │
                    │  Server (C)     │
                    │                 │
                    └─────────────────┘
```

### Component Hierarchy

```
MainActivity (ComponentActivity)
│
├── PARMCOTheme (Material 3 Theme)
│   │
│   └── Column (Main Layout)
│       │
│       ├── ConnectionStatusCard
│       │   ├── Status Indicator (Connected/Disconnected)
│       │   └── Connect/Disconnect Button
│       │
│       ├── MotorControlCard
│       │   ├── CircularSpeedDial (Touch-drag control)
│       │   ├── DirectionControls (Forward/Reverse buttons)
│       │   └── StartStopButton (Play/Stop)
│       │
│       ├── RPMDisplayCard
│       │   ├── Large RPM Value (Animated)
│       │   └── Unit Label ("RPM")
│       │
│       └── ControlModeSelector
│           ├── Manual Mode Button
│           └── Auto Mode Button
```

### State Management

The app uses **Jetpack Compose state management** with `mutableStateOf`:

```kotlin
// Connection state
var isConnected by remember { mutableStateOf(false) }

// Motor control state
var currentRPM by remember { mutableStateOf(0f) }
var currentSpeed by remember { mutableStateOf(0) }
var direction by remember { mutableStateOf("Forward") }

// Control mode state
var controlMode by remember { mutableStateOf(ControlMode.MANUAL) }

// Easter egg state
var tapCount by remember { mutableStateOf(0) }
var isPartyMode by remember { mutableStateOf(false) }
```

---

## 📚 Libraries and Dependencies

### 1. **AndroidX Core KTX**

**Dependency:** `androidx.core:core-ktx:1.12.0`  
**License:** Apache 2.0  
**Purpose:** Kotlin extensions for Android framework APIs

**Key Features Used:**
- `Toast.makeText()`: User notifications
- `ActivityResultContracts`: Permission request handling
- `Context` extensions: Simplified Android API access

**Why AndroidX Core KTX:**
- More concise Kotlin syntax
- Type-safe API wrappers
- Extension functions reduce boilerplate
- Kotlin-idiomatic Android development

---

### 2. **Jetpack Compose**

#### **Compose UI** (`androidx.compose.ui:ui:1.5.4`)

**License:** Apache 2.0  
**Purpose:** Declarative UI framework

**Key Components Used:**
```kotlin
// Layout
Column, Row, Box, Spacer

// Modifiers
.fillMaxSize(), .padding(), .size(), .background()

// Alignment
Alignment.Center, Arrangement.Center

// Drawing
Canvas (for custom graphics like dial arc)

// Gestures
.pointerInput(detectDragGestures, detectTapGestures)
```

**Why Compose:**
- Declarative UI paradigm (easier to understand and maintain)
- Less boilerplate than XML layouts
- Reactive state management built-in
- Powerful animation system
- Modern Android development standard

#### **Compose Material 3** (`androidx.compose.material3:material3:1.1.2`)

**License:** Apache 2.0  
**Purpose:** Material Design 3 components and theming

**Components Used:**
```kotlin
// Structure
Scaffold, Surface, Card

// Buttons
Button, IconButton, Icon

// Typography
Text, MaterialTheme.typography

// Colors
MaterialTheme.colorScheme

// Shapes
RoundedCornerShape, CircleShape
```

**Material 3 Features:**
- Dynamic color system
- Updated component designs
- Improved accessibility
- Consistent visual language
- Adaptive layouts

#### **Compose Animation** (`androidx.compose.animation:animation:1.5.4`)

**License:** Apache 2.0  
**Purpose:** Declarative animations and transitions

**Animations Used:**

1. **Spring Physics** (`spring()`)
```kotlin
// Natural-feeling button presses
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)
```

2. **Value Animations** (`animateFloatAsState`)
```kotlin
// Smooth RPM value transitions
val animatedRPM by animateFloatAsState(
    targetValue = currentRPM,
    animationSpec = tween(durationMillis = 300)
)
```

3. **Color Transitions** (`animateColorAsState`)
```kotlin
// Connection status color change
val statusColor by animateColorAsState(
    targetValue = if (isConnected) Color.Green else Color.Red
)
```

**Why Compose Animation:**
- Declarative animation syntax
- Automatic animation interruption handling
- Spring physics for natural motion
- GPU-accelerated animations
- Composable-first design

---

### 3. **Activity Compose** (`androidx.activity:activity-compose:1.8.1`)

**License:** Apache 2.0  
**Purpose:** Integration between Activity and Compose

**Key Features:**
```kotlin
setContent {
    PARMCOTheme {
        // Compose UI here
    }
}
```

**Why Activity Compose:**
- Bridges traditional Android Activity with Compose
- Enables `setContent` for Compose UI
- Manages composition lifecycle
- Handles configuration changes

---

### 4. **Kotlin Standard Library** (`org.jetbrains.kotlin:kotlin-stdlib:1.9.20`)

**License:** Apache 2.0  
**Purpose:** Core Kotlin language features

**Features Used:**
```kotlin
// Coroutines
launch, delay, isActive

// Collections
mutableListOf, filter, map

// Null safety
?.let, ?:, !!

// Extension functions
Custom extension functions for utilities

// Lambda expressions
{ } for callbacks and composables

// Data classes
data class for structured data
```

---

### 5. **Android Bluetooth APIs** (Built-in)

**Package:** `android.bluetooth`  
**License:** Apache 2.0  
**Purpose:** Bluetooth communication

**Key Classes Used:**

1. **BluetoothManager** - System Bluetooth service access
```kotlin
val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
val bluetoothAdapter = bluetoothManager.adapter
```

2. **BluetoothAdapter** - Bluetooth radio control
```kotlin
bluetoothAdapter.isEnabled  // Check if Bluetooth is on
bluetoothAdapter.bondedDevices  // Get paired devices
```

3. **BluetoothDevice** - Represents remote Bluetooth device
```kotlin
device.name  // Device name (e.g., "raspberrypi")
device.address  // MAC address
device.createRfcommSocketToServiceRecord(uuid)  // Create connection
```

4. **BluetoothSocket** - RFCOMM connection
```kotlin
socket.connect()  // Establish connection
socket.inputStream  // Receive data
socket.outputStream  // Send data
socket.close()  // Disconnect
```

**SPP UUID (Serial Port Profile):**
```kotlin
private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
```

**Why RFCOMM:**
- Standard Bluetooth serial communication
- Reliable stream-based protocol
- Compatible with C server on Raspberry Pi
- Well-documented and widely used

---

### 6. **Lucide Icons** (Used in UI)

**Purpose:** Icon library for UI elements

**Icons Used:**
```kotlin
Icons.Default.PlayArrow  // Start button
Icons.Default.Stop       // Stop button
Icons.Default.ArrowForward  // Forward direction
Icons.Default.ArrowBack     // Reverse direction
Icons.Default.Bluetooth     // Connection status
Icons.Default.Settings      // Mode selection
```

---

## 📦 Complete Dependency List

```gradle
dependencies {
    // AndroidX Core
    implementation 'androidx.core:core-ktx:1.12.0'
    
    // Jetpack Compose
    implementation platform('androidx.compose:compose-bom:2023.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.animation:animation'
    
    // Activity Compose
    implementation 'androidx.activity:activity-compose:1.8.1'
    
    // Lifecycle
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    
    // Kotlin Standard Library
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.20'
    
    // Bluetooth (Built-in Android API - no external dependency needed)
}
```

---

## 🎨 User Interface

### Design System

#### **Color Palette**

```kotlin
// Primary gradient background
Background = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF667eea),  // Blue-purple
        Color(0xFF764ba2)   // Deep purple
    )
)

// Card backgrounds
CardBackground = Color(0xFF1E1E2E).copy(alpha = 0.8f)

// Status colors
Connected = Color(0xFF4CAF50)     // Green
Disconnected = Color(0xFFF44336)  // Red

// Control colors
Forward = Color(0xFF2196F3)       // Blue
Reverse = Color(0xFFFF9800)       // Orange
Stop = Color(0xFFF44336)          // Red

// Text colors
TextPrimary = Color.White
TextSecondary = Color.White.copy(alpha = 0.7f)
```

#### **Typography**

```kotlin
// RPM Display
fontSize = 72.sp
fontWeight = FontWeight.Bold

// Control labels
fontSize = 18.sp
fontWeight = FontWeight.SemiBold

// Status text
fontSize = 14.sp
fontWeight = FontWeight.Normal
```

#### **Spacing**

```kotlin
// Card padding
CardPadding = 24.dp

// Element spacing
SmallSpacing = 8.dp
MediumSpacing = 16.dp
LargeSpacing = 24.dp

// Control sizes
DialSize = 300.dp
ButtonHeight = 70.dp
IconSize = 32.dp
```

### UI Components

#### **1. Connection Status Card**

![Connection Status Screenshot]

**Purpose:** Display Bluetooth connection state and provide connect/disconnect control

**Features:**
- Animated status indicator (pulse effect when connecting)
- Clear connection state text
- Large connect/disconnect button
- Visual feedback on button press

**States:**
- **Disconnected**: Red indicator, "Not Connected", "CONNECT" button
- **Connecting**: Yellow indicator (pulsing), "Connecting..."
- **Connected**: Green indicator, "Connected to Pi", "DISCONNECT" button

```kotlin
@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator (animated circle)
            Canvas(modifier = Modifier.size(16.dp)) {
                drawCircle(
                    color = if (isConnected) Color.Green else Color.Red
                )
            }
            
            // Status text
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                color = Color.White,
                fontSize = 18.sp
            )
            
            // Connect/Disconnect button with spring animation
            Button(
                onClick = { if (isConnected) onDisconnect() else onConnect() },
                modifier = Modifier.scale(buttonScale)
            ) {
                Text(if (isConnected) "DISCONNECT" else "CONNECT")
            }
        }
    }
}
```

#### **2. Circular Speed Dial**

![Speed Dial Screenshot]

**Purpose:** Intuitive touch-and-drag control for motor speed adjustment

**Features:**
- 300dp circular dial with arc progress indicator
- Touch-and-drag interaction (swipe around circle to change speed)
- Real-time value display in center
- Color-coded speed ranges:
  - 0-30%: Green (slow)
  - 31-70%: Blue (medium)
  - 71-100%: Red (fast)
- Smooth animation on value changes

**Interaction:**
1. User touches dial
2. Drag finger around circle
3. Speed adjusts based on angle (0° = 0%, 360° = 100%)
4. Release to set final value
5. Command sent to Raspberry Pi

```kotlin
@Composable
fun CircularSpeedDial(
    value: Int,  // 0-100
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var angle by remember { mutableStateOf(0f) }
    val animatedValue by animateFloatAsState(value.toFloat())
    
    Box(
        modifier = modifier
            .size(300.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val touch = change.position
                    
                    // Calculate angle from center to touch point
                    angle = atan2(
                        touch.y - center.y,
                        touch.x - center.x
                    ).toRadians()
                    
                    // Convert angle to speed (0-100%)
                    val newValue = ((angle / 360f) * 100).toInt()
                        .coerceIn(0, 100)
                    
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw background circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 20.dp.toPx())
            )
        }
        
        // Draw progress arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = when (animatedValue.toInt()) {
                    in 0..30 -> Color.Green
                    in 31..70 -> Color.Blue
                    else -> Color.Red
                },
                startAngle = -90f,
                sweepAngle = (animatedValue / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Center value display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedValue.toInt()}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "SPEED",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
```

#### **3. Direction Controls**

![Direction Controls Screenshot]

**Purpose:** Toggle motor direction between forward and reverse

**Features:**
- Two large arrow buttons (forward/reverse)
- Color-coded:
  - Forward: Blue (#2196F3)
  - Reverse: Orange (#FF9800)
- Active state highlighting
- Spring animation on press
- Clear arrow icons and labels

```kotlin
@Composable
fun DirectionControls(
    currentDirection: String,  // "Forward" or "Reverse"
    onDirectionChange: (String) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Forward button
        DirectionButton(
            label = "FORWARD",
            icon = Icons.Default.ArrowForward,
            color = Color(0xFF2196F3),
            isActive = currentDirection == "Forward",
            enabled = enabled,
            onClick = { onDirectionChange("Forward") }
        )
        
        // Reverse button
        DirectionButton(
            label = "REVERSE",
            icon = Icons.Default.ArrowBack,
            color = Color(0xFFFF9800),
            isActive = currentDirection == "Reverse",
            enabled = enabled,
            onClick = { onDirectionChange("Reverse") }
        )
    }
}

@Composable
fun DirectionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(80.dp)
            .scale(scale)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                color.copy(alpha = 0.3f)
            } else {
                CardBackground.copy(alpha = 0.5f)
            }
        ),
        border = if (isActive) {
            BorderStroke(3.dp, color)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = if (enabled) color else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
```

#### **4. RPM Display Card**

![RPM Display Screenshot]

**Purpose:** Show real-time motor speed with large, easy-to-read display

**Features:**
- Huge RPM value (72sp font)
- Smooth animation on value changes
- "RPM" unit label
- Tap-to-activate easter egg (Party Mode after 7 taps)
- Color changes based on speed:
  - 0-500 RPM: White
  - 501-1000 RPM: Yellow
  - 1000+ RPM: Green

```kotlin
@Composable
fun RPMDisplayCard(
    currentRPM: Float,
    onTap: () -> Unit
) {
    val animatedRPM by animateFloatAsState(
        targetValue = currentRPM,
        animationSpec = tween(durationMillis = 300)
    )
    
    val rpmColor = when {
        animatedRPM < 500 -> Color.White
        animatedRPM < 1000 -> Color.Yellow
        else -> Color.Green
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.1f", animatedRPM),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = rpmColor
            )
            Text(
                text = "RPM",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 4.sp
            )
        }
    }
}
```

#### **5. Control Mode Selector**

![Control Mode Screenshot]

**Purpose:** Switch between Manual PWM and Automatic RPM control modes

**Features:**
- Two-button selector (Manual / Auto)
- Active mode highlighted
- Material 3 segmented button design
- Smooth transition animation
- Disabled when disconnected

```kotlin
@Composable
fun ControlModeSelector(
    currentMode: ControlMode,
    onModeChange: (ControlMode) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Manual mode button
        ModeButton(
            label = "MANUAL",
            isActive = currentMode == ControlMode.MANUAL,
            enabled = enabled,
            onClick = { onModeChange(ControlMode.MANUAL) }
        )
        
        // Auto mode button
        ModeButton(
            label = "AUTO",
            isActive = currentMode == ControlMode.AUTO,
            enabled = enabled,
            onClick = { onModeChange(ControlMode.AUTO) }
        )
    }
}

@Composable
fun ModeButton(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(160.dp)
            .height(50.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                CardBackground
            }
        )
    ) {
        Text(
            text = label,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
```

#### **6. Start/Stop Button**

![Start/Stop Button Screenshot]

**Purpose:** Toggle motor on/off state

**Features:**
- Large, prominent button (100dp height)
- Play/Stop icon (changes based on state)
- Spring animation on press
- Disabled when not connected
- Safety-critical control (motor doesn't move without explicit start)

```kotlin
@Composable
fun StartStopButton(
    isRunning: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isRunning) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    val buttonColor = if (isRunning) Color.Red else Color.Green
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(100.dp)
            .scale(scale)
            .clickable(enabled = enabled) { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                buttonColor.copy(alpha = 0.2f)
            } else {
                CardBackground.copy(alpha = 0.3f)
            }
        ),
        border = if (enabled) {
            BorderStroke(3.dp, buttonColor)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isRunning) 8.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isRunning) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (enabled) buttonColor else Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (isRunning) "STOP" else "START",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (enabled) buttonColor else Color.Gray
                )
            }
        }
    }
}
```

---

## 📡 Bluetooth Communication

### Connection Flow

```
[Android App]                          [Raspberry Pi]
      │                                       │
      │ 1. Check Bluetooth enabled           │
      ├──────────────────────────────────────┤
      │                                       │
      │ 2. Request permissions (if needed)   │
      ├──────────────────────────────────────┤
      │                                       │
      │ 3. Get paired devices                │
      ├──────────────────────────────────────┤
      │                                       │
      │ 4. Find "raspberrypi" device         │
      ├──────────────────────────────────────┤
      │                                       │
      │ 5. Create RFCOMM socket              │
      │    (UUID: 00001101-...)              │
      ├──────────────────────────────────────┤
      │                                       │
      │ 6. Connect to device                 │
      ├─────────────CONNECT────────────────>│
      │                                       │
      │<────────CONNECTION ESTABLISHED───────┤
      │                                       │
      │ 7. Start listening for RPM data      │
      │                                       │
      │<────────────RPM:0.0─────────────────┤
      │                                       │
      │ 8. Send commands                     │
      ├─────────────F (Forward)─────────────>│
      ├─────────────M:75 (Manual 75%)───────>│
      ├─────────────A:1000 (Auto 1000RPM)───>│
      │                                       │
      │ 9. Receive RPM updates (every 0.3s)  │
      │<────────────RPM:234.5───────────────┤
      │<────────────RPM:567.8───────────────┤
      │<────────────RPM:892.1───────────────┤
      │                                       │
```

### Command Protocol

#### **Commands Sent (App → Pi)**

| Command | Format | Description | Example |
|---------|--------|-------------|---------|
| Manual Mode | `M:<PWM>\n` | Set manual PWM duty cycle (0-100%) | `M:75\n` |
| Auto Mode | `A:<RPM>\n` | Set target RPM for automatic control | `A:1200\n` |
| Forward | `F\n` | Set motor direction to forward | `F\n` |
| Reverse | `R\n` | Set motor direction to reverse | `R\n` |
| Stop | `S\n` | Emergency stop motor | `S\n` |

#### **Responses Received (Pi → App)**

| Response | Format | Description | Example |
|----------|--------|-------------|---------|
| RPM Update | `RPM:<value>\n` | Current motor speed measurement | `RPM:1234.5\n` |
| Mode Status | `MODE:<type>\n` | Current control mode | `MODE:AUTO\n` |
| Error | `ERR:<message>\n` | Error occurred | `ERR:Sensor failure\n` |

### Bluetooth Implementation

```kotlin
// Bluetooth SPP UUID (Serial Port Profile)
private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// Bluetooth manager and adapter
private lateinit var bluetoothManager: BluetoothManager
private lateinit var bluetoothAdapter: BluetoothAdapter

// Connection objects
private var bluetoothSocket: BluetoothSocket? = null
private var inputStream: InputStream? = null
private var outputStream: OutputStream? = null

/**
 * Connect to Raspberry Pi via Bluetooth
 * Finds device by name "raspberrypi" and establishes RFCOMM connection
 */
private fun connectBluetooth() {
    // Get Bluetooth adapter
    bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothAdapter = bluetoothManager.adapter
    
    // Check if Bluetooth is enabled
    if (!bluetoothAdapter.isEnabled) {
        Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
        return
    }
    
    // Find paired Raspberry Pi device
    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
    val device = pairedDevices.find { it.name == "raspberrypi" }
    
    if (device == null) {
        Toast.makeText(this, "Raspberry Pi not found. Please pair device.", Toast.LENGTH_LONG).show()
        return
    }
    
    // Connect in background thread (Bluetooth I/O blocks)
    thread {
        try {
            // Create RFCOMM socket
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            
            // Establish connection
            bluetoothSocket?.connect()
            
            // Get I/O streams
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            
            // Update UI on main thread
            runOnUiThread {
                isConnected = true
                Toast.makeText(this@MainActivity, "Connected to Raspberry Pi", Toast.LENGTH_SHORT).show()
            }
            
            // Start listening for RPM data
            startListening()
            
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Listen for incoming RPM data from Raspberry Pi
 * Runs continuously in background thread
 */
private fun startListening() {
    thread {
        val buffer = ByteArray(1024)
        
        while (isConnected) {
            try {
                // Read bytes from input stream
                val bytes = inputStream?.read(buffer)
                
                if (bytes != null && bytes > 0) {
                    // Convert bytes to string
                    val received = String(buffer, 0, bytes).trim()
                    
                    // Parse RPM value
                    // Expected format: "RPM:1234.5"
                    if (received.startsWith("RPM:")) {
                        val rpmValue = received.substring(4).toFloatOrNull()
                        
                        if (rpmValue != null) {
                            // Update UI on main thread
                            runOnUiThread {
                                currentRPM = rpmValue
                            }
                        }
                    }
                }
                
            } catch (e: IOException) {
                // Connection lost
                runOnUiThread {
                    isConnected = false
                    Toast.makeText(this@MainActivity, "Connection lost", Toast.LENGTH_SHORT).show()
                }
                break
            }
        }
    }
}

/**
 * Send command to Raspberry Pi
 * 
 * @param command: Command string (e.g., "M:75", "F", "A:1200")
 */
private fun sendCommand(command: String) {
    if (!isConnected) {
        Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
        return
    }
    
    thread {
        try {
            // Add newline terminator and send
            val message = "$command\n"
            outputStream?.write(message.toByteArray())
            outputStream?.flush()
            
            println("Sent command: $command")
            
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Disconnect from Raspberry Pi
 * Closes all streams and socket
 */
private fun disconnectBluetooth() {
    try {
        isConnected = false
        
        inputStream?.close()
        outputStream?.close()
        bluetoothSocket?.close()
        
        inputStream = null
        outputStream = null
        bluetoothSocket = null
        
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        
    } catch (e: IOException) {
        Toast.makeText(this, "Disconnect error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 🎮 Control Modes

### Manual Mode

**Description:** Direct PWM control - user sets duty cycle percentage

**UI Elements:**
- Circular dial for 0-100% PWM adjustment
- Real-time RPM feedback
- No automatic speed regulation

**Command Format:** `M:<PWM>\n` (e.g., `M:75\n` for 75% duty cycle)

**Use Case:**
- Fine-grained control of motor power
- Testing and calibration
- Open-loop operation

**Behavior:**
1. User drags dial to desired speed
2. App sends `M:<value>` command
3. Pi sets PWM output to exact value
4. Motor speed varies with load (no feedback control)

---

### Automatic Mode

**Description:** Target RPM control - P-controller on Pi regulates speed

**UI Elements:**
- RPM input field for target setpoint
- Real-time RPM feedback
- Visual indication of control mode

**Command Format:** `A:<RPM>\n` (e.g., `A:1200\n` for 1200 RPM target)

**Use Case:**
- Maintain constant speed under varying loads
- Precise speed requirements
- Closed-loop operation

**Behavior:**
1. User enters target RPM
2. App sends `A:<RPM>` command
3. Pi's P-controller continuously adjusts PWM
4. Motor maintains target speed (±5% tolerance)
5. Real-time error correction

---

## 📥 Installation and Setup

### Prerequisites

1. **Android Studio Hedgehog (2023.1.1) or later**
2. **Android SDK 34** (API level 34)
3. **Kotlin 1.9.20 or later**
4. **Physical Android device** with Bluetooth (Emulator won't work for BT)

### Project Setup

1. **Clone repository**
```bash
git clone https://github.com/YourUsername/PARMCO-Android.git
cd PARMCO-Android
```

2. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

3. **Configure build.gradle.kts**

**`build.gradle.kts` (Project level):**
```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
```

**`build.gradle.kts` (Module level):**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.parmco"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.parmco"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Debugging tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

4. **Add Bluetooth permissions to AndroidManifest.xml**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.parmco">

    <!-- Bluetooth permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    
    <!-- Location required for Bluetooth scanning on Android 6+ -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    
    <!-- Bluetooth hardware feature -->
    <uses-feature android:name="android.hardware.bluetooth" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.PARMCO">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PARMCO">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

5. **Sync Gradle**
   - Tools → Sync Project with Gradle Files
   - Wait for dependencies to download

---

## 🔨 Building the App

### Debug Build (Development)

```bash
# Command line build
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Or in Android Studio:**
- Build → Build Bundle(s) / APK(s) → Build APK(s)
- Find APK in: `app/build/outputs/apk/debug/`

### Release Build (Production)

```bash
# Generate release APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

**Signing the APK:**

1. Generate keystore:
```bash
keytool -genkey -v -keystore parmco-release.jks \
        -alias parmco -keyalg RSA -keysize 2048 -validity 10000
```

2. Add to `build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("parmco-release.jks")
            storePassword = "your-password"
            keyAlias = "parmco"
            keyPassword = "your-password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... other config
        }
    }
}
```

3. Build signed APK:
```bash
./gradlew assembleRelease
```

### Installation on Device

**Via USB (ADB):**
```bash
# Enable USB debugging on Android device
# Connect device via USB

# Install debug build
adb install app/build/outputs/apk/debug/app-debug.apk

# Or release build
adb install app/build/outputs/apk/release/app-release.apk
```

**Via File Transfer:**
1. Copy APK to device (email, cloud, USB transfer)
2. On device: Enable "Install unknown apps" for file manager
3. Tap APK file to install

---

## 📖 Usage Guide

### First-Time Setup

1. **Pair Raspberry Pi with Android device**
   - On Android: Settings → Bluetooth → Scan
   - Find "raspberrypi" and pair (PIN: 0000 or 1234)

2. **Launch PARMCO app**
   - Tap app icon on home screen
   - Grant Bluetooth permissions when prompted

3. **Connect to Raspberry Pi**
   - Tap "CONNECT" button on Connection Status card
   - Wait for "Connected to Raspberry Pi" message
   - Status indicator turns green

### Basic Operation

#### **Manual Mode (Default)**

1. Select "MANUAL" control mode
2. Set direction (FORWARD or REVERSE)
3. Drag circular dial to adjust speed (0-100%)
4. Press "START" to begin motor operation
5. Monitor real-time RPM in display card
6. Press "STOP" to halt motor

#### **Automatic Mode**

1. Select "AUTO" control mode
2. Set direction (FORWARD or REVERSE)
3. Enter target RPM (e.g., 1200)
4. Press "START" to begin automatic control
5. Watch RPM converge to target (P-controller active)
6. Adjust target RPM as needed
7. Press "STOP" to halt motor

### Safety Features

- **Emergency STOP button**: Large red button always accessible
- **Connection required**: Motor won't operate without active Bluetooth connection
- **Explicit START**: Motor doesn't move until user presses START
- **Visual feedback**: Clear indicators for all control states
- **Disconnection handling**: Motor stops automatically if connection lost

### Easter Egg: Party Mode 🎉

**Activation:**
1. Tap RPM display card 7 times rapidly
2. Enjoy the celebratory animation!
3. (No functional changes - just for fun)

---

## 🔧 Troubleshooting

### App Won't Install

**Symptom:** "App not installed" error

**Solutions:**
1. **Enable unknown sources**
   - Settings → Security → Unknown sources → Enable
   - Or: Settings → Apps → Special access → Install unknown apps → [Your file manager] → Allow

2. **Uninstall old version**
   - If updating, uninstall previous version first
   - Or use `adb install -r` to replace

3. **Check Android version**
   - Requires Android 5.0 (API 21) or higher
   - Check: Settings → About phone → Android version

### Bluetooth Connection Fails

**Symptom:** "Connection failed" message

**Solutions:**

1. **Devices not paired**
   ```
   - Android Settings → Bluetooth → Scan
   - Find "raspberrypi" and tap to pair
   - Enter PIN if prompted (usually 0000 or 1234)
   ```

2. **Bluetooth not enabled**
   ```
   - Enable Bluetooth on Android device
   - Check Raspberry Pi Bluetooth is active:
     systemctl status bluetooth
   ```

3. **Raspberry Pi server not running**
   ```
   - Check if motor_control is running:
     ps aux | grep motor_control
   
   - Start manually if needed:
     sudo ./motor_control
   
   - Or restart service:
     sudo systemctl restart parmco
   ```

4. **Wrong device name**
   ```
   - Check Raspberry Pi Bluetooth name:
     hciconfig
   
   - If different from "raspberrypi", update in app code:
     val device = pairedDevices.find { it.name == "your-device-name" }
   ```

5. **Permission denied**
   ```
   - Grant Bluetooth permissions in Android settings
   - Settings → Apps → PARMCO → Permissions → Enable all
   ```

### App Crashes on Startup

**Symptom:** App closes immediately after opening

**Solutions:**

1. **Check logcat for errors**
   ```bash
   adb logcat | grep PARMCO
   ```

2. **Missing permissions**
   - Reinstall app to trigger permission requests
   - Or manually grant in Settings → Apps → PARMCO → Permissions

3. **Bluetooth not available**
   - Ensure device has Bluetooth hardware
   - Check Bluetooth is enabled before launching app

4. **Compose incompatibility**
   - Update Android System WebView if needed
   - Settings → Apps → Android System WebView → Update

### RPM Not Updating

**Symptom:** RPM display shows 0.0 even when motor running

**Solutions:**

1. **Connection issue**
   - Check Bluetooth connection is active (green indicator)
   - Reconnect if disconnected

2. **Raspberry Pi not sending data**
   - Check Raspberry Pi logs:
     ```bash
     sudo journalctl -u parmco -f
     ```
   - Should see: "Sending RPM: 1234.5"

3. **IR sensor not working**
   - See Raspberry Pi motor driver troubleshooting guide
   - Check sensor power and alignment

4. **Parsing error**
   - Check received data format in logcat
   - Should be: "RPM:1234.5\n"

### Dial Control Not Responding

**Symptom:** Can't drag circular dial to change speed

**Solutions:**

1. **Not connected**
   - Dial disabled when not connected to Pi
   - Connect first, then try dial

2. **Wrong control mode**
   - Dial only works in Manual mode
   - Switch from Auto to Manual mode

3. **Touch not registered**
   - Try tapping center first, then drag
   - Ensure finger stays within dial area during drag

### Motor Doesn't Move

**Symptom:** Commands sent but motor doesn't respond

**Solutions:**

1. **Motor not started**
   - Press START button first
   - Motor requires explicit start command

2. **Speed set to zero**
   - Check dial is not at 0%
   - Increase speed to at least 20%

3. **Raspberry Pi hardware issue**
   - See motor driver documentation troubleshooting
   - Check power supply, connections, L293D, MOSFET

4. **Direction not set**
   - Ensure Forward or Reverse is selected
   - Try changing direction and back

### High Battery Drain

**Symptom:** App uses excessive battery

**Solutions:**

1. **Disconnect when not in use**
   - Always disconnect Bluetooth when done
   - Tap DISCONNECT button

2. **Close app completely**
   - Don't just minimize - fully close app
   - Swipe away from recent apps

3. **Bluetooth scan disabled**
   - App doesn't actively scan (only on connect)
   - Check no other apps are scanning

### Build Errors in Android Studio

**Common errors and fixes:**

#### **"SDK location not found"**
```bash
# Create local.properties file with SDK path
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

#### **"Gradle sync failed"**
```bash
# Clear Gradle cache
./gradlew clean
./gradlew --stop

# Invalidate caches in Android Studio
File → Invalidate Caches / Restart
```

#### **"Duplicate class found"**
```kotlin
// Add to build.gradle.kts:
configurations.all {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
}
```

#### **"Compose compiler version mismatch"**
```kotlin
// Update compose compiler extension version:
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.4"  // Match Kotlin version
}
```

---

## 📝 Code Documentation

### Fully Commented MainActivity.kt

The complete MainActivity.kt file with comprehensive inline documentation is available in the project repository. Key sections include:

1. **Package and Imports**: All required libraries
2. **Constants**: Bluetooth UUID and configuration
3. **MainActivity Class**: Main activity with Compose UI
4. **State Variables**: All mutable state for UI reactivity
5. **Bluetooth Functions**: Connection, sending, receiving
6. **UI Composables**: All @Composable functions
7. **Helper Functions**: Utilities and extensions

**Highlights:**

- Every function has a doc comment explaining purpose, parameters, and return values
- Complex algorithms (angle calculation, P-controller) have step-by-step comments
- All state changes are documented with reasoning
- Bluetooth protocol is fully explained
- Animation specs are commented with visual descriptions

**Example:**
```kotlin
/**
 * Circular speed dial control
 * 
 * Interactive touch-and-drag control for setting motor speed.
 * User can drag finger around circle to adjust value from 0-100%.
 * 
 * Features:
 * - Arc progress indicator showing current value
 * - Color-coded speed ranges (Green/Blue/Red)
 * - Smooth animation on value changes
 * - Centered value display with percentage
 * 
 * @param value Current speed value (0-100)
 * @param onValueChange Callback when user changes value
 * @param modifier Compose modifier for customization
 */
@Composable
fun CircularSpeedDial(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Implementation with detailed inline comments...
}
```

---

## 🤝 Integration with Motor Driver

### Communication Protocol

The Android app communicates with the Raspberry Pi motor driver using a simple text-based protocol over Bluetooth RFCOMM.

**Data Flow:**
```
Android App                 Raspberry Pi Motor Driver
    |                               |
    |---- M:75\n -----------------→ |  (Manual mode, 75% PWM)
    |← RPM:1234.5\n ---------------- |  (Current RPM measurement)
    |                               |
    |---- A:1200\n ---------------→ |  (Auto mode, target 1200 RPM)
    |← RPM:1234.5\n ---------------- |  (Updated every 0.3s)
    |                               |
    |---- F\n --------------------→ |  (Forward direction)
    |                               |
    |---- R\n --------------------→ |  (Reverse direction)
    |                               |
    |---- S\n --------------------→ |  (Emergency stop)
    |                               |
```

### Command Reference

| Command | Description | Response |
|---------|-------------|----------|
| `M:75` | Set PWM to 75% (Manual mode) | None (but RPM updates continue) |
| `A:1200` | Set target to 1200 RPM (Auto mode) | `MODE:AUTO,TARGET:1200` |
| `F` | Set direction forward | `DIR:FWD` |
| `R` | Set direction reverse | `DIR:REV` |
| `S` | Emergency stop | `MOTOR:STOPPED` |

### Synchronization

- **RPM updates**: Sent from Pi to App every 0.3 seconds (300ms)
- **Command latency**: Typically <50ms over Bluetooth
- **Control loop**: P-controller runs at 20Hz on Pi (independent of Bluetooth)

**Why this matters:**
- App doesn't need to worry about control algorithms
- Pi handles real-time speed regulation locally
- Bluetooth latency doesn't affect motor performance
- App just displays status and sends high-level commands

---

## 👥 Credits

**Android Development:**
- **Divya Vemuri**: Lead developer, UI/UX design, Bluetooth implementation

**Hardware & Embedded Systems:**
- **Jonathan (Group4)**: Raspberry Pi controller, circuit design, integration

**Project:** PARMCO (Precision Android-Raspberry Motor Controller)  
**Course:** Embedded Systems / Microcontroller Applications  
**Institution:** (Your university/institution)  
**Semester:** Fall 2025

---

## 📄 License

This project is developed for educational purposes as part of an academic course.

**Code License:** MIT License (for student use)  
**Documentation:** Creative Commons BY-SA 4.0  

**Third-party Libraries:**
- AndroidX: Apache 2.0 License
- Jetpack Compose: Apache 2.0 License
- Kotlin Standard Library: Apache 2.0 License
- Material 3 Design: Apache 2.0 License

---

## 🔄 Changelog

### Version 2.0 (November 2025)
- Added Material 3 design system
- Implemented circular speed dial with touch-drag control
- Added spring physics animations for all interactions
- Enhanced connection management with visual feedback
- Improved RPM display with smooth animations
- Added dual control mode support (Manual/Auto)
- Implemented easter egg Party Mode
- Comprehensive code documentation with inline comments
- Updated build configuration for Android 14 (API 34)

### Version 1.0 (October 2025)
- Initial release
- Basic Bluetooth RFCOMM communication
- Manual PWM control
- Real-time RPM display
- Direction control (Forward/Reverse)
- Simple UI with basic styling

---

**END OF DOCUMENTATION**

For questions or issues, contact:
- Divya Vemuri: [Contact info]
- Jonathan (Group4): [Contact info]

Project repository: [GitHub link if applicable]

**Related Documentation:**
- [Motor Driver Documentation](PARMCO_MOTOR_DRIVER_DOCUMENTATION_V2.md)
- [Circuit Schematic](circuit_diagram.png)
- [Project Website](PARMCO-Project-Website.html)