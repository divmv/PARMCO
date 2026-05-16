/*
 * Raspberry Pi 4 - Bluetooth Motor Controller with RPM Measurement
 * Authors: Jonathan, Divya Vemuri
 * * OVERVIEW:
 * This program implements a robust motor control system on the Raspberry Pi 4.
 * It uses direct memory-mapped access (GPIO and PWM registers) for precise timing,
 * communicates over Bluetooth RFCOMM, and employs a Proportional-Integral (PI) 
 * controller to regulate motor speed (RPM) automatically.
 * * CIRCUIT AND LOGIC NOTES:
 * 1. Motor Driver: L293D is controlled by two direction pins (IN1, IN2) and one
 * enable pin (EN1).
 * 2. Speed Control: PWM is applied to the L293D EN1 pin, driven by a MOSFET 
 * connected to GPIO18 (PWM Channel 1, ALT5 function).
 * 3. PWM Polarity: The L293D EN pin requires a LOW voltage to ENABLE the motor.
 * Therefore, the PWM output on the RPi is **inverted** (active-low enable logic)
 * so that a 100% duty cycle (DC) command results in a continuous LOW signal 
 * (full power), and a 0% DC command results in a continuous HIGH signal (stop).
 * 4. RPM Sensor: An IR sensor connected to GPIO22 generates 3 pulses per revolution.
 * * Android App Commands (to Pi):
 * START, STOP, FORWARD, REVERSE, 0-100 (Manual Speed), TARGET:0-6500 (Auto RPM)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <signal.h>
#include <errno.h>
#include <ctype.h>
#include <pthread.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <math.h>

// ============================================================================
// GPIO/PWM DEFINITIONS - BCM2711 PHYSICAL ADDRESS MAPPING
// ============================================================================

#define GPIO_IN1    17  // L293D IN1 (Direction)
#define GPIO_IN2    27  // L293D IN2 (Direction)
#define GPIO_PWM    18  // PWM Channel 1 Output (ALT5) -> MOSFET Gate -> L293D EN1
#define GPIO_IR     22  // IR Sensor Input (RPM measurement)

// Base Address for BCM2711 Peripherals (Raspberry Pi 4)
#define BCM2711_PERI_BASE   0xFE000000 
// Memory offsets for key peripherals from the base address
#define GPIO_BASE           (BCM2711_PERI_BASE + 0x200000) // GPIO Controller Registers
#define PWM_BASE            (BCM2711_PERI_BASE + 0x20C000) // PWM Controller Registers
#define CLOCK_BASE          (BCM2711_PERI_BASE + 0x101000) // Clock Manager Registers
#define BLOCK_SIZE          (4*1024)                       // Standard memory page size for mmap

// GPIO Register Offsets (32-bit words, relative to GPIO_BASE)
#define GPFSEL0     0   // Function Select 0 (Pins 0-9)
#define GPFSEL1     1   // Function Select 1 (Pins 10-19). GPIO17 and GPIO18 are here.
#define GPFSEL2     2   // Function Select 2 (Pins 20-29). GPIO22 and GPIO27 are here.
#define GPSET0      7   // Set Register 0. Writing '1' sets the corresponding pin HIGH.
#define GPCLR0      10  // Clear Register 0. Writing '1' sets the corresponding pin LOW.
#define GPLEV0      13  // Level Register 0. Reading shows the current pin state (HIGH/LOW).

// PWM Register Offsets (32-bit words, relative to PWM_BASE)
#define PWM_CTL     0   // PWM Control Register (Enable/Disable, Polarity, Mode)
#define PWM_RNG1    4   // PWM Channel 1 Range Register (Determines PWM Period/Frequency)
#define PWM_DAT1    5   // PWM Channel 1 Data Register (Determines Pulse Width/Duty Cycle)

// Clock Manager Register Offsets (32-bit words, relative to CLOCK_BASE)
// Used to configure the source and divisor for the PWM clock.
#define PWMCLK_CNTL 40  // PWM Clock Control Register (Start/Stop, Source Select)
#define PWMCLK_DIV  41  // PWM Clock Divider Register (Sets the clock frequency)

// ============================================================================
// MOTOR STATE STRUCTURES
// ============================================================================

// State machine for motor operation
typedef struct {
    int running;        // 0=Motor is disabled (stopped), 1=Motor is enabled.
    int direction;      // 1=Forward (IN1=H, IN2=L), -1=Reverse (IN1=L, IN2=H).
    int speed;          // The current PWM duty cycle (0-100%) applied.
    int automatic_mode; // 0=Speed is set manually by user, 1=Speed is set by PI controller.
    int target_rpm;     // Desired RPM for the automatic (PI) control mode.
} MotorState;

// Data structure for the RPM measurement thread
typedef struct {
    volatile int pulse_count; // Counts IR sensor edges between calculation periods.
    volatile int current_rpm; // The last calculated RPM value.
    volatile int should_stop; // Flag to request the thread to exit gracefully.
} RPMMeasurement;

// State structure for the Proportional-Integral (PI) controller
typedef struct {
    float integral;     // $\sum(\text{error} \times \Delta t)$. Accumulates long-term error to eliminate steady-state offset.
    int last_error;     // Previous error value (unused in simple PI, often used for Derivative control).
} PIDState;

// ============================================================================
// GLOBAL VARIABLES
// ============================================================================

// Memory-mapped pointers to hardware registers
volatile unsigned int *gpio = NULL; 
volatile unsigned int *pwm = NULL;  
volatile unsigned int *clk = NULL;  

MotorState motor = {0, 1, 0, 0, 0};     
RPMMeasurement rpm_data = {0, 0, 0};    
PIDState pid_state = {0.0, 0};          

pthread_t rpm_thread;
int rpm_thread_running = 0; 
int should_exit = 0;        
int client_fd = -1;         
int server_fd = -1;         

// PI Controller Constants
float kp = 0.015;           // Proportional Gain: High value gives fast response, but risks overshoot/oscillation.
float ki = 0.001;           // Integral Gain: Eliminates steady-state error, but risks integral windup.
int rpm_deadband = 30;      // RPM tolerance ($\pm 30$) around target where no speed adjustment occurs.
int max_adjustment = 3;     // Limits the maximum $\Delta \text{PWM}$ (percentage) change per 0.3-second cycle.

// ============================================================================
// FUNCTION PROTOTYPES (Omitted for brevity)
// ============================================================================

// ... function prototypes ...

// ============================================================================
// SIGNAL HANDLER - Handles OS termination signals (SIGINT, SIGTERM)
// ============================================================================

void signal_handler(int sig) {
    printf("\n🛑 Caught signal %d, shutting down...\n", sig);
    fflush(stdout);
    should_exit = 1; // Global exit flag set
    
    stop_rpm_measurement(); // Signal the high-priority thread to exit
    
    // Attempt to close all sockets gracefully to release resources
    if (client_fd >= 0) {
        shutdown(client_fd, SHUT_RDWR);
        close(client_fd);
        client_fd = -1;
    }
    if (server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
        close(server_fd);
        server_fd = -1;
    }
}

// ============================================================================
// GPIO/PWM HARDWARE FUNCTIONS - Direct Register Access
// ============================================================================

/**
 * @brief Maps the physical memory of GPIO, PWM, and Clock peripherals.
 * * Uses mmap() on /dev/mem to create volatile pointers to the hardware registers,
 * allowing the program to directly read and write pin states and control PWM.
 */
void setup_io(void) {
    int mem_fd;
    void *gpio_map, *pwm_map, *clk_map;

    // 1. Open /dev/mem (requires root/sudo access)
    if ((mem_fd = open("/dev/mem", O_RDWR | O_SYNC)) < 0) {
        perror("❌ Failed to open /dev/mem (need sudo)");
        exit(1);
    }

    // 2. Map GPIO registers
    gpio_map = mmap(NULL, BLOCK_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, mem_fd, GPIO_BASE);
    if (gpio_map == MAP_FAILED) {
        perror("❌ GPIO mmap failed");
        close(mem_fd);
        exit(1);
    }
    gpio = (volatile unsigned int *)gpio_map;

    // 3. Map PWM registers
    pwm_map = mmap(NULL, BLOCK_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, mem_fd, PWM_BASE);
    if (pwm_map == MAP_FAILED) {
        perror("❌ PWM mmap failed");
        close(mem_fd);
        exit(1);
    }
    pwm = (volatile unsigned int *)pwm_map;

    // 4. Map Clock registers
    clk_map = mmap(NULL, BLOCK_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, mem_fd, CLOCK_BASE);
    if (clk_map == MAP_FAILED) {
        perror("❌ Clock mmap failed");
        close(mem_fd);
        exit(1);
    }
    clk = (volatile unsigned int *)clk_map;

    close(mem_fd); // /dev/mem file descriptor is closed, but memory remains mapped
}

/**
 * @brief Sets a GPIO pin to output mode (Function Select 001).
 * * Modifies the correct GPFSEL register based on the pin number.
 */
void set_gpio_output(int pin) {
    int reg = pin / 10;
    int shift = (pin % 10) * 3;
    unsigned int value = *(gpio + reg);
    // Clear 3 bits: value = value & ~(7 << shift)
    // Set 3 bits to '001': value = value | (1 << shift)
    value = (value & ~(7 << shift)) | (1 << shift);
    *(gpio + reg) = value;
    usleep(100);
}

/**
 * @brief Sets a GPIO pin to input mode (Function Select 000).
 */
void set_gpio_input(int pin) {
    int reg = pin / 10;
    int shift = (pin % 10) * 3;
    unsigned int value = *(gpio + reg);
    // Clear 3 bits: value = value & ~(7 << shift)
    value = (value & ~(7 << shift)); 
    *(gpio + reg) = value;
    usleep(100);
}

/**
 * @brief Sets a GPIO pin HIGH using the GPSET0 register.
 * * Writing '1' to a bit in GPSET0 sets the corresponding pin HIGH without 
 * affecting other pins.
 */
void gpio_set(int pin) {
    *(gpio + GPSET0) = 1 << pin;
    usleep(10);
}

/**
 * @brief Sets a GPIO pin LOW using the GPCLR0 register.
 * * Writing '1' to a bit in GPCLR0 sets the corresponding pin LOW without
 * affecting other pins.
 */
void gpio_clear(int pin) {
    *(gpio + GPCLR0) = 1 << pin;
    usleep(10);
}

/**
 * @brief Reads the current state of a GPIO pin from the GPLEV0 register.
 * @return 1 if HIGH, 0 if LOW.
 */
int gpio_read(int pin) {
    return (*(gpio + GPLEV0) >> pin) & 1;
}

/**
 * @brief Configures all application-specific GPIO pins.
 */
void setup_gpio(void) {
    set_gpio_output(GPIO_IN1);
    set_gpio_output(GPIO_IN2);
    set_gpio_input(GPIO_IR); // Set IR sensor pin as input
    
    // Default motor state is stopped (direction pins low)
    gpio_clear(GPIO_IN1);
    gpio_clear(GPIO_IN2);
    
    printf("✅ GPIO initialized (IN1=%d, IN2=%d, IR=%d)\n", GPIO_IN1, GPIO_IN2, GPIO_IR);
    fflush(stdout);
}

/**
 * @brief Configures the hardware PWM Channel 1 on GPIO18.
 * * This involves setting the GPIO function to ALT5, configuring the clock source, 
 * the clock divider, the PWM range, and finally enabling the PWM with inverted polarity.
 * 
 */
void setup_pwm(void) {
    printf("   Configuring PWM...\n");
    
    int reg = GPIO_PWM / 10;
    int shift = (GPIO_PWM % 10) * 3;
    
    // 1. Set GPIO18 function to ALT5 (Function Select = 010)
    unsigned int value = *(gpio + reg);
    value = (value & ~(7 << shift)) | (2 << shift);
    *(gpio + reg) = value;
    
    // 2. Stop PWM Channel 1
    *(pwm + PWM_CTL) = 0;
    usleep(100);

    // 3. Stop PWM Clock: Write 0x5A000000 | (1 << 5) to kill the clock
    *(clk + PWMCLK_CNTL) = 0x5A000000 | (1 << 5); 
    usleep(100);
    // Wait for the clock to stop (busy bit 7 cleared)
    while ((*(clk + PWMCLK_CNTL) & (1 << 7))) {
        usleep(1);
    }

    // 4. Set PWM Clock Divider and Source
    // Source clock is 19.2MHz. Target PWM frequency is 1kHz.
    // PWM Clock Freq = 19.2MHz / DIV = 200kHz. DIV = 96 (0x60).
    // RNG1 = 200. PWM Freq = 200kHz / 200 = 1kHz.
    *(clk + PWMCLK_DIV) = 0x5A000000 | (96 << 12); // DIV: 96
    // Source is PLLD (0x1) and MASH=0 (0x0). Value = 0x11.
    *(clk + PWMCLK_CNTL) = 0x5A000011; 
    usleep(100);
    printf("   PWM clock: 19.2MHz / 96 = 200kHz\n");

    // 5. Set PWM Range (Period) and initial Data (Duty)
    *(pwm + PWM_RNG1) = 200; // Period is 200 cycles (1kHz)
    *(pwm + PWM_DAT1) = 200; // Initial Data: 200/200 = 100% duty
    printf("   PWM frequency: 1kHz (200kHz / 200)\n");
    
    // 6. Enable PWM Channel 1 with Inverted Polarity
    // Bit 0: ENBL1 (Enable Channel 1)
    // Bit 7: POLA1 (Invert Polarity of Channel 1) -> **CRITICAL** for active-low enable
    *(pwm + PWM_CTL) = (1 << 0) | (1 << 7);
    usleep(100);
    
    printf("✅ PWM initialized (1kHz, inverted logic, starting at 100%% duty)\n");
    fflush(stdout);
}

/**
 * @brief Sets the effective motor speed duty cycle (0-100%).
 * * Translates the user-facing percentage (0-100) into an inverted 
 * register value (200-0) for PWM_DAT1 due to active-low logic.
 * @param duty User-requested duty cycle (0=Off, 100=Max Power).
 */
void set_pwm_duty(int duty) {
    if (duty < 0) duty = 0;
    if (duty > 100) duty = 100;
    
    // Inverted Logic Mapping:
    // If duty = 100% (max power), pwm_value = 0. Output is LOW, motor ENABLED.
    // If duty = 0% (stop), pwm_value = 200. Output is HIGH, motor DISABLED.
    int pwm_value = (100 - duty) * 200 / 100; 
    *(pwm + PWM_DAT1) = pwm_value;
}

/**
 * @brief Cleans up hardware before program exit.
 */
void cleanup_gpio(void) {
    printf("   Stopping motor...\n");
    if (pwm) {
        *(pwm + PWM_DAT1) = 200; // Set PWM to 0% duty (disable motor)
        usleep(10);
        *(pwm + PWM_CTL) = 0;    // Disable PWM channel
    }
    if (gpio) {
        gpio_clear(GPIO_IN1);
        gpio_clear(GPIO_IN2);    // Clear direction pins
    }
    printf("✅ GPIO cleaned up (motor disabled)\n");
}

// ============================================================================
// MOTOR CONTROL FUNCTIONS
// ============================================================================

/**
 * @brief Sets direction pins for forward rotation.
 */
void motor_forward(void) {
    motor.direction = 1;
    gpio_set(GPIO_IN1);
    gpio_clear(GPIO_IN2);
    printf("➡️  Direction: FORWARD\n");
    fflush(stdout);
}

/**
 * @brief Sets direction pins for reverse rotation.
 */
void motor_reverse(void) {
    motor.direction = -1;
    gpio_clear(GPIO_IN1);
    gpio_set(GPIO_IN2);
    printf("⬅️  Direction: REVERSE\n");
    fflush(stdout);
}

/**
 * @brief Updates the stored speed and applies it via PWM.
 * @param speed New speed percentage (0-100).
 */
void motor_set_speed(int speed) {
    if (speed < 0) speed = 0;
    if (speed > 100) speed = 100;
    
    motor.speed = speed;
    
    if (motor.running) {
        set_pwm_duty(speed);
        printf("⚡ Speed: %d%%\n", speed);
        fflush(stdout);
    }
}

/**
 * @brief Enables the motor by applying the current speed.
 */
void motor_start(void) {
    motor.running = 1;
    set_pwm_duty(motor.speed);
    printf("▶️  Motor STARTED at %d%% (%s)\n", 
           motor.speed, motor.direction == 1 ? "FORWARD" : "REVERSE");
    fflush(stdout);
}

/**
 * @brief Disables the motor and resets PI controller state.
 */
void motor_stop(void) {
    motor.running = 0;
    set_pwm_duty(0); // 0% duty = HIGH output = Disabled
    pid_state.integral = 0.0;
    pid_state.last_error = 0;
    printf("⏹  Motor STOPPED\n");
    fflush(stdout);
}

// ============================================================================
// RPM MEASUREMENT & PI CONTROL THREAD
// ============================================================================

/**
 * @brief Dedicated thread for high-frequency RPM measurement and PI control.
 * * Runs continuously, polling the IR sensor pin for pulses and calculating/adjusting 
 * RPM every 0.3 seconds.
 */
void* rpm_measurement_thread(void* arg) {
    int last_state = 0;
    int current_state = 0;
    struct timeval last_calc_time, current_time;
    double elapsed_time;
    
    printf("🔄 RPM measurement thread STARTED\n");
    
    gettimeofday(&last_calc_time, NULL);
    
    // Loop runs extremely fast (polling at ~100us intervals)
    while (!rpm_data.should_stop && !should_exit) {
        current_state = gpio_read(GPIO_IR); // Poll the IR sensor pin
        
        // Edge detection: Count pulses only on a LOW to HIGH transition (rising edge)
        if (current_state == 1 && last_state == 0) {
            rpm_data.pulse_count++;
        }
        
        last_state = current_state;
        
        gettimeofday(&current_time, NULL);
        // Calculate elapsed time in seconds since the last calculation
        elapsed_time = (current_time.tv_sec - last_calc_time.tv_sec) + 
                       (current_time.tv_usec - last_calc_time.tv_usec) / 1000000.0;
        
        // Time to calculate RPM and perform PI control (every 0.3 seconds)
        if (elapsed_time >= 0.3) {
            int calculated_rpm = 0;
            if (rpm_data.pulse_count > 0) {
                // RPM Calculation Formula:
                // RPM = (Pulses * 60 seconds/min) / (Pulses_per_Rev * Elapsed_Time_Seconds)
                // Pulses_per_Rev = 3
                calculated_rpm = (int)((rpm_data.pulse_count * 60.0) / (3.0 * elapsed_time));
            }
            
            rpm_data.current_rpm = calculated_rpm;
            
            // --- PI CONTROLLER LOGIC ---
            if (motor.automatic_mode && motor.running) {
                int error = motor.target_rpm - calculated_rpm; // Error = Target RPM - Actual RPM
                int abs_error = abs(error);
                
                // Only adjust speed if error is outside the specified deadband
                if (abs_error > rpm_deadband) {
                    
                    // 1. Integral Term Accumulation (I-term)
                    // Integral += Error * Delta Time
                    pid_state.integral += error * elapsed_time;
                    
                    // Integral Anti-Windup: Clamp the integral term to prevent it from growing
                    // too large during periods of saturation (e.g., motor stuck at 100% speed).
                    if (pid_state.integral > 1000) pid_state.integral = 1000;
                    if (pid_state.integral < -1000) pid_state.integral = -1000;
                    
                    // 2. Proportional Term (P-term)
                    float p_term = kp * error;
                    // 3. I-term
                    float i_term = ki * pid_state.integral;
                    
                    float adjustment = p_term + i_term;
                    
                    // Limit the adjustment magnitude to ensure stability (smooth changes)
                    int adjustment_clamped = (int)adjustment;
                    if (adjustment_clamped > max_adjustment) adjustment_clamped = max_adjustment;
                    if (adjustment_clamped < -max_adjustment) adjustment_clamped = -max_adjustment;
                    
                    // Calculate the new PWM speed
                    int new_speed = motor.speed + adjustment_clamped;
                    
                    // Clamp new speed to the physical limits (0-100%)
                    if (new_speed < 0) new_speed = 0;
                    if (new_speed > 100) new_speed = 100;
                    
                    if (new_speed != motor.speed) {
                        motor.speed = new_speed;
                        set_pwm_duty(new_speed); // Apply the calculated speed
                        // Detailed debug output for PI control
                        printf("   🎯 AUTO: Target=%d, Actual=%d, Error=%d, P=%.2f, I=%.2f, Adj=%d, Speed=%d%%\n",
                               motor.target_rpm, calculated_rpm, error, p_term, i_term, adjustment_clamped, new_speed);
                        fflush(stdout);
                    }
                } else {
                    // Motor is running within the acceptable RPM range
                    printf("   ✅ AUTO: Target=%d, Actual=%d (within deadband $\\pm%d$ RPM)\n",
                           motor.target_rpm, calculated_rpm, rpm_deadband);
                    fflush(stdout);
                }
                
                pid_state.last_error = error;
            }
            // --- END PI CONTROLLER LOGIC ---
            
            // Send the calculated RPM back to the Bluetooth client
            if (client_fd >= 0) {
                char rpm_msg[32];
                snprintf(rpm_msg, sizeof(rpm_msg), "%d\n", calculated_rpm);
                ssize_t bytes_written = write(client_fd, rpm_msg, strlen(rpm_msg));
                if (bytes_written < 0) {
                    fprintf(stderr, "   ❌ Failed to send RPM: %s\n", strerror(errno));
                    fflush(stderr);
                    break; // Exit loop if client communication fails
                }
            }
            
            rpm_data.pulse_count = 0; // Reset counter
            last_calc_time = current_time; // Reset timer
        }
        
        // Brief sleep to yield CPU time, crucial in a tight polling loop
        usleep(100); 
    }
    
    printf("🔄 RPM measurement thread STOPPED\n");
    fflush(stdout);
    return NULL;
}

/**
 * @brief Starts the RPM measurement thread and detaches it.
 */
void start_rpm_measurement(void) {
    if (rpm_thread_running) return;
    
    // Reset all measurement and PI control data
    rpm_data.pulse_count = 0;
    rpm_data.current_rpm = 0;
    rpm_data.should_stop = 0;
    pid_state.integral = 0.0;
    pid_state.last_error = 0;
    
    int result = pthread_create(&rpm_thread, NULL, rpm_measurement_thread, NULL);
    if (result != 0) {
        fprintf(stderr, "❌ Failed to create RPM measurement thread: %s\n", strerror(result));
        return;
    }
    
    // Detach the thread: its resources will be cleaned up automatically upon exit
    pthread_detach(rpm_thread);
    
    rpm_thread_running = 1;
    printf("✅ RPM measurement thread created successfully\n");
    fflush(stdout);
}

/**
 * @brief Signals the RPM thread to stop and cleans up related state.
 */
void stop_rpm_measurement(void) {
    if (!rpm_thread_running) return;
    
    printf("⏹  Stopping RPM measurement...\n");
    fflush(stdout);
    
    rpm_data.should_stop = 1; // Set the flag watched by the thread loop
    rpm_thread_running = 0;
    
    usleep(500000); // Wait for half a second to ensure the thread exits
    rpm_data.pulse_count = 0;
    rpm_data.current_rpm = 0;
    
    printf("✅ RPM measurement stopped\n");
    fflush(stdout);
}

// ============================================================================
// BLUETOOTH FUNCTIONS
// ============================================================================

/**
 * @brief Sets up the Bluetooth RFCOMM server socket.
 * * Creates a socket, binds it to RFCOMM channel 1 (standard for SPP), and listens
 * for incoming connections from the Android app.
 * @return The server file descriptor, or -1 on failure.
 */
int setup_bluetooth_server(void) {
    struct sockaddr_rc loc_addr = {0};
    int opt = 1;

    // 1. Create socket: AF_BLUETOOTH, SOCK_STREAM (reliable connection), BTPROTO_RFCOMM
    server_fd = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    if (server_fd < 0) {
        perror("❌ Failed to create Bluetooth socket");
        return -1;
    }

    // Reuse address option
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // 2. Bind socket
    loc_addr.rc_family = AF_BLUETOOTH;
    loc_addr.rc_bdaddr = *BDADDR_ANY; // Bind to any available Bluetooth adapter
    loc_addr.rc_channel = 1;          // RFCOMM Channel 1
    if (bind(server_fd, (struct sockaddr *)&loc_addr, sizeof(loc_addr)) < 0) {
        perror("❌ Bluetooth bind failed");
        close(server_fd);
        return -1;
    }

    // 3. Listen for connections
    if (listen(server_fd, 1) < 0) {
        perror("❌ Bluetooth listen failed");
        close(server_fd);
        return -1;
    }

    printf("✅ Bluetooth server ready on RFCOMM channel 1\n");
    printf("📱 Waiting for Android app connection...\n");
    return server_fd;
}

/**
 * @brief Sends a response message followed by a newline to the connected client.
 */
void send_response(const char *msg) {
    if (client_fd >= 0) {
        char response[256];
        snprintf(response, sizeof(response), "%s\n", msg);
        write(client_fd, response, strlen(response));
    }
}

/**
 * @brief Removes leading and trailing whitespace from a string.
 * @param str The string to modify in place.
 */
void trim_whitespace(char *str) {
    char *end;
    
    // Trim leading space
    while(isspace((unsigned char)*str)) str++;
    
    if(*str == 0) return;
    
    // Trim trailing space
    end = str + strlen(str) - 1;
    while(end > str && isspace((unsigned char)*end)) end--;
    
    // Write new null terminator
    end[1] = '\0';
}

/**
 * @brief Processes and executes command strings received from the client.
 */
void process_command(const char *cmd) {
    char command[128];
    strncpy(command, cmd, sizeof(command) - 1);
    command[sizeof(command) - 1] = '\0';
    
    trim_whitespace(command);
    
    // Convert to uppercase for case-insensitive command matching
    for (int i = 0; command[i]; i++) {
        command[i] = toupper(command[i]);
    }
    
    printf("📥 Received: '%s'\n", command);
    fflush(stdout);
    
    if (strcmp(command, "START") == 0) {
        motor_start();
        send_response("Motor started");
        
    } else if (strcmp(command, "STOP") == 0) {
        motor_stop();
        send_response("Motor stopped");
        
    } else if (strcmp(command, "FORWARD") == 0) {
        motor_forward();
        send_response("Direction: FORWARD");
        
    } else if (strcmp(command, "REVERSE") == 0) {
        motor_reverse();
        send_response("Direction: REVERSE");
        
    } else if (strncmp(command, "TARGET:", 7) == 0) {
        // Set target RPM for automatic PI control mode
        int target = atoi(command + 7);
        if (target >= 0 && target <= 6500) {
            motor.automatic_mode = 1;
            motor.target_rpm = target;
            pid_state.integral = 0.0; // Reset integral term upon new target
            pid_state.last_error = 0;
            printf("🎯 Automatic mode: Target RPM = %d\n", target);
            char response[64];
            snprintf(response, sizeof(response), "Target RPM set: %d", target);
            send_response(response);
        } else {
            send_response("ERROR: Target RPM out of range (0-6500)");
        }
        
    } else {
        // Assume command is a manual speed percentage (0-100)
        int speed = atoi(command);
        if (speed >= 0 && speed <= 100) {
            motor.automatic_mode = 0; // Switch to manual control
            pid_state.integral = 0.0; // Reset integral term
            pid_state.last_error = 0;
            motor_set_speed(speed);
            printf("🔧 Manual mode: Speed = %d%%\n", speed);
            char response[64];
            snprintf(response, sizeof(response), "Speed set: %d%%", speed);
            send_response(response);
        } else {
            printf("⚠️  Unknown command: %s\n", command);
            fflush(stdout);
            send_response("ERROR: Unknown command");
        }
    }
}

/**
 * @brief Handles the main Bluetooth client session loop.
 */
void handle_client_connection(int client) {
    char buffer[256];
    char line_buffer[256] = {0}; // Buffer to accumulate full command lines
    int line_pos = 0;
    ssize_t bytes_read;
    client_fd = client; // Set global FD for RPM thread to use
    
    printf("✅ Client connected from Android app! (fd=%d)\n", client);
    send_response("Connected to Raspberry Pi Motor Controller");
    
    start_rpm_measurement(); // Start the RPM/PI control thread

    // Loop until the client disconnects or the program exits
    while (!should_exit) {
        // Blocking read call
        bytes_read = read(client, buffer, sizeof(buffer) - 1);
        
        if (bytes_read <= 0) {
            // Client closed the connection or an error occurred
            printf("📱 Client disconnected\n");
            break;
        }

        buffer[bytes_read] = '\0';

        // Process bytes read: commands are terminated by '\n' or '\r'
        for (ssize_t i = 0; i < bytes_read; i++) {
            if (buffer[i] == '\n' || buffer[i] == '\r') {
                if (line_pos > 0) {
                    line_buffer[line_pos] = '\0';
                    process_command(line_buffer); // Process the complete command
                    line_pos = 0;
                }
            } else if (line_pos < sizeof(line_buffer) - 1) {
                line_buffer[line_pos++] = buffer[i]; // Accumulate characters
            }
        }
    }
    
    stop_rpm_measurement(); // Stop RPM/PI thread
    close(client);
    client_fd = -1;
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

int main(void) {
    struct sockaddr_rc rem_addr = {0};
    socklen_t opt = sizeof(rem_addr);

    printf("\n");
    printf("╔════════════════════════════════════════════════════════════╗\n");
    printf("║   RASPBERRY PI 4 - BLUETOOTH MOTOR CONTROLLER             ║\n");
    printf("║   L293D Motor Driver with MOSFET Enable Control           ║\n");
    printf("║   Authors: Jonathan, Divya Vemuri                         ║\n");
    printf("╚════════════════════════════════════════════════════════════╝\n");
    printf("\n");

    // Capture SIGINT (Ctrl+C) and SIGTERM (kill) for safe shutdown
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    printf("🔧 Initializing hardware...\n");
    setup_io();   // Memory-map peripherals
    setup_gpio(); // Configure pins
    setup_pwm();  // Configure hardware PWM with inverted logic
    
    // Print operational parameters
    printf("\n");
    printf("📋 Motor Status:\n");
    printf("   Direction: %s\n", motor.direction == 1 ? "FORWARD" : "REVERSE");
    printf("   Speed: %d%%\n", motor.speed);
    printf("   Running: %s\n", motor.running ? "Yes" : "No");
    printf("   Mode: %s\n", motor.automatic_mode ? "AUTOMATIC" : "MANUAL");
    printf("\n");
    printf("⚙️  PI Controller Parameters:\n");
    printf("   Kp = %.3f\n", kp);
    printf("   Ki = %.3f\n", ki);
    printf("   Deadband = $\\pm%d$ RPM\n", rpm_deadband);
    printf("   Max adjustment = $\\pm%d\\%%$ per cycle\n", max_adjustment);
    printf("\n");

    if (setup_bluetooth_server() < 0) {
        cleanup_gpio();
        return 1;
    }

    printf("\n");
    printf("🟢 SERVER READY - Waiting for Android app connection\n");
    printf("   UUID: 00001101-0000-1000-8000-00805F9B34FB\n");
    printf("   Commands: START, STOP, FORWARD, REVERSE, 0-100, TARGET:0-6500\n");
    printf("\n");

    // Continuous loop to accept new Bluetooth client connections
    while (!should_exit) {
        // accept() is blocking and waits here until a connection is made
        client_fd = accept(server_fd, (struct sockaddr *)&rem_addr, &opt);
        
        if (client_fd < 0) {
            // Break loop if exit flag is set or non-recoverable error occurs
            if (should_exit) break;
            if (errno == EINTR) continue; // Signal interruption, safely retry
            perror("⚠️  Accept failed");
            sleep(1);
            continue;
        }

        handle_client_connection(client_fd); // Handles the entire client session
        
        // Post-disconnect state reset
        printf("   Resetting motor after disconnect...\n");
        fflush(stdout);
        motor_stop();
        motor.automatic_mode = 0;
        motor.target_rpm = 0;
    }

    printf("\n🧹 Cleaning up...\n");
    fflush(stdout);
    if (client_fd >= 0) close(client_fd);
    if (server_fd >= 0) close(server_fd);
    motor_stop();
    cleanup_gpio();
    
    printf("👋 Goodbye!\n\n");
    fflush(stdout);
    return 0;
}