error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICcore.scala:
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICcore.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 3460
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
        HyperSinhCosh, HyperAtanhMagnitude, // Hyperbolic (conditionally included)
        Exponential = Value // Added exponential mode
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
  def getHyperbolicShiftExponents(cycleCount: Int): Seq[Int] = { //WORKS
    var exponents = scala.collection.mutable.ArrayBuffer[Int]()
    var i = 0
    var k = 1
    var nextRepeat = 4 // First repeat occurs at k=4
    
    while (i < cycleCount) {
      exponents += k
      i += 1
      if (k == nextRepeat && i < cycleCount) {
        exponents += k
        i += 1
        nextRepeat = nextRepeat * 3 + 1
      }
      k += 1
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
  import CORDICCore._ // Import f@@rom companion object

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
    val inputX = Input(SInt(width.W))     // Trig: X, Linear: A (Multiplicand/Dividend), Hyper: X
    val inputY = Input(SInt(width.W))     // Trig: Y, Linear: B (Multiplier/Divisor),  Hyper: Y
    val inputTheta = Input(SInt(width.W)) // Trig: Theta, Hyper: Theta (Linear does not use Theta)

    // Data Outputs
    val output1 = Decoupled(SInt(width.W))
    val output2 = Decoupled(SInt(width.W))
  })

  // --- Internal State Machine Definition ---
  object State extends ChiselEnum {
    val sIdle, sBusy, sDone = Value
  }
  val state = RegInit(State.sIdle)
  // Printfs for state transitions
  when(state =/= RegNext(state)) {
    // printf(p"State changed from ${RegNext(state)} to $state\n")
  }


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

  // --- Fixed-point constants (common and trigonometric) ---
  val ONE_FIXED = doubleToFixed(1.0, fractionalBits, width).S(width.W)
  val K_TRIG_fixed = doubleToFixed(TRIG_CORDIC_K_DBL, fractionalBits, width).S(width.W)
  val INV_K_TRIG_fixed = doubleToFixed(1.0 / TRIG_CORDIC_K_DBL, fractionalBits, width).S(width.W) // Added for gain correction
  val X_INIT_SINCOS_fixed = if (gainCorrection) K_TRIG_fixed else ONE_FIXED // Modified for gain correction
  val Y_INIT_SINCOS_fixed = 0.S(width.W)
  val atanLUT_trig = VecInit(getAtanLUT(fractionalBits, width, cycleCount))

  // --- Linear CORDIC specific registers and constants (conditionally defined) ---
  val cordic_range_limit_r_fixed_linear = if (includeLinear) Some(doubleToFixed(CORDIC_LINEAR_RANGE_LIMIT_R, fractionalBits, width).S(width.W)) else None
  val currentScalingFactorK_linear = if (includeLinear) Some(Reg(UInt(log2Ceil(width + 1).W))) else None
  val originalDivisorIsNegative_linear = if (includeLinear) Some(Reg(Bool())) else None
  val termX_shifted_linear = if (includeLinear) Some(Reg(SInt(width.W))) else None
  val termOne_shifted_linear = if (includeLinear) Some(Reg(SInt(width.W))) else None

  // --- Hyperbolic CORDIC specific registers and constants (conditionally defined) ---
  val hyperShiftExponentsSeq_hyper = if (includeHyperbolic) Some(getHyperbolicShiftExponents(cycleCount)) else None
  val K_H_TOTAL_ITER_GAIN_DBL_hyper = if (includeHyperbolic && hyperShiftExponentsSeq_hyper.get.nonEmpty) Some(calculateHyperbolicGainFactor(hyperShiftExponentsSeq_hyper.get)) else None
  val INV_K_H_TOTAL_fixed_hyper = if (includeHyperbolic && K_H_TOTAL_ITER_GAIN_DBL_hyper.isDefined && K_H_TOTAL_ITER_GAIN_DBL_hyper.get != 0) Some(doubleToFixed(1.0 / K_H_TOTAL_ITER_GAIN_DBL_hyper.get, fractionalBits, width).S(width.W)) else if (includeHyperbolic) Some(ONE_FIXED) else None
  val X_INIT_HYPER_fixed_hyper = if (includeHyperbolic) Some(if (gainCorrection) INV_K_H_TOTAL_fixed_hyper.get else ONE_FIXED) else None
  val Y_INIT_HYPER_fixed = 0.S(width.W)
  val atanHyperLUT_hyper = if (includeHyperbolic) Some(VecInit(getAtanHyperLUT(fractionalBits, width, hyperShiftExponentsSeq_hyper.get))) else None
  val hyperShiftExponentsVec_hyper = if (includeHyperbolic) Some(VecInit(hyperShiftExponentsSeq_hyper.get.map(_.U(log2Ceil(math.max(1,width)).W)))) else None


  // --- Output Registers ---
  val output1_reg = Reg(SInt(width.W))
  val output2_reg = Reg(SInt(width.W))
  io.output1.bits := output1_reg
  io.output2.bits := output2_reg

  // --- Input Ready & Output Valid Logic Default Assignments ---
  io.mode.ready := false.B // Will be overridden in sIdle
  io.output1.valid := false.B // Default, will be overridden in sDone
  io.output2.valid := false.B // Default, will be overridden in sDone

  // --- Main State Machine ---
  switch(state) {
    is(State.sIdle) {
      io.mode.ready := true.B 
      // printf(p"sIdle: Mode ready. Waiting for mode.fire\n")

      when(io.mode.fire) {
        currentOpMode_reg := io.mode.bits
        inputX_buffer := io.inputX
        inputY_buffer := io.inputY
        inputTheta_buffer := io.inputTheta
        
        // printf(p"sIdle: Mode fired! Mode: ${io.mode.bits}, X: ${io.inputX}, Y: ${io.inputY}, Theta: ${io.inputTheta}\n")
        // printf(p"sIdle: currentOpMode_reg will be updated to ${io.mode.bits}. X_buf_prev: $inputX_buffer, Y_buf_prev: $inputY_buffer, Theta_buf_prev: $inputTheta_buffer\n")

        iter_count := 0.U
        // Initialize x_reg, y_reg, z_reg based on the *NEWLY RECEIVED* io.mode.bits and direct io.inputs
        switch(io.mode.bits) {
          is(CORDICModeAll.TrigSinCos) {
            x_reg := X_INIT_SINCOS_fixed
            y_reg := Y_INIT_SINCOS_fixed
            z_reg := io.inputTheta // Use direct input
            // printf(p"sIdle->Init TrigSinCos (using io.mode.bits & io.inputTheta): x_reg=${X_INIT_SINCOS_fixed}, y_reg=${Y_INIT_SINCOS_fixed}, z_reg=${io.inputTheta}\n")
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            x_reg := io.inputX     // Use direct input
            y_reg := io.inputY     // Use direct input
            z_reg := 0.S(width.W)
            // printf(p"sIdle->Init TrigArcTanMag (using io.mode.bits & io.inputs): x_reg=${io.inputX}, y_reg=${io.inputY}, z_reg=0\n")
          }
          is(CORDICModeAll.LinearMultiply) {
            if (includeLinear) {
              val inputA = io.inputX // Use direct input
              val inputB = io.inputY // Use direct input
              val inputB_abs_uint = inputB.abs.asUInt
              
              val conditions_mult = VecInit(Seq.tabulate(width + 1) { k_idx =>
                (inputB_abs_uint >> k_idx) <= cordic_range_limit_r_fixed_linear.get.asUInt
              })
              val k_mult = PriorityEncoder(conditions_mult)
              currentScalingFactorK_linear.get := k_mult

              x_reg := inputA
              y_reg := 0.S 
              z_reg := inputB >> k_mult
              originalDivisorIsNegative_linear.get := false.B // Should be based on inputB, not buffer
              termX_shifted_linear.get := inputA
              termOne_shifted_linear.get := ONE_FIXED
              // printf(p"sIdle->Init LinearMultiply (using io.mode.bits & io.inputs): A(X)=${inputA}, B(Y)=${inputB}, k_mult=${k_mult}, x_reg=${inputA}, y_reg=0, z_reg=${inputB >> k_mult}\n")
            } else { // printf(p"sIdle: LinearMultiply mode selected (io.mode.bits) but includeLinear is false.\n")
            }
          }
          is(CORDICModeAll.LinearDivide) {
            if (includeLinear) {
              val inputA = io.inputX // Use direct input
              val inputB = io.inputY // Use direct input
              originalDivisorIsNegative_linear.get := inputB < 0.S // Use direct input
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

              x_reg := absDivisor_uint.asSInt 
              y_reg := inputA >> k_div
              z_reg := 0.S
              termX_shifted_linear.get := absDivisor_uint.asSInt // Should be based on inputB.abs
              termOne_shifted_linear.get := ONE_FIXED
              // printf(p"sIdle->Init LinearDivide (using io.mode.bits & io.inputs): A(X)=${inputA}, B(Y)=${inputB}, k_div=${k_div}, x_reg=${absDivisor_uint.asSInt}, y_reg=${inputA >> k_div}, z_reg=0\n")
            } else { // printf(p"sIdle: LinearDivide mode selected (io.mode.bits) but includeLinear is false.\n")
            }
          }
          is(CORDICModeAll.HyperSinhCosh) {
            if (includeHyperbolic) {
              x_reg := X_INIT_HYPER_fixed_hyper.get
              y_reg := Y_INIT_HYPER_fixed
              z_reg := io.inputTheta // Use direct input
              // printf(p"sIdle->Init HyperSinhCosh (using io.mode.bits & io.inputTheta): x_reg=${X_INIT_HYPER_fixed_hyper.get}, y_reg=${Y_INIT_HYPER_fixed}, z_reg=${io.inputTheta}\n")
            } else { // printf(p"sIdle: HyperSinhCosh mode selected (io.mode.bits) but includeHyperbolic is false.\n")
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude) {
            if (includeHyperbolic) {
              x_reg := io.inputX     // Use direct input
              y_reg := io.inputY     // Use direct input
              z_reg := 0.S(width.W)
              // printf(p"sIdle->Init HyperAtanhMag (using io.mode.bits & io.inputs): x_reg=${io.inputX}, y_reg=${io.inputY}, z_reg=0\n")
            } else { // printf(p"sIdle: HyperAtanhMagnitude mode selected (io.mode.bits) but includeHyperbolic is false.\n")
            }
          }
          is(CORDICModeAll.Exponential) {
            if (includeHyperbolic) {
              x_reg := X_INIT_HYPER_fixed_hyper.get
              y_reg := Y_INIT_HYPER_fixed
              z_reg := io.inputTheta // Use direct input for the exponent value
            }
          }
        }
        state := State.sBusy
      }
    } 

    is(State.sBusy) {
      // printf(p"sBusy: Iteration ${iter_count} / ${cycleCount.U}\n")
      // printf(p"sBusy: x_reg=${x_reg}, y_reg=${y_reg}, z_reg=${z_reg}\n")

      when(iter_count < cycleCount.U) {
        val current_iter_idx = iter_count
        val y_shifted_trig_hyper = WireDefault(0.S(width.W))
        val x_shifted_trig_hyper = WireDefault(0.S(width.W))
        val delta_theta_trig_hyper = WireDefault(0.S(width.W))
        val direction = Wire(SInt(2.W)); direction := 0.S

        switch(currentOpMode_reg) {
          is(CORDICModeAll.TrigSinCos) {
            y_shifted_trig_hyper := (y_reg >> current_iter_idx).asSInt
            x_shifted_trig_hyper := (x_reg >> current_iter_idx).asSInt
            delta_theta_trig_hyper := atanLUT_trig(current_iter_idx)
            direction := Mux(z_reg >= 0.S, 1.S(2.W), -1.S(2.W))
            x_reg := x_reg - (direction * y_shifted_trig_hyper)
            y_reg := y_reg + (direction * x_shifted_trig_hyper)
            z_reg := z_reg - (direction * delta_theta_trig_hyper)
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            y_shifted_trig_hyper := (y_reg >> current_iter_idx).asSInt
            x_shifted_trig_hyper := (x_reg >> current_iter_idx).asSInt
            delta_theta_trig_hyper := atanLUT_trig(current_iter_idx)
            direction := Mux(y_reg >= 0.S, 1.S(2.W), -1.S(2.W))
            x_reg := x_reg + (direction * y_shifted_trig_hyper)
            y_reg := y_reg - (direction * x_shifted_trig_hyper)
            z_reg := z_reg + (direction * delta_theta_trig_hyper)
          }
          is(CORDICModeAll.LinearMultiply) {
            if (includeLinear) {
              val current_termX = termX_shifted_linear.get
              val current_termOne = termOne_shifted_linear.get
              val z_sign = Mux(z_reg > 0.S, 1.S(2.W), Mux(z_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(z_sign === 0.S, 1.S(2.W), z_sign)
              y_reg := y_reg + (direction * current_termX)
              z_reg := z_reg - (direction * current_termOne)
              termX_shifted_linear.get := (current_termX >> 1).asSInt
              termOne_shifted_linear.get := (current_termOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.LinearDivide) {
            if (includeLinear) {
              val current_termX = termX_shifted_linear.get
              val current_termOne = termOne_shifted_linear.get
              val y_sign = Mux(y_reg > 0.S, 1.S(2.W), Mux(y_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(y_sign === 0.S, -1.S(2.W), -y_sign)
              y_reg := y_reg + (direction * current_termX)
              z_reg := z_reg - (direction * current_termOne)
              termX_shifted_linear.get := (current_termX >> 1).asSInt
              termOne_shifted_linear.get := (current_termOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.HyperSinhCosh) { 
            if (includeHyperbolic) {
              val current_shift_exponent = hyperShiftExponentsVec_hyper.get(current_iter_idx)
              y_shifted_trig_hyper := (y_reg >> current_shift_exponent).asSInt
              x_shifted_trig_hyper := (x_reg >> current_shift_exponent).asSInt
              delta_theta_trig_hyper := atanHyperLUT_hyper.get(current_iter_idx)
              val z_sign = Mux(z_reg > 0.S, 1.S(2.W), Mux(z_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(z_sign === 0.S, -1.S(2.W), z_sign)
              x_reg := x_reg + (direction * y_shifted_trig_hyper)
              y_reg := y_reg + (direction * x_shifted_trig_hyper)
              z_reg := z_reg - (direction * delta_theta_trig_hyper)
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude) { 
            if (includeHyperbolic) {
              val current_shift_exponent = hyperShiftExponentsVec_hyper.get(current_iter_idx)
              y_shifted_trig_hyper := (y_reg >> current_shift_exponent).asSInt
              x_shifted_trig_hyper := (x_reg >> current_shift_exponent).asSInt
              delta_theta_trig_hyper := atanHyperLUT_hyper.get(current_iter_idx)
              val y_sign = Mux(y_reg > 0.S, 1.S(2.W), Mux(y_reg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(y_sign === 0.S, -1.S(2.W), -y_sign)
              x_reg := x_reg + (direction * y_shifted_trig_hyper)
              y_reg := y_reg + (direction * x_shifted_trig_hyper)
              z_reg := z_reg - (direction * delta_theta_trig_hyper)
            }
          }
        }
        // printf(p"sBusy: After Iter ${iter_count} -> x_reg=${x_reg}, y_reg=${y_reg}, z_reg=${z_reg}, dir=${direction}\n")
        iter_count := iter_count + 1.U
      }.otherwise {
        // printf(p"sBusy: Iterations complete. Moving to sDone.\n")
        state := State.sDone
      }
    } 

    is(State.sDone) {
      // printf(p"sDone: Calculating final results. Mode: ${currentOpMode_reg}\n")
      // Initialize to a known default. These will be overridden by specific mode logic.
      output1_reg := 0.S(width.W) 
      output2_reg := 0.S(width.W)

      switch(currentOpMode_reg) {
        is(CORDICModeAll.TrigSinCos) {
          output1_reg := x_reg // cosOut
          output2_reg := y_reg // sinOut
          // printf(p"sDone Results TrigSinCos: out1(cos)=${x_reg}, out2(sin)=${y_reg}\n")
        }
        is(CORDICModeAll.TrigArctanMagnitude) {
          output1_reg := z_reg // arctanOut
          if (gainCorrection) {
            val magnitude_full_prod = x_reg * K_TRIG_fixed
            output2_reg := (magnitude_full_prod >> fractionalBits.U).asSInt
          } else {
            output2_reg := x_reg
          }
          // printf(p"sDone Results TrigArcTanMag: out1(atan)=${z_reg}, out2(mag)=${output2_reg}\n")
        }
        is(CORDICModeAll.LinearMultiply) {
          if (includeLinear) {
            val k_val = currentScalingFactorK_linear.get
            val shifted_prod = y_reg << k_val 
            output1_reg := clamp(shifted_prod, width) 
            output2_reg := 0.S // Output2 not used
            // printf(p"sDone Results LinearMultiply: out1(prod)=${output1_reg}\n")
          } else { // printf(p"sDone: LinearMultiply mode - includeLinear is false.\n") 
          }
        }
        is(CORDICModeAll.LinearDivide) {
          if (includeLinear) {
            val k_val = currentScalingFactorK_linear.get
            val shifted_quot_base = z_reg << k_val 
            val finalQuotient_value = Mux(originalDivisorIsNegative_linear.get, -shifted_quot_base, shifted_quot_base)
            output1_reg := clamp(finalQuotient_value, width) 
            output2_reg := 0.S // Output2 not used
            // printf(p"sDone Results LinearDivide: out1(quot)=${output1_reg}\n")
          } else { // printf(p"sDone: LinearDivide mode - includeLinear is false.\n") 
          }
        }
        is(CORDICModeAll.HyperSinhCosh) {
          if (includeHyperbolic) {
            output1_reg := x_reg // coshOut
            output2_reg := y_reg // sinhOut
            // printf(p"sDone Results HyperSinhCosh: out1(cosh)=${x_reg}, out2(sinh)=${y_reg}\n")
          } else { // printf(p"sDone: HyperSinhCosh mode - includeHyperbolic is false.\n") 
          }
        }
        is(CORDICModeAll.HyperAtanhMagnitude) {
          if (includeHyperbolic) {
            output1_reg := z_reg // atanhOut
            if (gainCorrection && INV_K_H_TOTAL_fixed_hyper.isDefined) { 
              val magnitude_full_prod = x_reg * INV_K_H_TOTAL_fixed_hyper.get
              output2_reg := (magnitude_full_prod >> fractionalBits.U).asSInt
            } else {
              output2_reg := x_reg 
            }
            // printf(p"sDone Results HyperAtanhMag: out1(atanh)=${z_reg}, out2(mag)=${output2_reg}\n")
          } else { // printf(p"sDone: HyperAtanhMagnitude mode - includeHyperbolic is false.\n") 
          }
        }
        is(CORDICModeAll.Exponential) {
          if (includeHyperbolic) {
            // e^x = cosh(x) + sinh(x)
            output1_reg := x_reg + y_reg
            // e^-x = cosh(x) - sinh(x)
            output2_reg := x_reg - y_reg
          }
        }
      }
      
      io.output1.valid := true.B
      io.output2.valid := true.B
      // printf(p"sDone: Outputs valid. output1_reg=${output1_reg}, output2_reg=${output2_reg}. Waiting for ready...\n")

      when(io.output1.ready && io.output2.ready) {
        state := State.sIdle
        // printf(p"sDone: Outputs taken by consumer. Transitioning to sIdle.\n")
      }
    } 
  } 
} 
```


#### Short summary: 

empty definition using pc, found symbol in pc: 