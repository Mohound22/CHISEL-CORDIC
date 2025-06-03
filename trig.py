import matplotlib.pyplot as plt
import numpy as np
import math

# --- CORDIC Configuration ---
NUM_CORDIC_ITERATIONS = 64  # Max iterations for CORDIC precision

class CordicRotator:
    def __init__(self, target_angle_degrees, num_iterations, prescale_for_unit_output=True):
        self.target_angle_rad = math.radians(target_angle_degrees)
        self.num_iterations = num_iterations # Use passed value

        # Precompute atan(2^-i) table in radians
        self.atan_table_rad = [math.atan(2.0**(-i)) for i in range(self.num_iterations)]

        # Precompute CORDIC gain K_n = product(sqrt(1 + 2^(-2i)))
        self.gain = 1.0
        for i in range(self.num_iterations):
            self.gain *= math.sqrt(1.0 + (2.0**(-2 * i)))
        self.inv_gain = 1.0 / self.gain
        self.prescaled_for_unit_output = prescale_for_unit_output

        # Initial state for CORDIC rotation mode
        if self.prescaled_for_unit_output:
            self.x_current = self.inv_gain # Start with (1/K, 0)
            self.y_current = 0.0
            self.initial_x_desc = f"{self.inv_gain:.5f} (1/K)"
        else:
            self.x_current = 1.0 # Start with (1,0) - output will be scaled by K
            self.y_current = 0.0
            self.initial_x_desc = "1.00000"

        self.angle_accumulated_rad = 0.0  # Accumulated angle from CORDIC steps
        self.current_iteration = 0
        self.d_i = 0 # Direction of last rotation step

        print(f"CORDIC Rotator Initialized:")
        print(f"  Target Angle: {target_angle_degrees:.2f}° ({self.target_angle_rad:.4f} rad)")
        print(f"  Iterations: {self.num_iterations}")
        print(f"  Initial vector (1,0) prescaled by 1/K: {'Yes' if self.prescaled_for_unit_output else 'No'}")
        print(f"  K (Gain): {self.gain:.6f}, K_inv: {self.inv_gain:.6f}")
        print(f"  Initial CORDIC (x, y): ({self.x_current:.5f}, {self.y_current:.5f})")

    def step(self):
        if self.current_iteration >= self.num_iterations:
            print("CORDIC: Max iterations reached.")
            return False # No more steps

        angle_difference = self.target_angle_rad - self.angle_accumulated_rad
        self.d_i = 1 if angle_difference > 0 else -1

        x_prev = self.x_current
        y_prev = self.y_current
        pow2_i = 2.0**(-self.current_iteration) # 2^-i

        self.x_current = x_prev - self.d_i * y_prev * pow2_i
        self.y_current = y_prev + self.d_i * x_prev * pow2_i
        self.angle_accumulated_rad += self.d_i * self.atan_table_rad[self.current_iteration]
        self.current_iteration += 1
        return True

    def get_state_info_str(self):
        prescale_info = "Input was (1/K, 0)" if self.prescaled_for_unit_output else "Input was (1.0, 0)"
        iter_idx = self.current_iteration -1 # Iteration that just completed
        atan_val_str = "N/A"
        if iter_idx >= 0:
            atan_val_str = f"{math.degrees(self.atan_table_rad[iter_idx]):.3f}°"

        return (f"Iter: {self.current_iteration}/{self.num_iterations} ({prescale_info})\n"
                f"d_{iter_idx if iter_idx >=0 else 'init'}: {self.d_i if self.current_iteration > 0 else '-'}\n"
                f"Angle step (d * atan(2^-{iter_idx if iter_idx >=0 else 'N/A'})): "
                f"{self.d_i * math.degrees(self.atan_table_rad[iter_idx]) if iter_idx >=0 else 'N/A'}°\n"
                f"Accum. Angle: {math.degrees(self.angle_accumulated_rad):.3f}°\n"
                f"Target Angle: {math.degrees(self.target_angle_rad):.3f}°\n"
                f"Residual Angle: {math.degrees(self.target_angle_rad - self.angle_accumulated_rad):.3f}°\n"
                f"Current Magnitude: {math.sqrt(self.x_current**2 + self.y_current**2):.4f}")

# --- Matplotlib Setup ---
fig, ax = plt.subplots(figsize=(13, 10)) # Increased figure size
cordic_rotator = None
line_cordic_vector = None
text_info_display = None
text_coords_display = None # For large X, Y
target_line = None

def setup_plot(target_angle_degrees_for_plot):
    global line_cordic_vector, text_info_display, text_coords_display, target_line, ax, fig, cordic_rotator

    ax.clear()
    ax.set_aspect('equal', adjustable='box')
    ax.set_xlim([-1.7, 1.7]) # Adjusted limits slightly for potentially larger vector if not prescaled
    ax.set_ylim([-1.7, 1.7])
    ax.grid(True, linestyle='--', alpha=0.7)
    ax.axhline(0, color='black', lw=0.5)
    ax.axvline(0, color='black', lw=0.5)
    ax.set_xlabel("X-axis")
    ax.set_ylabel("Y-axis")
    ax.set_title(f"CORDIC Rotation (Target: {target_angle_degrees_for_plot:.1f}°). Press SPACE.", fontsize=14)

    # Plot Unit Circle (reference for prescaled output)
    theta_circle = np.linspace(0, 2 * np.pi, 200)
    x_u_circle = np.cos(theta_circle)
    y_u_circle = np.sin(theta_circle)
    ax.plot(x_u_circle, y_u_circle, label='Unit Circle (Mag=1.0 Ref)', color='blue', linewidth=1, alpha=0.4)

    # If not prescaled, also show the K-scaled circle for reference
    if not cordic_rotator.prescaled_for_unit_output:
        x_k_circle = cordic_rotator.gain * np.cos(theta_circle)
        y_k_circle = cordic_rotator.gain * np.sin(theta_circle)
        ax.plot(x_k_circle, y_k_circle, label=f'K-Scaled Circle (Mag={cordic_rotator.gain:.3f} Ref)', color='purple', linestyle=':', linewidth=1, alpha=0.4)


    # Plot Target Angle Line
    target_rad = math.radians(target_angle_degrees_for_plot)
    # Draw target line based on whether we expect unit or K-scaled output
    target_mag = 1.0 if cordic_rotator.prescaled_for_unit_output else cordic_rotator.gain
    target_line, = ax.plot([0, target_mag * math.cos(target_rad)], [0, target_mag * math.sin(target_rad)],
                           'g--', label=f'Target Angle ({target_angle_degrees_for_plot:.1f}°)', linewidth=1.5)

    # Initialize CORDIC vector line
    line_cordic_vector, = ax.plot([0, cordic_rotator.x_current], [0, cordic_rotator.y_current], 'r-', lw=2.5, marker='o',
                                  label='CORDIC Vector')

    # Text display for CORDIC info
    prescale_msg = "Yes (output mag ≈ 1.0)" if cordic_rotator.prescaled_for_unit_output else "No (output mag ≈ K)"
    initial_info_content = (f"Press SPACE to step\n"
                            f"Max Iterations: {cordic_rotator.num_iterations}\n"
                            f"Input (1,0) prescaled by 1/K: {prescale_msg}\n"
                            f"Initial CORDIC (x,y): ({cordic_rotator.initial_x_desc}, 0.00000)\n"
                            f"Accum. Angle: 0.000°")
    text_info_display = fig.text(0.015, 0.985, initial_info_content, fontsize=9, va='top', ha='left',
                                 bbox=dict(boxstyle='round,pad=0.3', fc='wheat', alpha=0.1))

    # Text display for large X, Y coordinates
    coords_content = f"x = {cordic_rotator.x_current:.5f}\ny = {cordic_rotator.y_current:.5f}"
    text_coords_display = fig.text(0.985, 0.015, coords_content, transform=fig.transFigure,
                                   fontsize=32, color='navy', ha='right', va='bottom',
                                   bbox=dict(boxstyle='round,pad=0.4', fc='aliceblue', alpha=0.9))

    ax.legend(loc='lower left', fontsize=8)
    fig.canvas.draw_idle()

def update_plot():
    global line_cordic_vector, text_info_display, text_coords_display, cordic_rotator, ax

    if cordic_rotator is None: return

    line_cordic_vector.set_data([0, cordic_rotator.x_current], [0, cordic_rotator.y_current])
    text_info_display.set_text(cordic_rotator.get_state_info_str())
    text_coords_display.set_text(f"x = {cordic_rotator.x_current:.5f}\ny = {cordic_rotator.y_current:.5f}")

    if cordic_rotator.current_iteration >= cordic_rotator.num_iterations:
        final_mag = math.sqrt(cordic_rotator.x_current**2 + cordic_rotator.y_current**2)
        ax.set_title(f"CORDIC Done (Target: {math.degrees(cordic_rotator.target_angle_rad):.1f}°) "
                     f"Final Angle: {math.degrees(cordic_rotator.angle_accumulated_rad):.3f}°, Mag: {final_mag:.4f}", fontsize=14)

    fig.canvas.draw_idle()

def on_key_press(event):
    global cordic_rotator
    if event.key == ' ' and cordic_rotator:
        print(f"\n--- Spacebar: Stepping CORDIC (Iter {cordic_rotator.current_iteration} -> {cordic_rotator.current_iteration+1}) ---")
        if cordic_rotator.step():
            iter_idx = cordic_rotator.current_iteration -1
            print(f"  d_{iter_idx} = {cordic_rotator.d_i}")
            print(f"  Angle this step = {cordic_rotator.d_i * math.degrees(cordic_rotator.atan_table_rad[iter_idx]):.4f}°")
            print(f"  New CORDIC (x, y) = ({cordic_rotator.x_current:.5f}, {cordic_rotator.y_current:.5f})")
            print(f"  New Accum. Angle = {math.degrees(cordic_rotator.angle_accumulated_rad):.4f}°")
            update_plot()
        else:
            print("CORDIC iterations complete.")
            update_plot() # Update title to 'Done'

def main():
    global cordic_rotator

    while True:
        try:
            angle_degrees_str = input(f"Enter target angle in degrees (e.g., 45, -30, 270): ")
            target_angle_degrees = float(angle_degrees_str)
            break
        except ValueError:
            print("Invalid input. Please enter a numeric value.")

    while True:
        prescale_choice = input("Prescale initial vector (1,0) by 1/K for unit length output? (yes/no) [yes]: ").strip().lower()
        if prescale_choice in ['yes', 'y', '']:
            use_prescaling = True
            break
        elif prescale_choice in ['no', 'n']:
            use_prescaling = False
            break
        else:
            print("Invalid choice. Please enter 'yes' or 'no'.")

    cordic_rotator = CordicRotator(target_angle_degrees, NUM_CORDIC_ITERATIONS, use_prescaling)
    setup_plot(target_angle_degrees)

    fig.canvas.mpl_connect('key_press_event', on_key_press)
    print("\nPlot window active. Press SPACEBAR to advance CORDIC steps.")
    plt.show()

if __name__ == "__main__":
    main()
