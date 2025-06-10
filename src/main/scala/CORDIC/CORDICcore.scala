package CORDIC

import chisel3._
import chisel3.util._
import scala.math.{pow, sqrt, log, atan}

/** Companion object for the CORDICCore module.
 *  Contains enumeration for CORDIC modes, mathematical constants, and helper functions.
 */
object CORDICCore {
  /** Defines the operational modes for the CORDIC core. */
  object CORDICModeAll extends ChiselEnum {
    val TrigSinCos, TrigArctanMagnitude,
        LinearMultiply, LinearDivide,
        HyperSinhCosh, HyperAtanhMagnitude,
        Exponential, NaturalLog = Value
  }

  /**
   * Converts a Double to a fixed-point representation.
   * @param x The Double value to convert.
   * @param fractionalBits The number of fractional bits in the fixed-point representation.
   * @param width The total bit width of the fixed-point number (including sign bit).
   * @return The fixed-point value as a BigInt.
   */
  def doubleToFixed(x: Double, fractionalBits: Int, width: Int): BigInt = {
    val scaled = BigDecimal(x) * BigDecimal(BigInt(1) << fractionalBits)
    val rounded = scaled.setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt
    val maxVal = (BigInt(1) << (width - 1)) - 1
    val minVal = -(BigInt(1) << (width - 1))
    rounded.max(minVal).min(maxVal)
  }

  /**
   * Clamps an SInt value to a specified target width, preventing overflow.
   * @param value The SInt value to clamp.
   * @param targetWidth The bit width to clamp to.
   * @return The clamped SInt value.
   */
  def clamp(value: SInt, targetWidth: Int): SInt = {
    val maxValTarget = ((BigInt(1) << (targetWidth - 1)) - 1).S(targetWidth.W)
    val minValTarget = (-(BigInt(1) << (targetWidth - 1))).S(targetWidth.W)
    val valueW = value.getWidth
    val maxValExtended = maxValTarget.pad(valueW)
    val minValExtended = minValTarget.pad(valueW)
    Mux(value > maxValExtended, maxValTarget,
      Mux(value < minValExtended, minValTarget,
        value(targetWidth - 1, 0).asSInt))
  }

  /** The gain of the trigonometric CORDIC operations, approximately 1 / product(sqrt(1 + 2^(-2i))) for i from 0 to N-1.
   *  This is often denoted as 1/K or An.
   */
  val TRIGONOMETRIC_GAIN: Double = 0.6072529350088813
  /**
   * Generates a Look-Up Table (LUT) of pre-calculated arctangent values for trigonometric CORDIC.
   * These values are atan(2^-i) for each iteration i.
   * @param fractionalBits The number of fractional bits for the fixed-point representation of the angles.
   * @param width The total bit width for the angle values.
   * @param numEntries The number of entries in the LUT, corresponding to the number of CORDIC iterations.
   * @return A sequence of SInts representing the arctangent values.
   */
  def getAtanLUT(fractionalBits: Int, width: Int, numEntries: Int): Seq[SInt] = {
    (0 until numEntries).map { i =>
      val angleRad = scala.math.atan(scala.math.pow(2.0, -i))
      doubleToFixed(angleRad, fractionalBits, width).S(width.W)
    }
  }

  /** The convergence range limit for the linear CORDIC mode. The input must be scaled to be within this range. */
  val LINEAR_RANGE_LIMIT: Double = 1.99

  /**
   * Generates the sequence of shift exponents for hyperbolic CORDIC iterations.
   * Hyperbolic CORDIC requires certain iterations (k = 4, 13, 40, ...) to be repeated
   * to ensure convergence of the algorithm. This function generates the correct sequence
   * of iteration shift values 'k' based on the total number of cycles.
   * @param cycleCount The total number of CORDIC iterations.
   * @return A sequence of integers representing the shift exponents for each cycle.
   */
  def getHyperbolicShiftExponents(cycleCount: Int): Seq[Int] = {
    var exponents = scala.collection.mutable.ArrayBuffer[Int]()
    var i = 0
    var k = 1
    var nextRepeat = 4
    
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

  /**
   * Calculates the gain factor for hyperbolic CORDIC.
   * The gain is product(sqrt(1 - 2^(-2*k_i))) where k_i are the shift exponents.
   * @param shiftValues The sequence of shift exponents for the hyperbolic CORDIC iterations.
   * @return The calculated hyperbolic gain factor as a Double.
   */
  def calculateHyperbolicGainFactor(shiftValues: Seq[Int]): Double = {
    shiftValues.map { shiftValue =>
      sqrt(1.0 - pow(2.0, -2.0 * shiftValue))
    }.product
  }

  /**
   * Generates a Look-Up Table (LUT) of pre-calculated hyperbolic arctangent (atanh) values.
   * These values are atanh(2^-k) for each shift exponent k in the hyperbolic sequence.
   * @param fractionalBits The number of fractional bits for the fixed-point representation.
   * @param width The total bit width of the LUT values.
   * @param hyperShiftExponents The sequence of shift exponents for hyperbolic CORDIC.
   * @return A sequence of SInts representing the atanh values.
   */
  def getAtanHyperLUT(fractionalBits: Int, width: Int, hyperShiftExponents: Seq[Int]): Seq[SInt] = {
    hyperShiftExponents.map { exponent =>
      val xAtanh = pow(2.0, -exponent)
      val angleRad = 0.5 * log((1.0 + xAtanh) / (1.0 - xAtanh))
      doubleToFixed(angleRad, fractionalBits, width).S(width.W)
    }
  }
}

/**
 * CORDIC (COordinate Rotation DIgital Computer) core module.
 *
 * This module implements a versatile, iterative CORDIC core capable of performing
 * trigonometric, hyperbolic, and linear functions. The core is configurable in
 * terms of data width, number of iterations, and supported function families.
 * It operates in a pipelined manner over multiple clock cycles.
 *
 * @param width The data width for inputs, outputs, and internal calculations.
 * @param cycleCount The number of iterations for the CORDIC algorithm. More iterations lead to higher accuracy.
 * @param integerBits The number of integer bits in the fixed-point representation.
 * @param gainCorrection Whether to apply gain correction. CORDIC iterations introduce a gain that can be corrected.
 * @param includeLinear Whether to include support for linear functions (multiplication, division).
 * @param includeHyperbolic Whether to include support for hyperbolic functions (sinh, cosh, atanh, natural log, exponential).
 */
class CORDICCore(
    val width: Int,
    val cycleCount: Int,
    val integerBits: Int = 3,
    val gainCorrection: Boolean = true,
    val includeLinear: Boolean,
    val includeHyperbolic: Boolean
) extends Module {
  import CORDICCore._

  require(width > 0, "Width must be positive")
  require(cycleCount > 0, "Cycle count must be positive")
  require(integerBits >= 1, "Integer bits must be at least 1 for sign bit and potential integer part")
  val fractionalBits: Int = width - 1 - integerBits
  require(fractionalBits > 0, s"Fractional bits must be positive. Check width ($width) vs integerBits ($integerBits).")

  val io = IO(new Bundle {
    /** Decoupled input for specifying the CORDIC operation mode. When this interface fires, the core latches the inputs and starts the calculation. */
    val mode = Flipped(Decoupled(CORDICModeAll()))
    /**
     * Input X value. Role and limits depend on the selected mode:
     * - `TrigSinCos`: Unused.
     * - `TrigArctanMagnitude`: X-coordinate of the input vector `(x, y)`. For full `atan2(y, x)` functionality, `x` is expected to be positive; pre-processing is required for negative `x`.
     * - `LinearMultiply`: The multiplicand.
     * - `LinearDivide`: The dividend.
     * - `HyperSinhCosh`, `Exponential`: Unused.
     * - `HyperAtanhMagnitude`: X-coordinate of the hyperbolic vector `(x, y)`. Must be positive. The ratio `|y/x|` must be within the convergence range (approx. `< 0.8`).
     * - `NaturalLog`: The input `a` for `ln(a)`. Must be positive. For convergence, `a` is expected to be roughly in the range `(1/9, 9)`.
     */
    val inputX = Input(SInt(width.W))
    /**
     * Input Y value. Role and limits depend on the selected mode:
     * - `TrigSinCos`: Unused.
     * - `TrigArctanMagnitude`: Y-coordinate of the input vector `(x, y)`.
     * - `LinearMultiply`: The multiplier.
     * - `LinearDivide`: The divisor. Must not be zero.
     * - `HyperSinhCosh`, `Exponential`: Unused.
     * - `HyperAtanhMagnitude`: Y-coordinate of the hyperbolic vector `(x, y)`.
     * - `NaturalLog`: Unused.
     */
    val inputY = Input(SInt(width.W))
    /**
     * Input Theta (angle) value. Role and limits depend on the selected mode:
     * - `TrigSinCos`: Input angle `theta` in radians for `cos(theta)` and `sin(theta)`. Must be within the CORDIC convergence range, approximately `[-1.74, +1.74]` radians.
     * - `TrigArctanMagnitude`: Unused.
     * - `LinearMultiply`: Unused.
     * - `LinearDivide`: Unused.
     * - `HyperSinhCosh`, `Exponential`: Input hyperbolic angle `theta` for `cosh(theta)` and `sinh(theta)`. Must be within the convergence range, approximately `[-1.12, +1.12]`.
     * - `HyperAtanhMagnitude`: Unused.
     * - `NaturalLog`: Unused.
     */
    val inputTheta = Input(SInt(width.W))
    /** Decoupled primary output. */
    val output1 = Decoupled(SInt(width.W))
    /** Decoupled secondary output. */
    val output2 = Decoupled(SInt(width.W))
  })

  // Internal state machine
  object State extends ChiselEnum {
    /** Idle state, ready to accept a new CORDIC operation. */
    val sIdle, 
    /** Busy state, performing CORDIC iterations. */
    sBusy, 
    /** Done state, holding the output values until they are read. */
    sDone = Value
  }
  val state = RegInit(State.sIdle)

  // Core CORDIC registers that hold the state during iterations.
  val xReg = Reg(SInt(width.W)) // Holds the x component
  val yReg = Reg(SInt(width.W)) // Holds the y component
  val zReg = Reg(SInt(width.W)) // Holds the angle accumulator (z)
  val iterCount = Reg(UInt(log2Ceil(cycleCount + 1).W))

  // Input buffers and mode register
  val currentOpModeReg = Reg(CORDICModeAll())
  val inputXBuffer = Reg(SInt(width.W))
  val inputYBuffer = Reg(SInt(width.W))
  val inputThetaBuffer = Reg(SInt(width.W))

  // Fixed-point constant for 1.0
  val oneFixed = doubleToFixed(1.0, fractionalBits, width).S(width.W)
  
  // Constants and LUTs for Trigonometric modes
  val trigGainFixed = doubleToFixed(TRIGONOMETRIC_GAIN, fractionalBits, width).S(width.W)
  val invTrigGainFixed = doubleToFixed(1.0 / TRIGONOMETRIC_GAIN, fractionalBits, width).S(width.W)
  val xInitSinCosFixed = if (gainCorrection) trigGainFixed else oneFixed
  val yInitSinCosFixed = 0.S(width.W)
  val atanTrigLut = VecInit(getAtanLUT(fractionalBits, width, cycleCount))

  // Resources for Linear modes (conditionally generated)
  val linearRangeLimitFixed = if (includeLinear) Some(doubleToFixed(LINEAR_RANGE_LIMIT, fractionalBits, width).S(width.W)) else None
  val linearScalingFactorKReg = if (includeLinear) Some(Reg(UInt(log2Ceil(width + 1).W))) else None
  val isDivisorNegativeReg = if (includeLinear) Some(Reg(Bool())) else None
  val linearTermXReg = if (includeLinear) Some(Reg(SInt(width.W))) else None
  val linearTermOneReg = if (includeLinear) Some(Reg(SInt(width.W))) else None

  // Resources for Hyperbolic modes (conditionally generated)
  val hyperbolicShiftExponents = if (includeHyperbolic) Some(getHyperbolicShiftExponents(cycleCount)) else None
  val hyperbolicGainDbl = if (includeHyperbolic && hyperbolicShiftExponents.get.nonEmpty) Some(calculateHyperbolicGainFactor(hyperbolicShiftExponents.get)) else None
  val invHyperbolicGainFixed = if (includeHyperbolic && hyperbolicGainDbl.isDefined && hyperbolicGainDbl.get != 0) Some(doubleToFixed(1.0 / hyperbolicGainDbl.get, fractionalBits, width).S(width.W)) else if (includeHyperbolic) Some(oneFixed) else None
  val xInitHyperbolicFixed = if (includeHyperbolic) Some(if (gainCorrection) invHyperbolicGainFixed.get else oneFixed) else None
  val yInitHyperbolicFixed = 0.S(width.W)
  val atanHyperbolicLut = if (includeHyperbolic) Some(VecInit(getAtanHyperLUT(fractionalBits, width, hyperbolicShiftExponents.get))) else None
  val hyperbolicShiftExponentsVec = if (includeHyperbolic) Some(VecInit(hyperbolicShiftExponents.get.map(_.U(log2Ceil(math.max(1,width)).W)))) else None

  val output1Reg = Reg(SInt(width.W))
  val output2Reg = Reg(SInt(width.W))
  io.output1.bits := output1Reg
  io.output2.bits := output2Reg

  io.mode.ready := false.B
  io.output1.valid := false.B
  io.output2.valid := false.B

  switch(state) {
    is(State.sIdle) {
      // In idle state, the core is ready to accept a new command.
      io.mode.ready := true.B 

      when(io.mode.fire) {
        // A new command has arrived. Latch inputs and configure registers based on the selected mode.
        currentOpModeReg := io.mode.bits
        inputXBuffer := io.inputX
        inputYBuffer := io.inputY
        inputThetaBuffer := io.inputTheta
        
        iterCount := 0.U
        switch(io.mode.bits) {
          is(CORDICModeAll.TrigSinCos) {
            // Rotation mode: computes sin(theta) and cos(theta).
            // Initial values: x = 1/K_trig, y = 0, z = theta.
            // After iterations: x' -> cos(theta), y' -> sin(theta).
            xReg := xInitSinCosFixed
            yReg := yInitSinCosFixed
            zReg := io.inputTheta
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            // Vectoring mode: computes arctan(y/x) and sqrt(x^2 + y^2).
            // Initial values: x = x_in, y = y_in, z = 0.
            // After iterations: z' -> arctan(y_in/x_in), x' -> K_trig * sqrt(x_in^2 + y_in^2).
            xReg := io.inputX
            yReg := io.inputY
            zReg := 0.S(width.W)
          }
          is(CORDICModeAll.LinearMultiply) {
            // Linear CORDIC for multiplication z * x.
            // The multiplier (z) must be scaled to be within the convergence range.
            if (includeLinear) {
              val multiplicand = io.inputX
              val multiplier = io.inputY
              val multiplierAbsUint = multiplier.abs.asUInt
              
              // Find scaling factor K to bring multiplier into range [-LINEAR_RANGE_LIMIT, +LINEAR_RANGE_LIMIT].
              val conditionsMult = VecInit(Seq.tabulate(width + 1) { kIdx =>
                (multiplierAbsUint >> kIdx) <= linearRangeLimitFixed.get.asUInt
              })
              val scalingFactorK = PriorityEncoder(conditionsMult)
              linearScalingFactorKReg.get := scalingFactorK

              // Setup for y' = y + z*x, with y=0 initially.
              xReg := multiplicand
              yReg := 0.S 
              zReg := multiplier >> scalingFactorK
              isDivisorNegativeReg.get := false.B // Not used in multiplication
              linearTermXReg.get := multiplicand
              linearTermOneReg.get := oneFixed
            }
          }
          is(CORDICModeAll.LinearDivide) {
            // Linear CORDIC for division y / x.
            // The dividend (y) must be scaled relative to the divisor (x) for convergence.
            if (includeLinear) {
              val dividend = io.inputX
              val divisor = io.inputY
              isDivisorNegativeReg.get := divisor < 0.S
              val absDivisorUint = divisor.abs.asUInt
              val dividendAbsUint = dividend.abs.asUInt

              // Calculate the upper limit for the scaled dividend.
              val limitProdIntermediate = absDivisorUint.zext * linearRangeLimitFixed.get.asUInt.zext
              val limitForDividendScaledAbsUint = (limitProdIntermediate >> fractionalBits).asUInt

              // Find scaling factor K to bring dividend into range.
              val conditionsDiv = VecInit(Seq.tabulate(width + 1) { kIdx =>
                Mux(limitForDividendScaledAbsUint === 0.U && dividendAbsUint =/= 0.U,
                  (dividendAbsUint >> kIdx) === 0.U,
                  (dividendAbsUint >> kIdx) <= limitForDividendScaledAbsUint)
              })
              val scalingFactorK = PriorityEncoder(conditionsDiv)
              linearScalingFactorKReg.get := scalingFactorK

              // Setup for z' = z + y/x, with z=0 initially.
              xReg := absDivisorUint.asSInt 
              yReg := dividend >> scalingFactorK
              zReg := 0.S
              linearTermXReg.get := absDivisorUint.asSInt
              linearTermOneReg.get := oneFixed
            }
          }
          is(CORDICModeAll.HyperSinhCosh, CORDICModeAll.Exponential) {    
            // Hyperbolic rotation mode: computes sinh(theta) and cosh(theta).
            // Initial values: x = 1/K_hyper, y = 0, z = theta.
            // After iterations: x' -> cosh(theta), y' -> sinh(theta).
            // Exponential e^theta is calculated as cosh(theta) + sinh(theta).
            if (includeHyperbolic) {
              xReg := xInitHyperbolicFixed.get
              yReg := yInitHyperbolicFixed
              zReg := io.inputTheta
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude) {
            // Hyperbolic vectoring mode: computes atanh(y/x) and sqrt(x^2 - y^2).
            // Initial values: x = x_in, y = y_in, z = 0.
            // After iterations: z' -> atanh(y_in/x_in), x' -> K_hyper * sqrt(x_in^2 - y_in^2).
            if (includeHyperbolic) {
              xReg := io.inputX
              yReg := io.inputY
              zReg := 0.S(width.W)
            }
          }
          is(CORDICModeAll.NaturalLog) {
            // Natural log using hyperbolic vectoring mode: ln(a) = 2 * atanh((a-1)/(a+1)).
            // We map input 'a' (io.inputX) to CORDIC inputs x=(a+1) and y=(a-1).
            // Then compute atanh(y/x). The final result from z' must be multiplied by 2.
            if (includeHyperbolic) {
              val one = oneFixed
              val xPlusOne = io.inputX + one
              val xMinusOne = io.inputX - one
              xReg := xPlusOne
              yReg := xMinusOne
              zReg := 0.S(width.W)
            }
          }
        }
        state := State.sBusy
      }
    } 

    is(State.sBusy) {
      // Perform one CORDIC iteration per clock cycle until cycleCount is reached.
      when(iterCount < cycleCount.U) {
        val currentIterIdx = iterCount
        val yShifted = WireDefault(0.S(width.W))
        val xShifted = WireDefault(0.S(width.W))
        val deltaTheta = WireDefault(0.S(width.W))
        val direction = WireDefault(0.S(2.W)) // Rotation direction: +1 or -1

        switch(currentOpModeReg) {
          is(CORDICModeAll.TrigSinCos) {
            // Rotation mode: drive z to zero.
            // d_i = sign(z_i).
            yShifted := (yReg >> currentIterIdx).asSInt
            xShifted := (xReg >> currentIterIdx).asSInt
            deltaTheta := atanTrigLut(currentIterIdx)
            direction := Mux(zReg >= 0.S, 1.S(2.W), -1.S(2.W))
            xReg := xReg - (direction * yShifted)
            yReg := yReg + (direction * xShifted)
            zReg := zReg - (direction * deltaTheta)
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            // Vectoring mode: drive y to zero, accumulate angle in z.
            yShifted := (yReg >> currentIterIdx).asSInt
            xShifted := (xReg >> currentIterIdx).asSInt
            deltaTheta := atanTrigLut(currentIterIdx)
            direction := Mux(yReg >= 0.S, 1.S(2.W), -1.S(2.W)) // d_i = sign(y_i)
            // x_new = x + d*y*2^-i; y_new = y - d*x*2^-i; z_new = z + d*atan(2^-i)
            xReg := xReg + (direction * yShifted)
            yReg := yReg - (direction * xShifted)
            zReg := zReg + (direction * deltaTheta)
          }
          is(CORDICModeAll.LinearMultiply) {
            // Linear rotation mode: drive z to zero by decomposing it.
            // At each step: y_new = y + d*x*2^-i; z_new = z - d*1*2^-i.
            // The terms (x*2^-i) and (1*2^-i) are implemented by shifting registers.
            if (includeLinear) {
              val currentTermX = linearTermXReg.get
              val currentTermOne = linearTermOneReg.get
              val zSign = Mux(zReg > 0.S, 1.S(2.W), Mux(zReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(zSign === 0.S, 1.S(2.W), zSign) // d_i = sign(z_i)
              yReg := yReg + (direction * currentTermX)
              zReg := zReg - (direction * currentTermOne)
              linearTermXReg.get := (currentTermX >> 1).asSInt
              linearTermOneReg.get := (currentTermOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.LinearDivide) {
            // Linear vectoring mode: drive y to zero.
            // At each step: y_new = y - d*x*2^-i; z_new = z + d*1*2^-i
            // Here d_i = -sign(y_i)
            if (includeLinear) {
              val currentTermX = linearTermXReg.get
              val currentTermOne = linearTermOneReg.get
              val ySign = Mux(yReg > 0.S, 1.S(2.W), Mux(yReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(ySign === 0.S, -1.S(2.W), -ySign) // d_i = -sign(y_i)
              yReg := yReg + (direction * currentTermX)
              zReg := zReg - (direction * currentTermOne)
              linearTermXReg.get := (currentTermX >> 1).asSInt
              linearTermOneReg.get := (currentTermOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.HyperSinhCosh, CORDICModeAll.Exponential) { 
            // Hyperbolic rotation mode: drive z to zero.
            // d_i = sign(z_i). Note the + on both x and y updates.
            if (includeHyperbolic) {
              val currentShiftExponent = hyperbolicShiftExponentsVec.get(currentIterIdx)
              yShifted := (yReg >> currentShiftExponent).asSInt
              xShifted := (xReg >> currentShiftExponent).asSInt
              deltaTheta := atanHyperbolicLut.get(currentIterIdx)
              val zSign = Mux(zReg > 0.S, 1.S(2.W), Mux(zReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(zSign === 0.S, -1.S(2.W), zSign) // d_i = sign(z_i)
              xReg := xReg + (direction * yShifted)
              yReg := yReg + (direction * xShifted)
              zReg := zReg - (direction * deltaTheta)
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude, CORDICModeAll.NaturalLog) { 
            // Hyperbolic vectoring mode: drive y to zero.
            // d_i = -sign(y_i). Note the + on both x and y updates.
            if (includeHyperbolic) {
              val currentShiftExponent = hyperbolicShiftExponentsVec.get(currentIterIdx)
              yShifted := (yReg >> currentShiftExponent).asSInt
              xShifted := (xReg >> currentShiftExponent).asSInt
              deltaTheta := atanHyperbolicLut.get(currentIterIdx)
              val ySign = Mux(yReg > 0.S, 1.S(2.W), Mux(yReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(ySign === 0.S, -1.S(2.W), -ySign) // d_i = -sign(y_i)
              xReg := xReg + (direction * yShifted)
              yReg := yReg + (direction * xShifted)
              zReg := zReg - (direction * deltaTheta)
            }
          }
        }
        iterCount := iterCount + 1.U
      }.otherwise {
        // All iterations are complete.
        state := State.sDone
      }
    } 

    is(State.sDone) {
      // Set output values based on the final state of the registers.
      output1Reg := 0.S(width.W) 
      output2Reg := 0.S(width.W)

      switch(currentOpModeReg) {
        is(CORDICModeAll.TrigSinCos) {
          // output1 = cos(theta), output2 = sin(theta)
          output1Reg := xReg
          output2Reg := yReg
        }
        is(CORDICModeAll.TrigArctanMagnitude) {
          // output1 = arctan(y/x), output2 = magnitude
          output1Reg := zReg
          if (gainCorrection) {
            // Correct for CORDIC gain K: (K * magnitude) * (1/K) = magnitude
            val magnitudeFullProd = xReg * trigGainFixed
            output2Reg := (magnitudeFullProd >> fractionalBits.U).asSInt
          } else {
            // Output uncorrected magnitude: K*sqrt(x^2+y^2)
            output2Reg := xReg
          }
        }
        is(CORDICModeAll.LinearMultiply) {
          // output1 = product, undoing the initial scaling
          if (includeLinear) {
            val scalingFactorK = linearScalingFactorKReg.get
            val shiftedProd = yReg << scalingFactorK 
            output1Reg := clamp(shiftedProd, width) 
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.LinearDivide) {
          // output1 = quotient, undoing the initial scaling and handling sign
          if (includeLinear) {
            val scalingFactorK = linearScalingFactorKReg.get
            val shiftedQuotBase = zReg << scalingFactorK 
            val finalQuotientValue = Mux(isDivisorNegativeReg.get, -shiftedQuotBase, shiftedQuotBase)
            output1Reg := clamp(finalQuotientValue, width) 
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.HyperSinhCosh) {
          // output1 = cosh(theta), output2 = sinh(theta)
          if (includeHyperbolic) {
            output1Reg := xReg
            output2Reg := yReg
          }
        }
        is(CORDICModeAll.HyperAtanhMagnitude) {
          // output1 = atanh(y/x), output2 = magnitude
          if (includeHyperbolic) {
            output1Reg := zReg
            if (gainCorrection && invHyperbolicGainFixed.isDefined) { 
              // Correct for hyperbolic gain K_h
              val magnitudeFullProd = xReg * invHyperbolicGainFixed.get
              output2Reg := (magnitudeFullProd >> fractionalBits.U).asSInt
            } else {
              // Output uncorrected magnitude: K_h * sqrt(x^2-y^2)
              output2Reg := xReg 
            }
          }
        }
        is(CORDICModeAll.NaturalLog) {
          // output1 = ln(a) = 2 * z
          if (includeHyperbolic) {
            output1Reg := (zReg << 1).asSInt
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.Exponential) {
          // output1 = e^theta = cosh(theta) + sinh(theta)
          // output2 = e^-theta = cosh(theta) - sinh(theta)
          if (includeHyperbolic) {
            output1Reg := xReg + yReg
            output2Reg := xReg - yReg
          }
        }
      }
      
      // Signal that outputs are valid
      io.output1.valid := true.B
      io.output2.valid := true.B

      // Wait for consumer to be ready before returning to idle
      when(io.output1.ready && io.output2.ready) {
        state := State.sIdle
      }
    } 
  } 
}
