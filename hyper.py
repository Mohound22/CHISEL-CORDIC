import matplotlib.pyplot as plt
import numpy as np
import math

# --- Hyperbolic CORDIC Configuration ---
NUM_DISTINCT_ITERATIONS_FOR_TABLE = 35

class CordicHyperbolicRotator:
    def __init__(self, target_hyperbolic_angle, prescale_for_unit_output=True):
        self.target_hyperbolic_angle = target_hyperbolic_angle
        self.prescaled_for_unit_output = prescale_for_unit_output

        self.iteration_sequence_1_indexed = []
        for i_val in range(1, NUM_DISTINCT_ITERATIONS_FOR_TABLE + 1):
            self.iteration_sequence_1_indexed.append(i_val)
            if (i_val % 3 == 1) and i_val >= 4:
                self.iteration_sequence_1_indexed.append(i_val)

        self.num_total_cordic_steps = len(self.iteration_sequence_1_indexed)
        self.atanh_table = [math.atanh(2.0**(-i_val)) for i_val in range(1, NUM_DISTINCT_ITERATIONS_FOR_TABLE + 1)]

        self.gain = 1.0
        for i_val_in_seq in self.iteration_sequence_1_indexed:
            self.gain *= math.sqrt(1.0 - (2.0**(-2 * i_val_in_seq)))
        self.inv_gain = 1.0 / self.gain

        if self.prescaled_for_unit_output:
            self.x_current = self.inv_gain
            self.y_current = 0.0
            self.initial_x_desc = f"{self.inv_gain:.5f} (1/K_h)"
        else:
            self.x_current = 1.0
            self.y_current = 0.0
            self.initial_x_desc = "1.00000"

        self.angle_accumulated_rad = 0.0
        self.current_step_index = 0
        self.d_i = 0

        self.max_reachable_angle = 0
        for i_val_in_seq in self.iteration_sequence_1_indexed:
            self.max_reachable_angle += self.atanh_table[i_val_in_seq - 1]

        print(f"Hyperbolic CORDIC Rotator Initialized:")
        print(f"  Target Hyperbolic Angle: {self.target_hyperbolic_angle:.4f} rad")
        print(f"  Total CORDIC steps in sequence: {self.num_total_cordic_steps}")
        print(f"  Max reachable angle with this sequence: +/- {self.max_reachable_angle:.4f} rad (~{math.degrees(self.max_reachable_angle):.2f}°)")
        if abs(self.target_hyperbolic_angle) > self.max_reachable_angle:
            print("  WARNING: Target angle may be outside the convergence range for this number of iterations.")
        print(f"  Initial vector (1,0) prescaled by 1/K_h: {'Yes' if self.prescaled_for_unit_output else 'No'}")
        print(f"  K_h (Gain): {self.gain:.6f}, 1/K_h: {self.inv_gain:.6f}")
        print(f"  Initial CORDIC (x, y): ({self.x_current:.5f}, {self.y_current:.5f})")


    def step(self):
        if self.current_step_index >= self.num_total_cordic_steps:
            print("CORDIC: Max steps in sequence reached.")
            return False

        i_val_for_step = self.iteration_sequence_1_indexed[self.current_step_index]
        angle_difference = self.target_hyperbolic_angle - self.angle_accumulated_rad
        self.d_i = 1 if angle_difference > 0 else -1
        if abs(angle_difference) < 1e-9 and self.current_step_index > 0 :
             self.d_i = 0

        x_prev = self.x_current
        y_prev = self.y_current
        pow2_i = 2.0**(-i_val_for_step)

        self.x_current = x_prev + self.d_i * y_prev * pow2_i
        self.y_current = y_prev + self.d_i * x_prev * pow2_i
        self.angle_accumulated_rad += self.d_i * self.atanh_table[i_val_for_step - 1]
        self.current_step_index += 1
        return True

    def get_state_info_str(self):
        prescale_info = "Input (1/K_h, 0)" if self.prescaled_for_unit_output else "Input (1.0, 0)"
        completed_step_idx = self.current_step_index -1
        i_val_str, atanh_val_str, d_val_str = "N/A", "N/A", "-"

        if completed_step_idx >= 0:
            i_val_of_last_step = self.iteration_sequence_1_indexed[completed_step_idx]
            i_val_str = str(i_val_of_last_step)
            atanh_val_for_step = self.atanh_table[i_val_of_last_step - 1]
            atanh_val_str = f"{self.d_i * atanh_val_for_step:.4f} rad"
            d_val_str = str(self.d_i)
        current_x_sq_minus_y_sq = self.x_current**2 - self.y_current**2
        return (f"Step: {self.current_step_index}/{self.num_total_cordic_steps} ({prescale_info})\n"
                f"Iter 'i' (for 2^-i): {i_val_str}\n"
                f"d_{completed_step_idx if completed_step_idx >=0 else 'init'}: {d_val_str}\n"
                f"Angle step (d*atanh(2^-i)): {atanh_val_str}\n"
                f"Accum. Hyp. Angle: {self.angle_accumulated_rad:.4f} rad\n"
                f"Target Hyp. Angle: {self.target_hyperbolic_angle:.4f} rad\n"
                f"Residual Angle: {self.target_hyperbolic_angle - self.angle_accumulated_rad:.4f} rad\n"
                f"x² - y²: {current_x_sq_minus_y_sq:.5f} (K_h² ≈ {self.gain**2:.5f})")

# --- Matplotlib Setup ---
fig, ax = plt.subplots(figsize=(12, 9)) # Adjusted size slightly
cordic_rotator = None
line_cordic_vector = None
text_info_display = None
text_coords_display = None
target_hyperbola_line_pos = None
# target_hyperbola_line_neg = None # Not needed for right-side view
asymptote_1 = None
asymptote_2 = None
ideal_target_point_plot = None


def setup_plot(target_hyperbolic_angle_for_plot):
    global line_cordic_vector, text_info_display, text_coords_display, ax, fig, cordic_rotator
    global target_hyperbola_line_pos, asymptote_1, asymptote_2, ideal_target_point_plot # Removed target_hyperbola_line_neg

    ax.clear()
    ax.set_aspect('equal', adjustable='box')

    plot_limit_y_base = 1.5 # Base y-limit for better initial view
    plot_limit_x_max_base = 1.5 # Base x-max limit

    # Dynamically adjust plot limits based on target angle and prescaling
    # cosh grows very fast, sinh slightly slower.
    # Target x = S * cosh(u), Target y = S * sinh(u), where S is 1 or K_h
    scale_factor = 1.0 if cordic_rotator.prescaled_for_unit_output else cordic_rotator.gain
    
    # Calculate expected max x and y based on target hyperbolic angle
    # Ensure target_hyperbolic_angle is not excessively large for cosh/sinh, as CORDIC won't converge there anyway
    safe_target_angle = min(abs(target_hyperbolic_angle_for_plot), cordic_rotator.max_reachable_angle + 0.2)

    expected_max_x = scale_factor * math.cosh(safe_target_angle)
    expected_max_y = scale_factor * abs(math.sinh(safe_target_angle))

    plot_limit_y = max(plot_limit_y_base, 1.2 * expected_max_y)
    plot_limit_x_max = max(plot_limit_x_max_base, 1.2 * expected_max_x)
    
    # Cap plot limits
    plot_limit_y = min(plot_limit_y, 7.0)
    plot_limit_x_max = min(plot_limit_x_max, 7.0)


    # --- KEY CHANGE HERE for Right-Side View ---
    ax.set_xlim([-0.05, plot_limit_x_max]) # Start slightly left of y-axis to show axis line
    ax.set_ylim([-plot_limit_y, plot_limit_y])
    # --- END KEY CHANGE ---

    ax.grid(True, linestyle='--', alpha=0.7)
    ax.axhline(0, color='black', lw=0.5)
    ax.axvline(0, color='black', lw=0.5)
    ax.set_xlabel("X-axis")
    ax.set_ylabel("Y-axis")
    ax.set_title(f"Hyperbolic CORDIC (Right-Side View, Target: {target_hyperbolic_angle_for_plot:.3f} rad). Press SPACE.", fontsize=12)

    x_vals_asymp_right = np.array([0, plot_limit_x_max])
    asymptote_1, = ax.plot(x_vals_asymp_right, x_vals_asymp_right, 'k:', alpha=0.5, label='y=x / y=-x Asymptotes')
    asymptote_2, = ax.plot(x_vals_asymp_right, -x_vals_asymp_right, 'k:', alpha=0.5)

    C_ref = 1.0 if cordic_rotator.prescaled_for_unit_output else cordic_rotator.gain**2
    
    if math.sqrt(C_ref) > 1e-6: # Ensure C_ref is positive and not too small
        # Determine t_hyper range to fit within plot_limit_y and plot_limit_x_max
        max_abs_y_on_plot = plot_limit_y
        max_x_on_plot = plot_limit_x_max
        
        # t corresponding to max_abs_y_on_plot: |sinh(t_y)| = max_abs_y_on_plot / sqrt(C_ref)
        t_for_y_limit = math.asinh(max_abs_y_on_plot / math.sqrt(C_ref))
        
        # t corresponding to max_x_on_plot: cosh(t_x) = max_x_on_plot / sqrt(C_ref)
        # Ensure argument to acosh is >= 1
        arg_acosh = max_x_on_plot / math.sqrt(C_ref)
        t_for_x_limit = math.acosh(arg_acosh) if arg_acosh >= 1.0 else t_for_y_limit # Fallback if x_limit is inside hyperbola vertex
        
        effective_t_limit_hyper = min(t_for_y_limit, t_for_x_limit)
        effective_t_limit_hyper = max(effective_t_limit_hyper, 0.2) # Minimum range for plotting
    else: # C_ref is too small or zero, default t_hyper
        effective_t_limit_hyper = 1.5 
        
    t_hyper = np.linspace(-effective_t_limit_hyper, effective_t_limit_hyper, 200)
    x_h = math.sqrt(C_ref) * np.cosh(t_hyper)
    y_h = math.sqrt(C_ref) * np.sinh(t_hyper)
    
    # Filter points to ensure they are on the right side of y-axis (x_h >=0)
    # cosh is always >=1, so sqrt(C_ref)*cosh(t_hyper) is always >= sqrt(C_ref) > 0 (for C_ref > 0)
    target_hyperbola_line_pos, = ax.plot(x_h, y_h, color='blue', linewidth=1, alpha=0.6, label=f'Target Hyperbola (x²-y²={C_ref:.3f})')

    x_ideal = scale_factor * math.cosh(target_hyperbolic_angle_for_plot)
    y_ideal = scale_factor * math.sinh(target_hyperbolic_angle_for_plot)
    
    if x_ideal >= -0.05: # Check if ideal point is on the visible part
        ideal_target_point_plot, = ax.plot(x_ideal, y_ideal, 'g*', markersize=10, label='Ideal Target Point')
    else:
        ideal_target_point_plot = None

    line_cordic_vector, = ax.plot([0, cordic_rotator.x_current], [0, cordic_rotator.y_current], 'r-', lw=2.5, marker='o',
                                  label='CORDIC Vector')

    prescale_msg = "Yes (target x²-y² ≈ 1)" if cordic_rotator.prescaled_for_unit_output else f"No (target x²-y² ≈ K_h²={cordic_rotator.gain**2:.3f})"
    initial_info_content = (f"Press SPACE to step\n"
                            f"Total Steps: {cordic_rotator.num_total_cordic_steps}\n"
                            f"Input (1,0) prescaled by 1/K_h: {prescale_msg}\n"
                            f"Initial CORDIC (x,y): ({cordic_rotator.initial_x_desc}, 0.00000)\n"
                            f"Accum. Hyp. Angle: 0.0000 rad")
    text_info_display = fig.text(0.02, 0.98, initial_info_content, fontsize=9, va='top', ha='left',
                                 bbox=dict(boxstyle='round,pad=0.3', fc='lightyellow', alpha=0.85))

    coords_content = f"x = {cordic_rotator.x_current:.5f}\ny = {cordic_rotator.y_current:.5f}"
    text_coords_display = fig.text(0.98, 0.02, coords_content, transform=fig.transFigure,
                                   fontsize=16, color='darkblue', ha='right', va='bottom',
                                   bbox=dict(boxstyle='round,pad=0.4', fc='lightblue', alpha=0.9))

    ax.legend(loc='upper right', fontsize=8)
    fig.canvas.draw_idle()

def update_plot():
    global line_cordic_vector, text_info_display, text_coords_display, cordic_rotator, ax
    if cordic_rotator is None: return

    line_cordic_vector.set_data([0, cordic_rotator.x_current], [0, cordic_rotator.y_current])
    text_info_display.set_text(cordic_rotator.get_state_info_str())
    text_coords_display.set_text(f"x = {cordic_rotator.x_current:.5f}\ny = {cordic_rotator.y_current:.5f}")

    if cordic_rotator.current_step_index >= cordic_rotator.num_total_cordic_steps:
        final_xs_min_ys = cordic_rotator.x_current**2 - cordic_rotator.y_current**2
        ax.set_title(f"Hyperbolic CORDIC Done (Target: {cordic_rotator.target_hyperbolic_angle:.3f} rad)\n"
                     f"Final Angle: {cordic_rotator.angle_accumulated_rad:.4f} rad, Final x²-y²: {final_xs_min_ys:.4f}", fontsize=12)
    fig.canvas.draw_idle()

def on_key_press(event):
    global cordic_rotator
    if event.key == ' ' and cordic_rotator:
        print(f"\n--- Spacebar: Stepping Hyperbolic CORDIC (Step {cordic_rotator.current_step_index+1}/{cordic_rotator.num_total_cordic_steps}) ---")
        if cordic_rotator.step():
            completed_step_idx = cordic_rotator.current_step_index -1
            i_val_of_last_step = cordic_rotator.iteration_sequence_1_indexed[completed_step_idx]
            atanh_val_for_step = cordic_rotator.atanh_table[i_val_of_last_step - 1]
            print(f"  Iter 'i' was: {i_val_of_last_step}")
            print(f"  d_{completed_step_idx} = {cordic_rotator.d_i}")
            print(f"  Angle this step = {cordic_rotator.d_i * atanh_val_for_step:.5f} rad")
            print(f"  New CORDIC (x, y) = ({cordic_rotator.x_current:.5f}, {cordic_rotator.y_current:.5f})")
            print(f"  New Accum. Hyp. Angle = {cordic_rotator.angle_accumulated_rad:.5f} rad")
            update_plot()
        else:
            print("Hyperbolic CORDIC iterations complete or error.")
            update_plot()

def main():
    global cordic_rotator
    max_angle_approx = CordicHyperbolicRotator(0).max_reachable_angle # Get from a dummy instance

    while True:
        try:
            angle_str = input(f"Enter target hyperbolic angle in radians (e.g., 0.5, -0.8).\nPractical range approx +/-{max_angle_approx:.3f} rad: ")
            target_hyperbolic_angle = float(angle_str)
            if abs(target_hyperbolic_angle) > max_angle_approx + 0.1:
                 print(f"Warning: Angle {target_hyperbolic_angle:.3f} rad is significantly outside the typical convergence range of ~+/-{max_angle_approx:.3f} rad.")
                 if input("Continue anyway? (y/n): ").lower() != 'y':
                     continue
            break
        except ValueError:
            print("Invalid input. Please enter a numeric value.")

    while True:
        prescale_choice = input("Prescale initial vector (1,0) by 1/K_h for target x²-y² ≈ 1? (yes/no) [yes]: ").strip().lower()
        if prescale_choice in ['yes', 'y', '']: use_prescaling = True; break
        elif prescale_choice in ['no', 'n']: use_prescaling = False; break
        else: print("Invalid choice. Please enter 'yes' or 'no'.")

    cordic_rotator = CordicHyperbolicRotator(target_hyperbolic_angle, use_prescaling)
    setup_plot(target_hyperbolic_angle)

    fig.canvas.mpl_connect('key_press_event', on_key_press)
    print(f"\nPlot window active. Press SPACEBAR to advance Hyperbolic CORDIC steps (max {cordic_rotator.num_total_cordic_steps} steps).")
    plt.show()

if __name__ == "__main__":
    main()
