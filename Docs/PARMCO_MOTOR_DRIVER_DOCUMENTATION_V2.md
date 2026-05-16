# PARMCO Motor Driver Control Software
## Raspberry Pi Motor Controller with P-Controller and RPM Measurement

**Project:** PARMCO (Precision Android-Raspberry Motor Controller)  
**Authors:** Group4 (Jonathan) & Divya Vemuri  
**Hardware:** Raspberry Pi 4, L293D H-Bridge, IRFZ44N MOSFET, IR Sensor  
**Language:** C  
**Platform:** Raspberry Pi OS (Linux)  
**Documentation Version:** 2.0 (November 2025)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Hardware Design](#hardware-design)
4. [Software Architecture](#software-architecture)
5. [Libraries and Dependencies](#libraries-and-dependencies)
6. [Key Functions](#key-functions)
7. [Control Algorithms](#control-algorithms)
8. [Bluetooth Communication](#bluetooth-communication)
9. [Installation and Deployment](#installation-and-deployment)
10. [Usage](#usage)
11. [Troubleshooting](#troubleshooting)
12. [Project Files](#project-files)

---

## 🎯 Overview

The PARMCO motor driver software is a sophisticated real-time control system that runs on a Raspberry Pi 4. It provides wireless Bluetooth control of a DC motor with real-time speed feedback and automatic speed regulation.

### Key Features

✅ **Real-time performance** - P-controller runs locally on Pi at 20Hz  
✅ **Zero-latency control** - No Bluetooth delays in control loop  
✅ **Bidirectional motor control** - Forward and reverse operation  
✅ **PWM speed control** - 0-100% speed range via MOSFET switching  
✅ **RPM feedback** - Real-time speed measurement from IR sensor (3 pulses per revolution)  
✅ **Wireless interface** - Android app sends setpoints via Bluetooth RFCOMM  
✅ **Thread-safe** - Multi-threaded architecture with pthread and mutex protection  
✅ **Systemd integration** - Automatic startup on boot with service management  
✅ **IEEE-standard documentation** - Professional-quality schematics and diagrams  

### What This System Does

1. **Receives commands** from an Android app via Bluetooth (RPM setpoints, direction, manual PWM)
2. **Controls motor** through L293D H-bridge with MOSFET-based PWM speed control
3. **Measures speed** using IR sensor detecting reflective markers on motor shaft
4. **Regulates speed** automatically using a P-controller when in automatic mode
5. **Reports status** back to Android app including current RPM, connection state, and control mode

---

## 🏗️ System Architecture

### Architectural Evolution

The system architecture evolved through multiple iterations to achieve optimal performance:

#### **Version 1: App-based Control (Initial Design)**
- Android app calculated P-controller output
- App sent PWM commands via Bluetooth
- Control latency: 100-200ms due to wireless communication
- Performance: Poor due to Bluetooth delays in feedback loop
- **Result:** Rejected due to latency issues

#### **Version 2: Pi-based Control (Current Implementation)**
- Raspberry Pi runs P-controller locally at 20Hz
- Android app only sends RPM setpoints
- Control latency: Near-zero (local computation)
- Performance: Excellent real-time response
- **Result:** Adopted as final architecture ✅

### System Components

```
┌─────────────────────┐         Bluetooth          ┌──────────────────────┐
│                     │◄──────────RFCOMM───────────►│                      │
│   Android App       │                             │   Raspberry Pi 4     │
│   (Jetpack Compose) │   Sends: RPM setpoints     │   (C Control Server) │
│                     │   Receives: Current RPM     │                      │
└─────────────────────┘                             └──────────┬───────────┘
                                                               │
                                                               │ GPIO Control
                                                               │
                         ┌─────────────────────────────────────┴─────────────┐
                         │                                                   │
                    ┌────▼────┐      ┌──────────┐      ┌────────┐      ┌───▼────┐
                    │ GPIO17  │      │ GPIO18   │      │ GPIO27 │      │ GPIO22 │
                    │ (IN1)   │      │ (PWM)    │      │ (IN2)  │      │ (RPM)  │
                    └────┬────┘      └────┬─────┘      └────┬───┘      └───┬────┘
                         │                │                  │               │
                         │           ┌────▼─────┐            │               │
                         │           │ IRFZ44N  │            │               │
                         │           │ MOSFET   │            │               │
                         │           │ (Enable) │            │               │
                         │           └────┬─────┘            │               │
                         │                │                  │               │
                    ┌────▼────────────────▼──────────────────▼───┐           │
                    │                                            │           │
                    │           L293D H-Bridge Driver            │           │
                    │           (Dual H-bridge IC)               │           │
                    │                                            │           │
                    └────┬──────────────────────┬────────────────┘           │
                         │                      │                            │
                    ┌────▼────┐            ┌────▼────┐              ┌────────▼────────┐
                    │  OUT1   │            │  OUT2   │              │   IR Sensor     │
                    └────┬────┘            └────┬────┘              │   (3 pulses/    │
                         │                      │                   │   revolution)   │
                         └──────────┬───────────┘                   └─────────────────┘
                                    │
                              ┌─────▼──────┐
                              │            │
                              │  12V DC    │
                              │  Motor     │
                              │            │
                              └────────────┘
```

### Multi-threaded Architecture

The C control server uses a multi-threaded design for concurrent operations:

1. **Main Thread**: Bluetooth client handling and command processing
2. **RPM Measurement Thread**: Pthread-based continuous RPM calculation (runs every 0.3 seconds)
3. **P-Controller Thread**: 20Hz control loop for automatic speed regulation
4. **Interrupt Handler**: GPIO interrupt for IR sensor pulse detection (wiringPiISR)

---

## 🔌 Hardware Design

### Circuit Schematic

The hardware design uses IEEE-standard components and professional routing:

![Motor Driver Schematic](circuit_diagram.png)

### Circuit Components

| Component | Part Number | Quantity | Purpose |
|-----------|------------|----------|---------|
| Microcontroller | Raspberry Pi 4 | 1 | Main control unit |
| Motor Driver IC | L293D | 1 | Dual H-bridge, 600mA per channel |
| N-Channel MOSFET | IRFZ44N | 1 | Enable pin PWM control |
| Gate Resistor | 1kΩ (R1) | 1 | MOSFET gate protection |
| Pull-up Resistor | 10kΩ (R2) | 1 | Enable pin pull-up to 3.3V |
| DC Motor | 12V Motor | 1 | Load device |
| IR Sensor | IR Module (3-pin) | 1 | RPM measurement |
| Power Supply | 12V 2A | 1 | Motor power supply |
| Breadboard | 830 point | 1 | Prototyping platform |
| Jumper Wires | Assorted | 1 set | Connections |

### Pin Connections

#### Raspberry Pi GPIO to L293D
```
GPIO17 (Pin 11)  →  L293D IN1 (Pin 2)   : Direction control (forward)
GPIO27 (Pin 13)  →  L293D IN2 (Pin 7)   : Direction control (reverse)
GPIO18 (Pin 12)  →  MOSFET Gate         : PWM speed control
GPIO22 (Pin 15)  →  IR Sensor OUT       : RPM measurement
3.3V (Pin 1)     →  L293D Vcc1 (Pin 16) : Logic power
GND (Pin 6)      →  Common Ground        : Ground reference
```

#### MOSFET Connections
```
Gate   →  GPIO18 (through 1kΩ resistor R1)
Drain  →  L293D EN1 (Pin 1)
Source →  Ground
```

#### Pull-up Resistor R2 (10kΩ)
```
One end → L293D EN1 (Pin 1)
Other end → Raspberry Pi 3.3V
```

#### L293D to Motor
```
L293D OUT1 (Pin 3)  →  Motor Terminal 1
L293D OUT2 (Pin 6)  →  Motor Terminal 2
L293D Vcc2 (Pin 8)  →  12V External Supply
L293D GND (Pins 4,5,12,13) → Ground
```

#### IR Sensor
```
VCC → 3.3V (separate connection from L293D logic power)
OUT → GPIO22
GND → Common Ground
```

### Critical Power Design Notes

⚠️ **IMPORTANT POWER RAIL SEPARATION:**

1. **3.3V Logic Rail**: Powers L293D logic circuits (Vcc1) and pull-up resistor R2
   - This ensures all control signals remain within safe voltage levels
   - Pull-up resistor MUST connect to 3.3V, NOT 12V (would damage L293D enable pin)

2. **12V Motor Rail**: Powers L293D motor outputs (Vcc2)
   - Provides sufficient power for DC motor operation
   - Isolated from logic circuits to prevent interference

3. **Common Ground**: Shared ground reference between Pi and motor circuit
   - Essential for proper signal communication
   - Prevents ground loops and voltage reference issues

### Motor Control Logic

The circuit uses active-low enable logic with inverted PWM:

#### **Forward Rotation**
```
GPIO17 = HIGH
GPIO27 = LOW  
GPIO18 = LOW (MOSFET off, EN1 pulled HIGH by R2)
Result: Motor runs forward at full speed
```

#### **Reverse Rotation**
```
GPIO17 = LOW
GPIO27 = HIGH
GPIO18 = LOW (MOSFET off, EN1 pulled HIGH by R2)
Result: Motor runs reverse at full speed
```

#### **Speed Control (PWM)**
```
GPIO18 = PWM signal (0-100% duty cycle)
Lower duty cycle = Motor enabled more = Higher speed
Higher duty cycle = Motor enabled less = Lower speed
```

**Note:** The inverted PWM logic means:
- 0% duty cycle (always LOW) = Full speed (EN1 always HIGH)
- 100% duty cycle (always HIGH) = Stopped (EN1 always LOW)

#### **Complete Stop**
```
GPIO18 = HIGH (MOSFET on, pulls EN1 to ground)
Result: Motor driver disabled, motor stops
```

#### **RPM Measurement**
```
IR sensor detects 3 reflective markers per motor shaft revolution
GPIO22 configured with interrupt to count falling edges
RPM = (pulse_count / 3) × (60 / time_elapsed)
```

---

## 💻 Software Architecture

### File Structure

```
/home/group4/Group-4/FinalProject/Checkpoint_3_Code/Control/
├── Control.c              # Main C control server (compiled to ./motor_control)
├── parmco.service         # Systemd service file
├── install_service.sh     # Service installation script
└── README.md             # Setup instructions
```

### Program Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Program Startup                           │
│  1. Initialize GPIO pins (wiringPi)                         │
│  2. Set up interrupt on GPIO22 for IR sensor               │
│  3. Initialize Bluetooth RFCOMM server socket               │
│  4. Start RPM measurement thread (pthread)                  │
│  5. Listen for Android app connections                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Client Connection Established                   │
│  1. Accept incoming Bluetooth connection                    │
│  2. Send initial status packet to app                       │
│  3. Start P-controller thread (if auto mode)               │
│  4. Enter command processing loop                           │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 Command Processing Loop                      │
│  ┌─────────────────────────────────────────────────┐       │
│  │  Receive command from Android app                │       │
│  └─────────────────┬───────────────────────────────┘       │
│                    │                                         │
│         ┌──────────┴──────────┐                            │
│         │ Parse command type   │                            │
│         └──────────┬───────────┘                            │
│                    │                                         │
│    ┌───────────────┼───────────────┬──────────────────┐   │
│    │               │               │                  │   │
│    ▼               ▼               ▼                  ▼   │
│  MANUAL          AUTO          DIRECTION         EMERGENCY│
│  MODE            MODE            CHANGE            STOP   │
│    │               │               │                  │   │
│    │               │               │                  │   │
│    ▼               ▼               ▼                  ▼   │
│ Set PWM       Start P-ctrl     Set GPIO17/27     Stop all │
│ directly      thread with      for fwd/rev       motors   │
│               setpoint                                     │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Concurrent Background Threads                   │
│                                                              │
│  ┌─────────────────────┐    ┌──────────────────────┐       │
│  │  RPM Measurement    │    │   P-Controller       │       │
│  │  Thread             │    │   Thread (Auto Mode) │       │
│  │                     │    │                      │       │
│  │  Every 0.3 seconds: │    │  Every 50ms (20Hz):  │       │
│  │  1. Read pulse      │    │  1. Read current RPM │       │
│  │     count           │    │  2. Calculate error  │       │
│  │  2. Calculate RPM   │    │  3. Compute PWM      │       │
│  │  3. Send to app     │    │  4. Apply to motor   │       │
│  └─────────────────────┘    └──────────────────────┘       │
│                                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │  IR Sensor Interrupt Handler                      │       │
│  │  (wiringPiISR on GPIO22)                         │       │
│  │                                                   │       │
│  │  On falling edge:                                │       │
│  │  1. Increment pulse counter (atomic operation)  │       │
│  │  2. Return immediately                           │       │
│  └──────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📚 Libraries and Dependencies

### 1. **wiringPi** (GPIO Control)

**License:** LGPLv3  
**Purpose:** Hardware interface library for GPIO control

**Key Functions Used:**
```c
wiringPiSetupGpio()           // Initialize GPIO using BCM numbering
pinMode(pin, mode)             // Configure pin as input or output
digitalWrite(pin, value)       // Set output pin HIGH or LOW
pwmWrite(pin, value)           // Set PWM duty cycle (0-1024)
pwmSetMode(PWM_MODE_MS)        // Set PWM mode to mark-space
pwmSetClock(divisor)           // Set PWM clock divider
pwmSetRange(range)             // Set PWM range
wiringPiISR(pin, mode, func)   // Attach interrupt handler to pin
```

**Why wiringPi:**
- Provides simple, intuitive API for GPIO control
- Hardware PWM support for smooth motor speed control
- Interrupt handling for time-critical IR sensor readings
- Well-documented and widely used in Raspberry Pi projects

**Installation:**
```bash
sudo apt-get update
sudo apt-get install wiringpi
```

### 2. **pthread** (POSIX Threads)

**License:** Part of glibc (LGPL)  
**Purpose:** Multi-threading for concurrent operations

**Key Functions Used:**
```c
pthread_create(&thread, NULL, function, arg)  // Create new thread
pthread_join(thread, NULL)                    // Wait for thread completion
pthread_mutex_init(&mutex, NULL)              // Initialize mutex
pthread_mutex_lock(&mutex)                    // Acquire lock
pthread_mutex_unlock(&mutex)                  // Release lock
pthread_cancel(thread)                        // Request thread cancellation
```

**Why pthread:**
- Enables concurrent RPM measurement without blocking main control loop
- Mutex protection ensures thread-safe access to shared variables
- Standard POSIX threading API available on all Linux systems
- Lightweight and efficient for real-time control applications

**Threads in PARMCO:**
1. **Main Thread**: Handles Bluetooth communication and command processing
2. **RPM Thread**: Continuously calculates and reports RPM every 0.3 seconds
3. **Control Thread**: Runs P-controller at 20Hz when in automatic mode

### 3. **Bluetooth (libbluetooth)**

**License:** GPLv2  
**Purpose:** Wireless communication with Android app

**Key Functions Used:**
```c
socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM)  // Create Bluetooth socket
bind(sock, (struct sockaddr*)&loc_addr, len)        // Bind to local adapter
listen(sock, 1)                                      // Listen for connections
accept(sock, (struct sockaddr*)&rem_addr, &len)     // Accept client connection
read(client, buffer, size)                          // Receive data
write(client, buffer, size)                         // Send data
close(sock)                                          // Close socket
```

**Why Bluetooth RFCOMM:**
- Reliable stream-based communication (like TCP but over Bluetooth)
- Standard SPP (Serial Port Profile) protocol compatible with Android
- No pairing required if devices are already paired
- Simple socket programming API similar to network sockets

**Bluetooth Communication Protocol:**

Commands from Android → Raspberry Pi:
```
"M:75"      - Manual mode, set PWM to 75%
"A:1200"    - Auto mode, target 1200 RPM
"F"         - Forward direction
"R"         - Reverse direction
"S"         - Emergency stop
```

Responses from Raspberry Pi → Android:
```
"RPM:1234"  - Current motor speed is 1234 RPM
"MODE:AUTO" - Currently in automatic control mode
"MODE:MAN"  - Currently in manual control mode
"ERR:msg"   - Error message
```

### 4. **Standard C Libraries**

**stdio.h**: File I/O and formatted output
```c
printf()    // Debug output to console
sprintf()   // Format strings for Bluetooth transmission
fopen()     // Open log files
fprintf()   // Write to log files
```

**stdlib.h**: Memory management and utilities
```c
malloc()    // Allocate memory for buffers
free()      // Deallocate memory
atoi()      // Convert string to integer (parse commands)
exit()      // Terminate program
```

**string.h**: String manipulation
```c
strlen()    // Get command length
strncpy()   // Copy command strings safely
strstr()    // Find substrings in commands
memset()    // Clear buffers
```

**unistd.h**: Unix system calls
```c
usleep()    // Microsecond delays
sleep()     // Second delays
close()     // Close file descriptors
```

**signal.h**: Signal handling
```c
signal(SIGINT, handler)   // Handle Ctrl+C gracefully
signal(SIGTERM, handler)  // Handle systemd stop
```

### Compilation Command

```bash
gcc -o motor_control Control.c \
    -lwiringPi \      # Link wiringPi for GPIO
    -lpthread \       # Link pthread for multi-threading
    -lbluetooth \     # Link Bluetooth library
    -lm \             # Link math library (for control calculations)
    -O2 \             # Optimization level 2
    -Wall             # Enable all warnings
```

---

## 🔧 Key Functions

### 1. GPIO Initialization

```c
/**
 * Initialize all GPIO pins for motor control and sensing
 * 
 * Pins configured:
 * - GPIO17 (IN1): Output, controls forward rotation
 * - GPIO27 (IN2): Output, controls reverse rotation  
 * - GPIO18 (PWM): Hardware PWM output for speed control
 * - GPIO22 (RPM): Input with interrupt for IR sensor
 * 
 * PWM Configuration:
 * - Mode: Mark-space (standard PWM)
 * - Frequency: 1 kHz (1000 Hz)
 * - Range: 0-1024 (10-bit resolution)
 * - Clock divider: 19.2 (19.2 MHz base clock / 19.2 = 1 MHz)
 */
void initGPIO() {
    // Initialize wiringPi library using BCM GPIO numbering
    if (wiringPiSetupGpio() == -1) {
        fprintf(stderr, "Failed to initialize wiringPi\n");
        exit(1);
    }
    
    // Configure direction control pins as outputs
    pinMode(GPIO_IN1, OUTPUT);    // GPIO17 - Forward
    pinMode(GPIO_IN2, OUTPUT);    // GPIO27 - Reverse
    
    // Configure PWM pin for speed control
    pinMode(GPIO_PWM, PWM_OUTPUT);
    pwmSetMode(PWM_MODE_MS);      // Mark-space mode
    pwmSetClock(19.2);            // Set clock divider for 1kHz PWM
    pwmSetRange(1024);            // 10-bit resolution (0-1024)
    
    // Configure IR sensor pin as input with pull-up resistor
    pinMode(GPIO_RPM, INPUT);
    pullUpDnControl(GPIO_RPM, PUD_UP);
    
    // Attach interrupt handler for falling edge detection
    // This enables counting pulses from IR sensor without polling
    if (wiringPiISR(GPIO_RPM, INT_EDGE_FALLING, &irSensorISR) < 0) {
        fprintf(stderr, "Failed to setup ISR for RPM sensor\n");
        exit(1);
    }
    
    // Initialize motor to stopped state
    digitalWrite(GPIO_IN1, LOW);
    digitalWrite(GPIO_IN2, LOW);
    pwmWrite(GPIO_PWM, 1024);     // 100% duty = motor disabled (active-low)
    
    printf("GPIO initialized successfully\n");
}
```

### 2. Motor Control Functions

```c
/**
 * Set motor direction to forward
 * IN1=HIGH, IN2=LOW creates forward current flow through H-bridge
 */
void motorForward() {
    digitalWrite(GPIO_IN1, HIGH);
    digitalWrite(GPIO_IN2, LOW);
    printf("Motor direction: FORWARD\n");
}

/**
 * Set motor direction to reverse
 * IN1=LOW, IN2=HIGH creates reverse current flow through H-bridge
 */
void motorReverse() {
    digitalWrite(GPIO_IN1, LOW);
    digitalWrite(GPIO_IN2, HIGH);
    printf("Motor direction: REVERSE\n");
}

/**
 * Set motor speed using PWM (inverted logic for active-low enable)
 * 
 * @param speed: 0-100 percentage
 *               0 = stopped (PWM=1024, enable always LOW)
 *               100 = full speed (PWM=0, enable always HIGH)
 * 
 * PWM inversion necessary because:
 * - MOSFET pulls enable pin LOW when GPIO18 is HIGH
 * - Enable pin must be HIGH for motor to run
 * - Therefore: Lower duty cycle = Higher enable time = Higher speed
 */
void setMotorSpeed(int speed) {
    // Clamp speed to valid range
    if (speed < 0) speed = 0;
    if (speed > 100) speed = 100;
    
    // Convert 0-100% to 0-1024 PWM value, then invert
    // speed=0   → pwm=1024 (always HIGH, motor disabled)
    // speed=50  → pwm=512  (50% duty, motor at half speed)
    // speed=100 → pwm=0    (always LOW, motor at full speed)
    int pwmValue = 1024 - ((speed * 1024) / 100);
    
    pwmWrite(GPIO_PWM, pwmValue);
    printf("Motor speed set to %d%% (PWM=%d)\n", speed, pwmValue);
}

/**
 * Emergency stop - disable motor immediately
 * Sets PWM to maximum (always HIGH) which pulls enable LOW
 */
void motorStop() {
    pwmWrite(GPIO_PWM, 1024);
    digitalWrite(GPIO_IN1, LOW);
    digitalWrite(GPIO_IN2, LOW);
    printf("Motor STOPPED\n");
}
```

### 3. RPM Measurement

```c
// Global variables for RPM calculation (thread-safe with mutex)
volatile unsigned long pulseCount = 0;  // Total pulses detected
pthread_mutex_t rpmMutex;               // Protects pulseCount
float currentRPM = 0.0;                 // Latest calculated RPM

/**
 * Interrupt Service Routine for IR sensor
 * Called automatically on every falling edge of GPIO22
 * 
 * CRITICAL: This function must be as fast as possible
 * - No printf() calls (too slow)
 * - No complex calculations
 * - Just increment counter atomically
 */
void irSensorISR() {
    pthread_mutex_lock(&rpmMutex);
    pulseCount++;  // Count this pulse
    pthread_mutex_unlock(&rpmMutex);
}

/**
 * RPM measurement thread - runs continuously in background
 * Calculates RPM every 0.3 seconds and sends to Android app
 * 
 * @param arg: Pointer to client socket file descriptor
 */
void* rpmMeasurementThread(void* arg) {
    int client_sock = *(int*)arg;
    char buffer[50];
    unsigned long lastCount = 0;
    struct timespec lastTime, currentTime;
    
    // Get initial timestamp
    clock_gettime(CLOCK_MONOTONIC, &lastTime);
    
    while (1) {
        // Wait 300ms between measurements
        usleep(300000);  // 300ms = 0.3 seconds
        
        // Get current time and pulse count
        clock_gettime(CLOCK_MONOTONIC, &currentTime);
        
        pthread_mutex_lock(&rpmMutex);
        unsigned long currentCount = pulseCount;
        pthread_mutex_unlock(&rpmMutex);
        
        // Calculate time elapsed in seconds
        double elapsed = (currentTime.tv_sec - lastTime.tv_sec) +
                        (currentTime.tv_nsec - lastTime.tv_nsec) / 1000000000.0;
        
        // Calculate RPM from pulse count
        // Formula: RPM = (pulses / 3) × (60 / time)
        // - Divide by 3 because there are 3 markers per revolution
        // - Multiply by 60 to convert from RPS to RPM
        unsigned long pulses = currentCount - lastCount;
        currentRPM = (pulses / 3.0) * (60.0 / elapsed);
        
        // Send RPM to Android app
        snprintf(buffer, sizeof(buffer), "RPM:%.1f\n", currentRPM);
        write(client_sock, buffer, strlen(buffer));
        
        // Update for next iteration
        lastCount = currentCount;
        lastTime = currentTime;
    }
    
    return NULL;
}
```

### 4. P-Controller

```c
// P-controller parameters
#define KP 0.5              // Proportional gain (tunable)
#define CONTROL_RATE_HZ 20  // Control loop frequency (20Hz = 50ms)

float targetRPM = 0.0;      // Desired speed setpoint from app
int controlMode = 0;        // 0=Manual, 1=Automatic

/**
 * P-Controller thread - runs at 20Hz when in automatic mode
 * Continuously adjusts motor PWM to maintain target RPM
 * 
 * Control algorithm:
 * 1. Read current RPM from sensor
 * 2. Calculate error = target - current
 * 3. Calculate control output = Kp × error
 * 4. Apply output as motor PWM (0-100%)
 * 5. Wait 50ms (20Hz rate)
 * 6. Repeat
 */
void* pControllerThread(void* arg) {
    float error, controlOutput;
    int pwmCommand;
    
    while (controlMode == 1) {  // Run while in auto mode
        // Calculate error (positive = too slow, negative = too fast)
        error = targetRPM - currentRPM;
        
        // Proportional control: output proportional to error
        controlOutput = KP * error;
        
        // Convert control output to PWM command (0-100%)
        pwmCommand = (int)controlOutput;
        
        // Clamp to valid range
        if (pwmCommand < 0) pwmCommand = 0;
        if (pwmCommand > 100) pwmCommand = 100;
        
        // Apply to motor
        setMotorSpeed(pwmCommand);
        
        // Debug output
        printf("[P-CTRL] Target=%.1f Current=%.1f Error=%.1f PWM=%d\n",
               targetRPM, currentRPM, error, pwmCommand);
        
        // Wait 50ms for next control cycle (20Hz)
        usleep(50000);
    }
    
    return NULL;
}
```

### 5. Bluetooth Server

```c
/**
 * Initialize Bluetooth RFCOMM server socket
 * Creates socket and binds to local Bluetooth adapter
 * 
 * @return: Server socket file descriptor, or -1 on error
 */
int initBluetoothServer() {
    int server_sock;
    struct sockaddr_rc loc_addr = {0};
    
    // Create Bluetooth RFCOMM socket (like TCP socket but for Bluetooth)
    server_sock = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (server_sock < 0) {
        perror("Failed to create Bluetooth socket");
        return -1;
    }
    
    // Bind to local Bluetooth adapter (any adapter, channel 1)
    loc_addr.rc_family = AF_BLUETOOTH;
    loc_addr.rc_bdaddr = *BDADDR_ANY;  // Use any available adapter
    loc_addr.rc_channel = (uint8_t) 1;  // RFCOMM channel 1
    
    if (bind(server_sock, (struct sockaddr*)&loc_addr, sizeof(loc_addr)) < 0) {
        perror("Failed to bind Bluetooth socket");
        close(server_sock);
        return -1;
    }
    
    // Listen for incoming connections (queue size = 1)
    if (listen(server_sock, 1) < 0) {
        perror("Failed to listen on Bluetooth socket");
        close(server_sock);
        return -1;
    }
    
    printf("Bluetooth server listening on channel 1\n");
    printf("Waiting for Android app to connect...\n");
    
    return server_sock;
}

/**
 * Accept incoming Bluetooth connection from Android app
 * Blocks until client connects
 * 
 * @param server_sock: Server socket from initBluetoothServer()
 * @return: Client socket file descriptor, or -1 on error
 */
int acceptBluetoothClient(int server_sock) {
    int client_sock;
    struct sockaddr_rc rem_addr = {0};
    socklen_t opt = sizeof(rem_addr);
    char buf[1024] = {0};
    
    // Wait for client connection (blocking)
    client_sock = accept(server_sock, (struct sockaddr*)&rem_addr, &opt);
    if (client_sock < 0) {
        perror("Failed to accept Bluetooth connection");
        return -1;
    }
    
    // Get client Bluetooth address for logging
    ba2str(&rem_addr.rc_bdaddr, buf);
    printf("Android app connected from %s\n", buf);
    
    return client_sock;
}
```

### 6. Command Processing

```c
/**
 * Process commands received from Android app
 * Parses command strings and executes corresponding actions
 * 
 * Command format:
 * - "M:75"    → Manual mode, PWM 75%
 * - "A:1200"  → Auto mode, target 1200 RPM
 * - "F"       → Forward direction
 * - "R"       → Reverse direction
 * - "S"       → Stop motor
 * 
 * @param command: Command string from Android app
 */
void processCommand(char* command) {
    char response[100];
    
    // Remove newline if present
    char* newline = strchr(command, '\n');
    if (newline) *newline = '\0';
    
    printf("Received command: %s\n", command);
    
    // Manual mode: M:PWM
    if (command[0] == 'M' && command[1] == ':') {
        controlMode = 0;  // Switch to manual mode
        int pwm = atoi(&command[2]);  // Parse PWM value
        setMotorSpeed(pwm);
        sprintf(response, "MODE:MAN,PWM:%d\n", pwm);
    }
    // Auto mode: A:RPM
    else if (command[0] == 'A' && command[1] == ':') {
        controlMode = 1;  // Switch to auto mode
        targetRPM = atof(&command[2]);  // Parse target RPM
        
        // Create P-controller thread if not already running
        pthread_t control_thread;
        pthread_create(&control_thread, NULL, pControllerThread, NULL);
        
        sprintf(response, "MODE:AUTO,TARGET:%.1f\n", targetRPM);
    }
    // Forward direction
    else if (command[0] == 'F') {
        motorForward();
        sprintf(response, "DIR:FWD\n");
    }
    // Reverse direction
    else if (command[0] == 'R') {
        motorReverse();
        sprintf(response, "DIR:REV\n");
    }
    // Emergency stop
    else if (command[0] == 'S') {
        motorStop();
        controlMode = 0;  // Return to manual mode
        sprintf(response, "MOTOR:STOPPED\n");
    }
    // Unknown command
    else {
        sprintf(response, "ERR:Unknown command\n");
    }
    
    // Send response back to app (if needed)
    // write(client_sock, response, strlen(response));
}
```

---

## 🎮 Control Algorithms

### P-Controller Theory

The system uses a **Proportional (P) controller** to automatically regulate motor speed. This is the simplest form of PID control, using only the proportional term.

#### **Control Equation**

```
u(t) = Kp × e(t)

Where:
u(t) = Control output (PWM percentage, 0-100%)
Kp  = Proportional gain (tuning parameter)
e(t) = Error signal (target RPM - current RPM)
```

#### **How It Works**

1. **Measure**: Read current motor RPM from IR sensor
2. **Compare**: Calculate error = target RPM - current RPM
3. **Compute**: Calculate control output = Kp × error
4. **Apply**: Set motor PWM to control output value
5. **Repeat**: Run at 20Hz (every 50ms)

#### **Example**

```
Target RPM: 1000
Current RPM: 800
Kp = 0.5

Error = 1000 - 800 = 200 RPM (too slow)
Control output = 0.5 × 200 = 100% PWM (full power)
```

#### **Tuning Kp**

The proportional gain `Kp` determines how aggressively the controller responds to errors:

- **Too low (e.g. Kp = 0.1)**: Slow response, may never reach target
- **Just right (e.g. Kp = 0.5)**: Fast response, minimal overshoot
- **Too high (e.g. Kp = 2.0)**: Fast but oscillates around target

**Current Setting:** `Kp = 0.5` (empirically determined)

### Why P-Controller on Pi Instead of App?

**Latency Comparison:**

| Architecture | Control Latency | Performance |
|--------------|-----------------|-------------|
| App-based control | 100-200ms (Bluetooth delay) | ❌ Poor - oscillates |
| Pi-based control | <1ms (local) | ✅ Excellent - stable |

The P-controller runs directly on the Raspberry Pi at 20Hz (50ms period) to eliminate Bluetooth latency from the feedback loop. This provides near-instantaneous response to speed changes, resulting in stable, accurate speed regulation.

**Android app only sends**:
- Target RPM setpoint (when user changes slider)
- Direction commands (Forward/Reverse)
- Mode changes (Manual/Auto)

**Raspberry Pi handles**:
- Real-time speed measurement (IR sensor)
- P-controller calculations (20Hz)
- PWM output to motor

---

## 📡 Bluetooth Communication

### Protocol Specification

#### **Command Format** (Android → Raspberry Pi)

All commands are ASCII strings terminated with newline `\n`.

| Command | Format | Description | Example |
|---------|--------|-------------|---------|
| Manual Mode | `M:<PWM>` | Set manual PWM (0-100%) | `M:75\n` |
| Auto Mode | `A:<RPM>` | Set target RPM for P-controller | `A:1200\n` |
| Forward | `F` | Set motor direction to forward | `F\n` |
| Reverse | `R` | Set motor direction to reverse | `R\n` |
| Stop | `S` | Emergency stop motor | `S\n` |

#### **Response Format** (Raspberry Pi → Android)

| Response | Format | Description | Example |
|----------|--------|-------------|---------|
| RPM Update | `RPM:<value>` | Current motor speed | `RPM:1234.5\n` |
| Mode Status | `MODE:<type>` | Current control mode | `MODE:AUTO\n` |
| Error | `ERR:<message>` | Error occurred | `ERR:Sensor failure\n` |

#### **Communication Flow**

```
[Android App]                           [Raspberry Pi]
      |                                        |
      |  -------- Connect Bluetooth -------> |
      |                                        |
      | <------- RPM:0.0 (initial) --------- |
      |                                        |
      |  ---------- F (Forward) -----------> |
      | <------- DIR:FWD ------------------- |
      |                                        |
      |  --------- A:1000 (Auto) ----------> |
      | <------- MODE:AUTO,TARGET:1000 ----- |
      |                                        |
      | <------- RPM:234.5 ----------------- | (every 0.3s)
      | <------- RPM:456.8 ----------------- |
      | <------- RPM:789.2 ----------------- |
      | <------- RPM:945.1 ----------------- |
      | <------- RPM:1002.3 ---------------- | (reached target)
      |                                        |
      |  ---------- S (Stop) --------------> |
      | <------- MOTOR:STOPPED ------------- |
      |                                        |
```

### Bluetooth Pairing

**Initial Setup (One-time):**

1. On Raspberry Pi:
```bash
sudo bluetoothctl
power on
agent on
default-agent
discoverable on
pairable on
```

2. On Android phone:
   - Settings → Bluetooth → Scan
   - Find "raspberrypi" and pair
   - Enter PIN if prompted (usually 0000 or 1234)

3. After pairing, automatic connection:
   - Raspberry Pi listens on RFCOMM channel 1
   - Android app connects automatically on launch
   - No manual pairing needed after first setup

### Security Considerations

⚠️ **Security Notes:**

- Bluetooth connection is NOT encrypted by default
- Anyone within Bluetooth range (~10m) could potentially connect
- For production use, implement:
  - Bluetooth encryption (secure pairing)
  - Authentication token system
  - MAC address whitelisting

**Current Implementation:** Designed for educational/demo purposes with single trusted Android device.

---

## 🚀 Installation and Deployment

### Prerequisites

```bash
# Update system
sudo apt-get update
sudo apt-get upgrade

# Install required libraries
sudo apt-get install -y wiringpi bluetooth libbluetooth-dev

# Enable Bluetooth
sudo systemctl enable bluetooth
sudo systemctl start bluetooth
```

### Compilation

```bash
cd /home/group4/Group-4/FinalProject/Checkpoint_3_Code/Control/

# Compile the C program
gcc -o motor_control Control.c \
    -lwiringPi \
    -lpthread \
    -lbluetooth \
    -lm \
    -O2 \
    -Wall

# Make executable
chmod +x motor_control

# Test run (manual start)
sudo ./motor_control
```

**Note:** Requires `sudo` for GPIO and Bluetooth access.

### Systemd Service Installation

The system includes automatic startup on boot using systemd.

#### **Service File (`parmco.service`)**

```ini
[Unit]
Description=PARMCO Motor Controller
After=bluetooth.service network.target
Requires=bluetooth.service

[Service]
Type=simple
ExecStart=/home/group4/Group-4/FinalProject/Checkpoint_3_Code/Control/motor_control
WorkingDirectory=/home/group4/Group-4/FinalProject/Checkpoint_3_Code/Control/
Restart=always
RestartSec=10
User=root

[Install]
WantedBy=multi-user.target
```

#### **Installation Script (`install_service.sh`)**

```bash
#!/bin/bash

# PARMCO Service Installation Script
# Installs systemd service for automatic startup

echo "Installing PARMCO motor control service..."

# Copy service file to systemd directory
sudo cp parmco.service /etc/systemd/system/

# Reload systemd daemon
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable parmco.service

# Start service now
sudo systemctl start parmco.service

# Show service status
sudo systemctl status parmco.service

echo "Installation complete!"
echo "Service will now start automatically on boot."
echo ""
echo "Useful commands:"
echo "  sudo systemctl status parmco   - Check service status"
echo "  sudo systemctl stop parmco     - Stop service"
echo "  sudo systemctl start parmco    - Start service"
echo "  sudo systemctl restart parmco  - Restart service"
echo "  sudo journalctl -u parmco -f   - View live logs"
```

#### **Install Service**

```bash
# Make installation script executable
chmod +x install_service.sh

# Run installation
./install_service.sh
```

### Service Management

```bash
# Check service status
sudo systemctl status parmco

# View logs
sudo journalctl -u parmco -f

# Stop service
sudo systemctl stop parmco

# Start service
sudo systemctl start parmco

# Restart service
sudo systemctl restart parmco

# Disable auto-start
sudo systemctl disable parmco

# Re-enable auto-start
sudo systemctl enable parmco
```

---

## 📖 Usage

### Manual Testing

```bash
# 1. Start the motor control server
sudo ./motor_control

# Expected output:
# GPIO initialized successfully
# Bluetooth server listening on channel 1
# Waiting for Android app to connect...

# 2. Launch Android app on phone
# 3. App should auto-connect and display "Connected"

# 4. Control motor from app:
# - Set direction (Forward/Reverse buttons)
# - Adjust speed slider (Manual mode)
# - Or set target RPM (Auto mode)
# - Monitor real-time RPM display

# 5. Stop server: Ctrl+C
# GPIO cleanup will occur automatically
```

### Integration with Android App

The Android app (developed by Divya Vemuri) provides:

1. **Connection Management**
   - Automatic Bluetooth discovery
   - One-tap connection to Raspberry Pi
   - Connection status indicator

2. **Manual Control**
   - Direction buttons (Forward/Reverse)
   - Speed slider (0-100% PWM)
   - Real-time RPM display
   - Emergency STOP button

3. **Automatic Control**
   - Target RPM input field
   - P-controller enable/disable
   - Real-time error display
   - Visual feedback of control action

4. **Status Monitoring**
   - Current RPM (updated every 0.3s)
   - Connection state
   - Control mode (Manual/Auto)
   - Motor direction

**App Repository:** (Link to Divya's Android app code)

---

## 🔧 Troubleshooting

### Motor Not Running

**Symptom:** Motor does not rotate when commands sent

**Possible Causes:**

1. **Power supply issue**
   ```bash
   # Check 12V supply is connected
   # Measure voltage at L293D Vcc2 pin
   # Should read 12V DC
   ```

2. **GPIO pin misconfiguration**
   ```bash
   # Test GPIO outputs
   sudo ./test_gpio.sh
   
   # Should see:
   # GPIO17: HIGH → Motor should twitch forward
   # GPIO27: HIGH → Motor should twitch reverse
   # GPIO18 PWM: Should see LED dimming
   ```

3. **L293D enable pin stuck low**
   ```bash
   # Measure voltage at L293D EN1 (Pin 1)
   # When motor should run: 3.3V
   # When stopped: 0V
   
   # If always 0V:
   # - Check MOSFET is not shorted
   # - Check pull-up resistor R2 is connected to 3.3V (NOT 12V!)
   # - Check GPIO18 is not stuck HIGH
   ```

### RPM Always Reads Zero

**Symptom:** RPM display shows 0.0 even when motor running

**Possible Causes:**

1. **IR sensor not powered**
   ```bash
   # Check sensor VCC and GND connections
   # Measure VCC pin: Should be 3.3V
   ```

2. **IR sensor misaligned**
   ```bash
   # Check sensor is close to motor shaft (2-5mm)
   # Verify reflective markers are on shaft
   # Markers should be white tape or reflective stickers
   # Should have 3 markers evenly spaced
   ```

3. **Interrupt not firing**
   ```bash
   # Test IR sensor manually
   # Wave white paper in front of sensor
   # Check debug output for pulse count
   
   # If no pulses detected:
   # - Check GPIO22 is configured as input
   # - Check interrupt handler is registered
   # - Test sensor with multimeter (OUT pin should toggle)
   ```

### Bluetooth Connection Fails

**Symptom:** Android app shows "Disconnected" or "Connection failed"

**Possible Causes:**

1. **Bluetooth service not running**
   ```bash
   # Check Bluetooth status
   sudo systemctl status bluetooth
   
   # If not running:
   sudo systemctl start bluetooth
   ```

2. **Devices not paired**
   ```bash
   # On Raspberry Pi:
   sudo bluetoothctl
   devices  # List paired devices
   
   # If Android device not listed:
   discoverable on
   pairable on
   # Then pair from Android phone
   ```

3. **Server not listening**
   ```bash
   # Check if motor_control is running
   ps aux | grep motor_control
   
   # Check service logs
   sudo journalctl -u parmco -f
   
   # Should see: "Bluetooth server listening on channel 1"
   ```

### P-Controller Oscillates

**Symptom:** Motor speed oscillates around target RPM instead of stabilizing

**Possible Causes:**

1. **Kp gain too high**
   ```c
   // In Control.c, reduce Kp:
   #define KP 0.3  // Was 0.5, reduce to 0.3
   
   // Recompile and test
   ```

2. **Control rate too slow**
   ```c
   // Increase control loop frequency:
   #define CONTROL_RATE_HZ 50  // Was 20Hz, increase to 50Hz
   
   // Recompile and test
   ```

3. **Motor has high inertia**
   ```c
   // Add derivative term (upgrade to PD controller):
   float derivative = (error - last_error) / dt;
   control_output = (KP * error) + (KD * derivative);
   ```

### High CPU Usage

**Symptom:** Raspberry Pi CPU at 100%, system sluggish

**Possible Causes:**

1. **Busy-waiting loop**
   ```c
   // Bad (busy-wait):
   while (1) {
       // Check something continuously
   }
   
   // Good (sleep between checks):
   while (1) {
       // Do work
       usleep(50000);  // Sleep 50ms
   }
   ```

2. **Too many threads**
   ```bash
   # Check thread count
   ps -eLf | grep motor_control
   
   # Should see:
   # - 1 main thread
   # - 1 RPM measurement thread
   # - 1 P-controller thread (if auto mode)
   # = 3 threads total
   
   # If more, check for thread leaks
   ```

### Emergency Recovery

**If motor runs out of control:**

1. **Physical cutoff**: Disconnect 12V power supply
2. **Software stop**: Press Ctrl+C on server terminal
3. **Service stop**: `sudo systemctl stop parmco`
4. **Emergency stop**: Press STOP button in Android app

**After emergency stop:**
```bash
# Check what went wrong
sudo journalctl -u parmco -n 100

# Reset GPIO pins
sudo ./reset_gpio.sh

# Restart service
sudo systemctl restart parmco
```

---

## 📁 Project Files

### Complete File Listing

```
/home/group4/Group-4/FinalProject/Checkpoint_3_Code/Control/
├── Control.c                  # Main C control server (3000+ lines)
├── motor_control              # Compiled binary
├── parmco.service             # Systemd service file
├── install_service.sh         # Service installation script
├── reset_gpio.sh              # GPIO reset utility
├── test_gpio.sh               # GPIO testing script
├── README.md                  # Setup and usage instructions
└── circuit_diagram.png        # IEEE-standard schematic (300 DPI)
```

### Additional Resources

- **Interactive Website**: `/home/group4/PARMCO-Project-Website.html`
- **Android App**: (Developed by Divya Vemuri, separate repository)
- **Documentation**: This file + Android app documentation
- **Parts List**: See [Hardware Design](#hardware-design) section

---

## 📚 References

### Hardware Datasheets

- [L293D Motor Driver IC](https://www.ti.com/lit/ds/symlink/l293.pdf) - Texas Instruments
- [IRFZ44N N-Channel MOSFET](https://www.infineon.com/dgdl/irfz44npbf.pdf) - Infineon Technologies
- [Raspberry Pi 4 GPIO](https://www.raspberrypi.com/documentation/computers/os.html#gpio-and-the-40-pin-header) - Raspberry Pi Foundation

### Software Libraries

- [wiringPi Documentation](http://wiringpi.com/) - Gordon Henderson
- [Linux Bluetooth Programming](https://people.csail.mit.edu/albert/bluez-intro/) - MIT CSAIL
- [POSIX Threads Tutorial](https://computing.llnl.gov/tutorials/pthreads/) - Lawrence Livermore National Laboratory

### Control Theory

- "Feedback Control of Dynamic Systems" - Franklin, Powell, Emami-Naeini
- "Modern Control Engineering" - Katsuhiko Ogata

### Academic Standards

- IEEE Standard 315-1975: Graphic Symbols for Electrical and Electronics Diagrams
- IEEE Standard 91-1984: Logic Symbols

---

## 👥 Credits

**Group 4 Project Team:**
- **Jonathan (Group4)**: Hardware design, circuit implementation, C control server, system integration
- **Divya Vemuri**: Android app development, UI/UX design, Bluetooth client implementation

**Course:** Embedded Systems / Microcontroller Applications  
**Institution:** (Your university/institution)  
**Semester:** Fall 2025  

**Special Thanks:**
- Course instructors and TAs
- Raspberry Pi Foundation for excellent documentation
- Open source community for wiringPi and Bluetooth libraries

---

## 📄 License

This project is developed for educational purposes as part of an academic course.

**Code License:** MIT License (for student use)  
**Documentation:** Creative Commons BY-SA 4.0  
**Hardware Design:** Open Source Hardware

**Attribution Required:** If you use this project or its documentation, please credit:
```
PARMCO - Precision Android-Raspberry Motor Controller
Group 4 (Jonathan) & Divya Vemuri, 2025
```

---

## 🔄 Changelog

### Version 2.0 (November 2025)
- Added systemd service integration for automatic startup
- Improved Bluetooth connection handling
- Enhanced P-controller tuning (Kp = 0.5)
- Added IEEE-standard circuit schematic
- Updated documentation with complete code examples
- Added comprehensive troubleshooting guide

### Version 1.0 (October 2025)
- Initial release
- Basic motor control with L293D and MOSFET
- RPM measurement with IR sensor
- Bluetooth communication with Android app
- Manual and automatic control modes

---

**END OF DOCUMENTATION**

For questions or issues, contact:
- Jonathan (Group4): [Contact info]
- Divya Vemuri: [Contact info]

Project repository: [GitHub link if applicable]