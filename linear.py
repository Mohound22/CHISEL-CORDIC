import matplotlib.pyplot as plt
import numpy as np
import math

# --- CORDIC Configuration ---
NUM_CORDIC_ITERATIONS = 16  # Number of iterations for Linear CORDIC precision
                            # For multiplication, this determines how many bits of the multiplier are processed.

class CordicLinearOperator:
    def __init__(self, x_input_val, target_multiplier_val, num_iterations_val):
        self.x_input = x_input_val  # The value to be multiplied (multiplicand)
        self.target_multiplier = target_multiplier_val # The multiplier
        self.num_iterations = num_iterations_val

        # Initial state for Linear CORDIC multiplication mode
        self.x_current = self.x_input  # x is constant in this mode
        self.y_current = 0.0           # Accumulator for the product y_final = x_input * target_multiplier
        self.z_current = self.target_multiplier # Residual multiplier, z_final should approach 0

        self.current_iteration_num = 0 # Number of iterations completed
        self.d_i_last = 0 # Direction of the last operation

        print(f"Linear CORDIC Operator Initialized (Multiplication):")
        print(f"  x_input (Multiplicand): {self.x_input:.4f}")
        print(f"  Target Multiplier: {self.target_multiplier:.4f}")
        print(f"  Expected Product: {self.x_input * self.target_multiplier:.4f}")
        print(f"  Iterations: {self.num_iterations}")

    def step(self):
        if self.current_iteration_num >= self.num_iterations:
            print("CORDIC: Max iterations reached.")
            return False # No more steps

        # Determine direction d_i based on the sign of the residual multiplier z_current
        # If z is positive, we want to add x_current * 2^-i to y_current, so d_i = +1.
        # This means we've accounted for the +2^-i part of the multiplier.
        # If z is negative, we want to subtract x_current * 2^-i from y_current (by adding d_i * x * 2^-i), so d_i = -1.
        # This means we've overshot and need to correct.
        if self.z_current > 1e-9: # Add tolerance for floating point comparison
            self.d_i_last = 1
        elif self.z_current < -1e-9:
            self.d_i_last = -1
        else:
            self.d_i_last = 0 # z_current is effectively zero

        # Elementary shift value for this iteration
        # Iterations are typically 0-indexed for 2^-i in linear CORDIC
        shift_val_pow2_i = 2.0**(-self.current_iteration_num)

        # Linear CORDIC multiplication equations:
        # x_new = x_old (x_current remains self.x_input)
        self.y_current = self.y_current + self.d_i_last * self.x_current * shift_val_pow2_i
        self.z_current = self.z_current - self.d_i_last * shift_val_pow2_i

        self.current_iteration_num += 1
        return True # Step was successful

    def get_state_info_str(self):
        # Iteration index that just completed (0-indexed)
        completed_iter_idx = self.current_iteration_num - 1
        
        shift_val_str = "N/A"
        term_added_to_y_str = "N/A"
        d_val_str = "-"

        if completed_iter_idx >= 0:
            current_shift = 2.0**(-completed_iter_idx)
            shift_val_str = f"{current_shift:.5f} (2^-{completed_iter_idx})"
            term_added_to_y_str = f"{self.d_i_last * self.x_current * current_shift:.5f}"
            d_val_str = str(self.d_i_last)


        return (f"Iteration: {self.current_iteration_num}/{self.num_iterations}\n"
                f"d_{completed_iter_idx if completed_iter_idx >=0 else 'init'}: {d_val_str}\n"
                f"Shift (2^-i): {shift_val_str}\n"
                f"Term added to y (d*x*2^-i): {term_added_to_y_str}\n"
                f"Current y (product): {self.y_current:.5f}\n"
                f"Current z (residual mult.): {self.z_current:.5f}\n"
                f"Target Product: {self.x_input * self.target_multiplier:.5f}")

# --- Matplotlib Setup ---
fig, ax = plt.subplots(figsize=(12, 8))
cordic_operator = None # Will be instance of CordicLinearOperator

# Lines for plotting progression
line_y_progression = None
line_z_progression = None
line_target_product = None

# History for plotting lines
iteration_history = []
y_history = []
z_history = []

# Text displays
text_info_display = None
text_current_vals_display = None # For y and z values

def setup_plot(x_val_for_plot, target_mult_for_plot, num_total_iterations):
    global line_y_progression, line_z_progression, line_target_product, ax, fig, cordic_operator
    global iteration_history, y_history, z_history
    global text_info_display, text_current_vals_display

    # Clear history for a new run
    iteration_history.clear()
    y_history.clear()
    z_history.clear()

    ax.clear() # Clear previous plot if any
    ax.set_xlabel("Iteration Number")
    ax.set_ylabel("Value")
    ax.set_title(f"Linear CORDIC (Multiplication: {x_val_for_plot:.3f} * {target_mult_for_plot:.3f}). Press SPACE.", fontsize=13)
    ax.grid(True, linestyle='--', alpha=0.7)

    # X-axis limits for iterations (0 to N)
    ax.set_xlim([-0.5, num_total_iterations + 0.5])

    # Estimate Y-axis limits
    expected_product = x_val_for_plot * target_mult_for_plot
    # Consider initial y (0), initial z (target_multiplier), and final y (expected_product)
    min_val_seen = min(0, target_mult_for_plot, expected_product)
    max_val_seen = max(0, target_mult_for_plot, expected_product)
    
    padding = abs(max_val_seen - min_val_seen) * 0.15 + 0.5 # Add some padding
    ax.set_ylim([min_val_seen - padding, max_val_seen + padding])

    # Plot target product line (horizontal)
    line_target_product, = ax.plot([0, num_total_iterations], [expected_product, expected_product],
                                   'g--', lw=1.5, label=f'Target Product ({expected_product:.4f})')

    # Add initial state (iteration 0, before first step) to history
    iteration_history.append(0)
    y_history.append(cordic_operator.y_current) # Should be 0
    z_history.append(cordic_operator.z_current) # Should be target_multiplier

    # Initialize progression lines
    line_y_progression, = ax.plot(iteration_history, y_history, 'r-o', lw=2, markersize=4, label='y_current (Accumulated Product)')
    line_z_progression, = ax.plot(iteration_history, z_history, 'b-s', lw=1.5, markersize=3, label='z_current (Residual Multiplier)')

    # Text display for CORDIC state info
    initial_info_content = cordic_operator.get_state_info_str() # Get initial state before any step
    text_info_display = fig.text(0.015, 0.985, initial_info_content, fontsize=9, va='top', ha='left',
                                 bbox=dict(boxstyle='round,pad=0.3', fc='cornsilk', alpha=0.85))

    # Text display for current y and z values (larger)
    vals_content = f"y = {cordic_operator.y_current:.5f}\nz = {cordic_operator.z_current:.5f}"
    text_current_vals_display = fig.text(0.985, 0.015, vals_content, transform=fig.transFigure,
                                   fontsize=16, color='darkgreen', ha='right', va='bottom',
                                   bbox=dict(boxstyle='round,pad=0.4', fc='honeydew', alpha=0.9))

    ax.legend(loc='best', fontsize=9)
    fig.canvas.draw_idle()

def update_plot():
    global line_y_progression, line_z_progression, text_info_display, text_current_vals_display
    global cordic_operator, ax, iteration_history, y_history, z_history

    if cordic_operator is None: return

    # Add current state (after the step) to history
    # cordic_operator.current_iteration_num is the count of completed iterations
    iteration_history.append(cordic_operator.current_iteration_num)
    y_history.append(cordic_operator.y_current)
    z_history.append(cordic_operator.z_current)

    # Update line data
    line_y_progression.set_data(iteration_history, y_history)
    line_z_progression.set_data(iteration_history, z_history)

    # Update info text and current values text
    text_info_display.set_text(cordic_operator.get_state_info_str())
    text_current_vals_display.set_text(f"y = {cordic_operator.y_current:.5f}\nz = {cordic_operator.z_current:.5f}")

    if cordic_operator.current_iteration_num >= cordic_operator.num_iterations:
        ax.set_title(f"Linear CORDIC Done. Final y (Product): {cordic_operator.y_current:.5f}", fontsize=13)

    # Optional: Adjust y-limits dynamically if values go out of initial estimate
    # current_min_y = min(ax.get_ylim()[0], min(y_history), min(z_history))
    # current_max_y = max(ax.get_ylim()[1], max(y_history), max(z_history))
    # if current_min_y < ax.get_ylim()[0] - 0.1 or current_max_y > ax.get_ylim()[1] + 0.1:
    #     ax.set_ylim([current_min_y - abs(current_min_y*0.1)-0.1, current_max_y + abs(current_max_y*0.1)+0.1])

    fig.canvas.draw_idle() # Request a redraw

def on_key_press(event):
    global cordic_operator
    if event.key == ' ' and cordic_operator:
        # Iteration number about to be performed is current_iteration_num
        print(f"\n--- Spacebar: Stepping Linear CORDIC (Iter {cordic_operator.current_iteration_num}) ---")
        if cordic_operator.step(): # step() increments current_iteration_num internally
            completed_iter_idx = cordic_operator.current_iteration_num -1 # The iteration that just finished
            shift_val = 2.0**(-completed_iter_idx)
            
            print(f"  d_{completed_iter_idx} = {cordic_operator.d_i_last}")
            print(f"  Shift value (2^-{completed_iter_idx}) = {shift_val:.5f}")
            print(f"  Term added/subtracted from y = {cordic_operator.d_i_last * cordic_operator.x_current * shift_val:.5f}")
            print(f"  New y_current = {cordic_operator.y_current:.5f}")
            print(f"  New z_current = {cordic_operator.z_current:.5f}")
            update_plot()
        else:
            print("Linear CORDIC iterations complete or error in stepping.")
            update_plot() # Update title to 'Done'

def main():
    global cordic_operator

    while True:
        try:
            x_input_str = input(f"Enter the value to be multiplied (x_input, e.g., 1.0, 0.75, -2.5): ")
            x_val = float(x_input_str)
            break
        except ValueError:
            print("Invalid input. Please enter a numeric value for x_input.")

    while True:
        try:
            target_mult_str = input(f"Enter the target multiplier (e.g., 0.625, 1.5, -0.9): ")
            target_mult_val = float(target_mult_str)
            break
        except ValueError:
            print("Invalid input. Please enter a numeric value for the target multiplier.")

    # No prescaling choice for linear multiplication CORDIC as K=1
    # NUM_CORDIC_ITERATIONS defined at the top controls the precision

    cordic_operator = CordicLinearOperator(x_val, target_mult_val, NUM_CORDIC_ITERATIONS)
    setup_plot(x_val, target_mult_val, NUM_CORDIC_ITERATIONS)

    fig.canvas.mpl_connect('key_press_event', on_key_press)
    print("\nPlot window active. Press SPACEBAR to advance Linear CORDIC steps.")
    plt.show()

if __name__ == "__main__":
    main()
