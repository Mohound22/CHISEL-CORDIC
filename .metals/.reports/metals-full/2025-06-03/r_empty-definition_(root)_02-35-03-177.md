error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICcore.scala:
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICcore.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2255
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICcore.scala
text:
```scala
package CORDIC

import chisel3._
import chisel3.util._
import scala.math.{pow, sqrt, log, atan}

// Companion Object for enums, constants, and helper functions
object CORDICCore {
  // All possible operations for the CORDIC core
  object CORDICModeAll extends ChiselEnum {
    val TrigSinCos, TrigArctanMagnitude, // Trigonometric
        LinearMultiply, LinearDivide,     // Linear (conditionally included)
        HyperSinhCosh, HyperAtanhMagnitude = Value // Hyperbolic (conditionally included)
  }

  /* Converts a Double to a BigInt representing a fixed-point number */
  def doubleToFixed(x: Double, fractionalBits: Int, width: Int): BigInt = {
    val scaled = BigDecimal(x) * BigDecimal(BigInt(1) << fractionalBits)
    val rounded = scaled.setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt
    val maxVal = (BigInt(1) << (width - 1)) - 1
    val minVal = -(BigInt(1) << (width - 1))
    rounded.max(minVal).min(maxVal)
  }

  // Clamp helper function (from linearCHISEL, generally useful)
  def clamp(value: SInt, targetWidth: Int): SInt = {
    val maxVal_target = ((BigInt(1) << (targetWidth - 1)) - 1).S(targetWidth.W)
    val minVal_target = (-(BigInt(1) << (targetWidth - 1))).S(targetWidth.W)

    val value_w = value.getWidth
    val max_val_extended = maxVal_target.pad(value_w)
    val min_val_extended = minVal_target.pad(value_w)

    Mux(value > max_val_extended, maxVal_target,
      Mux(value < min_val_extended, minVal_target,
        value(targetWidth - 1, 0).asSInt))
  }

  // --- Trigonometric constants and helpers ---
  val TRIG_CORDIC_K_DBL: Double = 0.6072529350088813 // Gain for XY, Z unchanged
  def getAtanLUT(fractionalBits: Int, width: Int, numEntries: Int): Seq[SInt] = {
    (0 until numEntries).map { i =>
      val angle_rad = scala.math.atan(scala.math.pow(2.0, -i))
      doubleToFixed(angle_rad, fractionalBits, width).S(width.W)
    }
  }

  // --- Linear constants ---
  val CORDIC_LINEAR_RANGE_LIMIT_R: Double = 1.99

  // --- Hyperbolic constants and helpers ---
  // Generates sequence of shift exponents for hyperbolic CORDIC
  def getHyperbolicShiftExponents(cycleCount: Int): Seq[Int] = {
    var exponents = scala.collection.mutable.ArrayBuffer[Int]()
    var i = 0
    var k_exp = 1 // Exponent v@@alue starts at 1
    var iteration_idx = 0 // Iteration number for repetition logic
    var nextRepeatAtIter = 3 // Iteration k=4 (0-indexed: 3) is the first repeat target (1,2,3,4,4,...)

    while (i < cycleCount) {
      exponents += k_exp
      i += 1
      
      if (iteration_idx == nextRepeatAtIter && i < cycleCount) { // Check iteration_idx for repeat logic
        exponents += k_exp // Repeat the current exponent
        i += 1
        nextRepeatAtIter = nextRepeatAtIter * 3 + 4 // Rule k_i+1 = 3*k_i + 1 for indices, so for values related to 4, 13, 40
                                                    // The original formula seems to be about k values 4, 13, 40
                                                    // The sequence of k values is 1,2,3,4,4,5,6,7,8,9,10,11,12,13,13...
                                                    // If k_exp = 4, next repeat is at k_exp = 13.
                                                    // Original logic from hyperCHISEL: nextRepeat = nextRepeat * 3 + 1, init nextRepeat = 4. This was for 'k' (exponent value)
                                                    // Let's stick to the provided logic for sequence generation.
                                                    // The provided hyperCHISEL used 'k' for exponent value, and 'i' for cycle count.
                                                    // The 'nextRepeat' logic was based on 'k'.
                                                    // if (k == nextRepeat && i < cycleCount)
                                                    // My k_exp is the exponent value.
      }
      // Update k_exp and iteration_idx for next step
      if (i < cycleCount && iteration_idx == nextRepeatAtIter) {
         // Just repeated, k_exp stays same for the next distinct value (e.g. after 4,4 comes 5)
         // No, k_exp should increment unless it's a repeat of the *previous* distinct k_exp
      }

      // This logic needs to be exactly as in hyperCHISEL for the exponents
      // Re-implementing carefully from hyperCHISEL's getHyperbolicShiftExponents:
      // Resetting exponents for clarity with original hyperCHISEL logic
      if (i == exponents.length && iteration_idx == nextRepeatAtIter) { // This condition means we just added the k_exp that is a repeat trigger
          // We already added k_exp. If we need to repeat and space allows, we add it again.
          // The loop structure was: add k, i++, if k == nextRepeat && i < cycleCount, add k, i++, update nextRepeat. k++.
          // This is tricky. Let's trace:
          // cycleCount = 5.
          // i=0, k=1, nextRepeat=4. exp=[],
          // iter 1: exp=[1], i=1. k becomes 2.
          // iter 2: exp=[1,2], i=2. k becomes 3.
          // iter 3: exp=[1,2,3], i=3. k becomes 4.
          // iter 4: exp=[1,2,3,4], i=4. k is 4, k==nextRepeat. i < cycleCount (4<5).
          //         exp=[1,2,3,4,4], i=5. nextRepeat = 4*3+1 = 13. k becomes 5.
          // loop terminates as i == cycleCount. Correct for N=5: [1,2,3,4,4]

          // The logic in hyperCHISEL was simpler:
          // while (i < cycleCount) { exponents += k; i += 1; 
          //   if (k == nextRepeat && i < cycleCount) { exponents += k; i += 1; nextRepeat = nextRepeat * 3 + 1; }
          //   k += 1; }
          // This is what I should replicate.
      }
      iteration_idx +=1
      // k_exp should increment if not a repeat of the just-added value.
      // The original hyperCHISEL increments 'k' (the exponent value) after the potential repeat.
      if (!(iteration_idx > 0 && iteration_idx -1 == nextRepeatAtIter && exponents(exponents.length-1) == exponents(exponents.length-2))) {
         // if not just repeated the same value
         //This is getting too complex. Revert to the simpler loop from hyperCHISEL.scala
      }
       // k_exp += 1 // This was part of original, moved inside the simplified loop below
    }
    // Corrected implementation from hyperCHISEL.scala
    exponents.clear() // Reset for correct logic
    var current_exp_val = 1
    var count = 0
    var next_repeat_val = 4
    while(count < cycleCount){
        exponents += current_exp_val
        count += 1
        if(current_exp_val == next_repeat_val && count < cycleCount){
            exponents += current_exp_val
            count += 1
            next_repeat_val = next_repeat_val * 3 + 1
        }
        current_exp_val += 1
    }
    exponents.toSeq
  }


  def calculateHyperbolicGainFactor(shiftValues: Seq[Int]): Double = {
    shiftValues.map { s_i => // s_i is the exponent value (1, 2, 3, 4, 4...)
      sqrt(1.0 - pow(2.0, -2.0 * s_i))
    }.product
  }

  def getAtanHyperLUT(fractionalBits: Int, width: Int, hyperShiftExponents: Seq[Int]): Seq[SInt] = {
    hyperShiftExponents.map { exponent => // exponent is 1, 2, 3, 4, 4...
      val x_atanh = pow(2.0, -exponent) // x for atanh(x)
      val angle_rad = 0.5 * log((1.0 + x_atanh) / (1.0 - x_atanh)) // atanh(x) = 0.5 * ln((1+x)/(1-x))
      doubleToFixed(angle_rad, fractionalBits, width).S(width.W)
    }
  }
}

class CORDICCore(
    val width: Int,
    val cycleCount: Int,
    val integerBits: Int = 3,
    val gainCorrection: Boolean = true, // Unified gain/magnitude correction for Trig and Hyperbolic
    val includeLinear: Boolean,
    val includeHyperbolic: Boolean
) extends Module {
  import CORDICCore._ // Import from companion object

  // Parameter Validations
  require(width > 0, "Width must be positive")
  require(cycleCount > 0, "Cycle count must be positive")
  require(integerBits >= 1, "Integer bits must be at least 1 for sign bit and potential integer part")
  val fractionalBits: Int = width - 1 - integerBits // 1 bit for sign
  require(fractionalBits > 0, s"Fractional bits must be positive. Check width ($width) vs integerBits ($integerBits). FractionalBits = $fractionalBits")

  val io = IO(new Bundle {
    // Control and Mode Input
    val mode = Flipped(Decoupled(CORDICModeAll()))

    // Data Inputs (common bus, specific inputs used based on mode)
    val inputX = Flipped(Decoupled(SInt(width.W)))     // Trig: X, Linear: A (Multiplicand/Dividend), Hyper: X
    val inputY = Flipped(Decoupled(SInt(width.W)))     // Trig: Y, Linear: B (Multiplier/Divisor),  Hyper: Y
    val inputTheta = Flipped(Decoupled(SInt(width.W))) // Trig: Theta, Hyper: Theta (Linear does not use Theta)

    // Data Outputs
    // Output 1 mapping:
    // - TrigSinCos: cos(Theta)
    // - TrigArctanMagnitude: arctan(Y/X)
    // - LinearMultiply: Product (X*Y)
    // - LinearDivide: Quotient (X/Y)
    // - HyperSinhCosh: cosh(Theta)
    // - HyperAtanhMagnitude: atanh(Y/X)
    val output1 = Decoupled(SInt(width.W))

    // Output 2 mapping:
    // - TrigSinCos: sin(Theta)
    // - TrigArctanMagnitude: Magnitude sqrt(X^2+Y^2)
    // - LinearMultiply: 0 (unused)
    // - LinearDivide: 0 (unused)
    // - HyperSinhCosh: sinh(Theta)
    // - HyperAtanhMagnitude: Magnitude sqrt(X^2-Y^2)
    val output2 = Decoupled(SInt(width.W))
  })

  // --- Internal State Machine Definition ---
  object State extends ChiselEnum {
    val sIdle, sBusy, sDone = Value
  }
  val state = RegInit(State.sIdle)

  // --- CORDIC iterative registers ---
  val x_reg = Reg(SInt(width.W))
  val y_reg = Reg(SInt(width.W))
  val z_reg = Reg(SInt(width.W))
  val iter_count = Reg(UInt(log2Ceil(cycleCount + 1).W))

  // --- Latched input mode and data registers ---
  val currentOpMode_reg = Reg(CORDICModeAll())
  val inputX_buffer = Reg(SInt(width.W))
  val inputY_buffer = Reg(SInt(width.W))
  val inputTheta_buffer = Reg(SInt(width.W))

  // --- Input reception state flags ---
  val mode_received = RegInit(false.B)
  val x_received = RegInit(false.B)
  val y_received = RegInit(false.B)
  val theta_received = RegInit(false.B)

  // --- Fixed-point constants (common and trigonometric) ---
  val ONE_FIXED = doubleToFixed(1.0, fractionalBits, width).S(width.W)
  val K_TRIG_fixed = doubleToFixed(TRIG_CORDIC_K_DBL, fractionalBits, width).S(width.W)
  val X_INIT_SINCOS_fixed = if (gainCorrection) K_TRIG_fixed else ONE_FIXED
  val Y_INIT_SINCOS_fixed = 0.S(width.W)
  val atanLUT_trig = VecInit(getAtanLUT(fractionalBits, width, cycleCount))

  // --- Linear CORDIC specific registers and constants (conditionally defined) ---
  val cordic_range_limit_r_fixed_linear = if (includeLinear) Some(doubleToFixed(CORDIC_LINEAR_RANGE_LIMIT_R, fractionalBits, width).S(width.W)) else None
  val currentScalingFactorK_linear = if (includeLinear) Some(Reg(UInt(log2Ceil(width + 1).W))) else None
  val originalDivisorIsNegative_linear = if (includeLinear) Some(Reg(Bool())) else None
  val termX_shifted_linear = if (includeLinear) Some(Reg(SInt(width.W))) else None // Holds x_val >> iter or abs(divisor) >> iter
  val termOne_shifted_linear = if (includeLinear) Some(Reg(SInt(width.W))) else None // Holds ONE_FIXED >> iter

  // --- Hyperbolic CORDIC specific registers and constants (conditionally defined) ---
  val hyperShiftExponentsSeq_hyper = if (includeHyperbolic) Some(getHyperbolicShiftExponents(cycleCount)) else None
  val K_H_TOTAL_ITER_GAIN_DBL_hyper = if (includeHyperbolic && hyperShiftExponentsSeq_hyper.get.nonEmpty) Some(calculateHyperbolicGainFactor(hyperShiftExponentsSeq_hyper.get)) else None
  val INV_K_H_TOTAL_fixed_hyper = if (includeHyperbolic && K_H_TOTAL_ITER_GAIN_DBL_hyper.isDefined && K_H_TOTAL_ITER_GAIN_DBL_hyper.get != 0) Some(doubleToFixed(1.0 / K_H_TOTAL_ITER_GAIN_DBL_hyper.get, fractionalBits, width).S(width.W)) else if (includeHyperbolic) Some(ONE_FIXED) else None // Fallback if gain is 0 or not calculable
  val X_INIT_HYPER_fixed_hyper = if (includeHyperbolic) Some(if (gainCorrection && INV_K_H_TOTAL_fixed_hyper.isDefined) INV_K_H_TOTAL_fixed_hyper.get else ONE_FIXED) else None
  val Y_INIT_HYPER_fixed = 0.S(width.W)
  val atanHyperLUT_hyper = if (includeHyperbolic) Some(VecInit(getAtanHyperLUT(fractionalBits, width, hyperShiftExponentsSeq_hyper.get))) else None
  val hyperShiftExponentsVec_hyper = if (includeHyperbolic) Some(VecInit(hyperShiftExponentsSeq_hyper.get.map(_.U(log2Ceil(math.max(1,width)).W)))) else None // Max with 1 for log2Ceil if width is small


  // --- Output Registers ---
  val output1_reg = Reg(SInt(width.W))
  val output2_reg = Reg(SInt(width.W))
  io.output1.bits := output1_reg
  io.output2.bits := output2_reg

  // --- Input Ready & Output Valid Logic Default Assignments ---
  io.mode.ready := false.B
  io.inputX.ready := false.B
  io.inputY.ready := false.B
  io.inputTheta.ready := false.B
  io.output1.valid := false.B
  io.output2.valid := false.B

  // --- Main State Machine ---
  switch(state) {
    is(State.sIdle) {
      // Step 1: Receive CORDIC operation mode
      io.mode.ready := !mode_received
      when(io.mode.fire) {
        currentOpMode_reg := io.mode.bits
        mode_received := true.B
      }

      // Step 2: Once mode is received, signal readiness for required data inputs
      when(mode_received) {
        // Determine which inputs are needed for the latched mode
        // TrigSinCos: Theta
        // TrigArctanMagnitude: X, Y
        // LinearMultiply: X (A), Y (B)
        // LinearDivide: X (A), Y (B)
        // HyperSinhCosh: Theta
        // HyperAtanhMagnitude: X, Y
        val op = currentOpMode_reg
        val needsX = op === CORDICModeAll.TrigArctanMagnitude ||
                     (if (includeLinear) op === CORDICModeAll.LinearMultiply || op === CORDICModeAll.LinearDivide else false.B) ||
                     (if (includeHyperbolic) op === CORDICModeAll.HyperAtanhMagnitude else false.B)

        val needsY = op === CORDICModeAll.TrigArctanMagnitude ||
                     (if (includeLinear) op === CORDICModeAll.LinearMultiply || op === CORDICModeAll.LinearDivide else false.B) ||
                     (if (includeHyperbolic) op === CORDICModeAll.HyperAtanhMagnitude else false.B)
        
        val needsTheta = op === CORDICModeAll.TrigSinCos ||
                         (if (includeHyperbolic) op === CORDICModeAll.HyperSinhCosh else false.B)

        io.inputX.ready := needsX && !x_received
        io.inputY.ready := needsY && !y_received
        io.inputTheta.ready := needsTheta && !theta_received

        when(io.inputX.fire) { inputX_buffer := io.inputX.bits; x_received := true.B }
        when(io.inputY.fire) { inputY_buffer := io.inputY.bits; y_received := true.B }
        when(io.inputTheta.fire) { inputTheta_buffer := io.inputTheta.bits; theta_received := true.B }

        // Step 3: Check if all required inputs for the latched mode have been received
        val all_data_inputs_received = (needsX === x_received) && (needsY === y_received) && (needsTheta === theta_received)

        when(all_data_inputs_received) {
          // All necessary inputs received, initialize CORDIC registers and proceed to busy state
          iter_count := 0.U

          // Initialize x_reg, y_reg, z_reg based on currentOpMode_reg
          switch(currentOpMode_reg) {
            is(CORDICModeAll.TrigSinCos) {
              x_reg := X_INIT_SINCOS_fixed
              y_reg := Y_INIT_SINCOS_fixed
              z_reg := inputTheta_buffer
            }
            is(CORDICModeAll.TrigArctanMagnitude) {
              x_reg := inputX_buffer
              y_reg := inputY_buffer
              z_reg := 0.S(width.W)
            }
            is(CORDICModeAll.LinearMultiply) {
              if (includeLinear) {
                val inputA = inputX_buffer // Multiplicand A
                val inputB = inputY_buffer // Multiplier B
                val inputB_abs_uint = inputB.abs.asUInt
                
                val conditions_mult = VecInit(Seq.tabulate(width + 1) { k_idx =>
                  (inputB_abs_uint >> k_idx) <= cordic_range_limit_r_fixed_linear.get.asUInt
                })
                val k_mult = PriorityEncoder(conditions_mult)
                currentScalingFactorK_linear.get := k_mult

                x_reg := inputA                  // x = Multiplicand A
                y_reg := 0.S                     // y = Accumulator for product, starts at 0
                z_reg := inputB >> k_mult        // z = Scaled Multiplier B'
                
                originalDivisorIsNegative_linear.get := false.B // Not used for multiply
                termX_shifted_linear.get := inputA        // termX starts as Multiplicand
                termOne_shifted_linear.get := ONE_FIXED   // termOne starts as 1.0
              }
            }
            is(CORDICModeAll.LinearDivide) {
              if (includeLinear) {
                val inputA = inputX_buffer // Dividend A
                val inputB = inputY_buffer // Divisor B
                originalDivisorIsNegative_linear.get := inputB < 0.S
                val absDivisor_uint = inputB.abs.asUInt
                val inputA_abs_uint = inputA.abs.asUInt

                val limit_prod_intermediate = absDivisor_uint.zext * cordic_range_limit_r_fixed_linear.get.asUInt.zext
                val limitForAScaledAbs_UInt = (limit_prod_intermediate >> fractionalBits).asUInt

                val conditions_div = VecInit(Seq.tabulate(width + 1) { k_idx =>
                  Mux(limitForAScaledAbs_UInt === 0.U && inputA_abs_uint =/= 0.U,
                    (inputA_abs_uint >> k_idx) === 0.U,
                    (inputA_abs_uint >> k_idx) <= limitForAScaledAbs_UInt)
                })
                val k_div = PriorityEncoder(conditions_div)
                currentScalingFactorK_linear.get := k_div

                x_reg := absDivisor_uint.asSInt // x = abs(Divisor)
                y_reg := inputA >> k_div       // y = Scaled Dividend A'
                z_reg := 0.S                   // z = Accumulator for quotient
                
                termX_shifted_linear.get := absDivisor_uint.asSInt // termX starts as abs(Divisor)
                termOne_shifted_linear.get := ONE_FIXED            // termOne starts as 1.0
              }
            }
            is(CORDICModeAll.HyperSinhCosh) {
              if (includeHyperbolic) {
                x_reg := X_INIT_HYPER_fixed_hyper.get
                y_reg := Y_INIT_HYPER_fixed
                z_reg := inputTheta_buffer
              }
            }
            is(CORDICModeAll.HyperAtanhMagnitude) {
              if (includeHyperbolic) {
                x_reg := inputX_buffer
                y_reg := inputY_buffer
                z_reg := 0.S(width.W)
              }
            }
          }
          state := State.sBusy
          // Reset input reception flags for the next transaction
          mode_received := false.B
          x_received := false.B
          y_received := false.B
          theta_received := false.B
        }
      }
    } // End sIdle

    is(State.sBusy) {
      when(iter_count < cycleCount.U) {
        val current_iter_idx = iter_count // For Trig LUT and shifts (0 to cycleCount-1)
        
        // --- Temporary wires for shifted values and angles for Trig/Hyper ---
        val y_shifted_trig_hyper = WireDefault(0.S(width.W))
        val x_shifted_trig_hyper = WireDefault(0.S(width.W))
        val delta_theta_trig_hyper = WireDefault(0.S(width.W))
        
        // --- Direction of rotation/vectoring ---
        val direction = Wire(SInt(2.W)) // +1 or -1
        direction := 0.S // Default, should be overridden by active mode logic

        // --- CORDIC Iteration Logic per mode ---
        switch(currentOpMode_reg) {
          is(CORDICModeAll.TrigSinCos) { // Rotation mode
            y_shifted_trig_hyper := (y_reg >> current_iter_idx).asSInt
            x_shifted_trig_hyper := (x_reg >> current_iter_idx).asSInt
            delta_theta_trig_hyper := atanLUT_trig(current_iter_idx)
            
            direction := Mux(z_reg >= 0.S, 1.S(2.W), -1.S(2.W)) // d = sign(z)

            x_reg := x_reg - (direction * y_shifted_trig_hyper)
            y_reg := y_reg + (direction * x_shifted_trig_hyper)
            z_reg := z_reg - (direction * delta_theta_trig_hyper)
          }
          is(CORDICModeAll.TrigArctanMagnitude) { // Vectoring mode
            y_shifted_trig_hyper := (y_reg >> current_iter_idx).asSInt
            x_shifted_trig_hyper := (x_reg >> current_iter_idx).asSInt
            delta_theta_trig_hyper := atanLUT_trig(current_iter_idx)

            direction := Mux(y_reg >= 0.S, 1.S(2.W), -1.S(2.W)) // d = sign(y) (simplified from original)

            x_reg := x_reg + (direction * y_shifted_trig_hyper)
            y_reg := y_reg - (direction * x_shifted_trig_hyper)
            z_reg := z_reg + (direction * delta_theta_trig_hyper)
          }
          is(CORDICModeAll.LinearMultiply) {
            if (includeLinear) {
              val current_termX = termX_shifted_linear.get
              val current_termOne = termOne_shifted_linear.get
              
              val z_sign = Mux(z_reg > 0.S, 1.S(2.W), Mux(z_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(z_sign === 0.S, 1.S(2.W), z_sign) // d = (z==0) ? 1 : sign(z)

              y_reg := y_reg + (direction * current_termX) // y_new = y + d*x_shifted_term
              z_reg := z_reg - (direction * current_termOne) // z_new = z - d*one_shifted_term

              termX_shifted_linear.get := (current_termX >> 1).asSInt
              termOne_shifted_linear.get := (current_termOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.LinearDivide) {
            if (includeLinear) {
              val current_termX = termX_shifted_linear.get
              val current_termOne = termOne_shifted_linear.get

              val y_sign = Mux(y_reg > 0.S, 1.S(2.W), Mux(y_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(y_sign === 0.S, -1.S(2.W), -y_sign) // d = (y==0) ? -1 : -sign(y)

              y_reg := y_reg + (direction * current_termX) // y_new = y + d*x_shifted_term (x is divisor term)
              z_reg := z_reg - (direction * current_termOne) // z_new = z - d*one_shifted_term

              termX_shifted_linear.get := (current_termX >> 1).asSInt
              termOne_shifted_linear.get := (current_termOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.HyperSinhCosh) { 
            if (includeHyperbolic) { // Hyperbolic Rotation mode
              val current_shift_exponent = hyperShiftExponentsVec_hyper.get(current_iter_idx)
              y_shifted_trig_hyper := (y_reg >> current_shift_exponent).asSInt
              x_shifted_trig_hyper := (x_reg >> current_shift_exponent).asSInt
              delta_theta_trig_hyper := atanHyperLUT_hyper.get(current_iter_idx)
              
              val z_sign = Mux(z_reg > 0.S, 1.S(2.W), Mux(z_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(z_sign === 0.S, -1.S(2.W), z_sign) // d = (z==0) ? -1 : sign(z)
              
              x_reg := x_reg + (direction * y_shifted_trig_hyper) // x_new = x + d*y_shifted
              y_reg := y_reg + (direction * x_shifted_trig_hyper) // y_new = y + d*x_shifted
              z_reg := z_reg - (direction * delta_theta_trig_hyper)
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude) { 
            if (includeHyperbolic) { // Hyperbolic Vectoring mode
              val current_shift_exponent = hyperShiftExponentsVec_hyper.get(current_iter_idx)
              y_shifted_trig_hyper := (y_reg >> current_shift_exponent).asSInt
              x_shifted_trig_hyper := (x_reg >> current_shift_exponent).asSInt
              delta_theta_trig_hyper := atanHyperLUT_hyper.get(current_iter_idx)

              val y_sign = Mux(y_reg > 0.S, 1.S(2.W), Mux(y_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(y_sign === 0.S, -1.S(2.W), -y_sign) // d = (y==0) ? -1 : -sign(y)

              x_reg := x_reg + (direction * y_shifted_trig_hyper) // x_new = x + d*y_shifted
              y_reg := y_reg + (direction * x_shifted_trig_hyper) // y_new = y + d*x_shifted
              z_reg := z_reg - (direction * delta_theta_trig_hyper) // z_new = z - d*delta_theta (note: minus for atanh accumulation)
            }
          }
        }
        iter_count := iter_count + 1.U
      }.otherwise {
        // Iterations complete
        state := State.sDone
      }
    } // End sBusy

    is(State.sDone) {
      // Calculate final results based on currentOpMode_reg and CORDIC registers
      var res1 = 0.S(width.W)
      var res2 = 0.S(width.W)

      switch(currentOpMode_reg) {
        is(CORDICModeAll.TrigSinCos) {
          res1 = x_reg // cosOut
          res2 = y_reg // sinOut
        }
        is(CORDICModeAll.TrigArctanMagnitude) {
          res1 = z_reg // arctanOut
          if (gainCorrection) {
            val magnitude_full_prod = x_reg * K_TRIG_fixed
            res2 = (magnitude_full_prod >> fractionalBits.U).asSInt // magnitudeOut
          } else {
            res2 = x_reg // magnitudeOut (uncorrected)
          }
        }
        is(CORDICModeAll.LinearMultiply) {
          if (includeLinear) {
            val k_val = currentScalingFactorK_linear.get
            val shifted_prod = y_reg << k_val // y_reg is (A * B_scaled)
            res1 = clamp(shifted_prod, width) // product = (A * B_scaled) * 2^k
            res2 = 0.S // Output2 not used for Linear Multiply
          }
        }
        is(CORDICModeAll.LinearDivide) {
          if (includeLinear) {
            val k_val = currentScalingFactorK_linear.get
            val shifted_quot_base = z_reg << k_val // z_reg is (A_scaled / abs(B))
            val finalQuotient_value = Mux(originalDivisorIsNegative_linear.get, -shifted_quot_base, shifted_quot_base)
            res1 = clamp(finalQuotient_value, width) // quotient = (A_scaled / abs(B)) * 2^k = A / B
            res2 = 0.S // Output2 not used for Linear Divide
          }
        }
        is(CORDICModeAll.HyperSinhCosh) {
          if (includeHyperbolic) {
            // x_reg should be cosh(Theta) and y_reg should be sinh(Theta)
            // if gainCorrection was applied via X_INIT_HYPER_fixed_hyper
            res1 = x_reg // coshOut
            res2 = y_reg // sinhOut
          }
        }
        is(CORDICModeAll.HyperAtanhMagnitude) {
          if (includeHyperbolic) {
            res1 = z_reg // atanhOut
            if (gainCorrection && INV_K_H_TOTAL_fixed_hyper.isDefined) { // Check INV_K_H_TOTAL_fixed_hyper is defined
              // x_reg is Mag_true * K_H_eff. Multiply by 1/K_H_eff.
              val magnitude_full_prod = x_reg * INV_K_H_TOTAL_fixed_hyper.get
              res2 = (magnitude_full_prod >> fractionalBits.U).asSInt // magnitudeResultHyper
            } else {
              res2 = x_reg // magnitudeResultHyper (uncorrected or if gain couldn't be applied)
            }
          }
        }
      }
      output1_reg := res1
      output2_reg := res2
      
      io.output1.valid := true.B
      io.output2.valid := true.B // Assert valid for both; consumer checks if output2 is meaningful

      // Wait for consumer to be ready for both outputs
      when(io.output1.ready && io.output2.ready) {
        state := State.sIdle
        io.output1.valid := false.B // Deassert valid once consumed
        io.output2.valid := false.B
      }
    } // End sDone
  } // End switch(state)
} 
```


#### Short summary: 

empty definition using pc, found symbol in pc: 