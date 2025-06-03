package CORDIC

import scala.math.{pow, round, log} // log might not be needed, but keeping for consistency for now

// CordicModelConstants is now defined in scalaTrigModelIterable.scala (or another shared file)
// and includes ModeLinear. We will import it directly.

class LinearCordicModel(width: Int, cycleCount: Int, integerBits: Int) {
  import CordicModelConstants._ // Import all constants from the shared object
  import CordicModelConstants.ModeLinear._ // Import specific linear mode

  private val fractionalBits = width - 1 - integerBits
  
  // K_L = 1.0 for Linear CORDIC, so no K constant needed for scaling inputs/outputs due to CORDIC gain.
  // We do need a fixed-point representation of 1.0 for z updates.
  private val ONE_FIXED: BigInt = doubleToFixed(1.0, fractionalBits, width)
  // Approximate range limit for z_cordic (multiplier) or quotient. Sum 2^-i approaches 2.0.
  // Use a slightly smaller value for safety margin in fixed point.
  private val CORDIC_RANGE_LIMIT_R: Double = 1.99 
  private val CORDIC_RANGE_LIMIT_R_FIXED: BigInt = doubleToFixed(CORDIC_RANGE_LIMIT_R, fractionalBits, width)

  private def fixedToDouble(f: BigInt): Double = f.toDouble / scala.math.pow(2, fractionalBits) // For debugging

  // State machine
  private object State extends Enumeration {
    type State = Value
    val Idle, Busy, Done = Value
  }
  import State._

  private var state: State = Idle
  private var x, y, z: BigInt = BigInt(0) // x: constant operand, y: accumulator/dividend, z: multiplier/quotient
  private var iter: Int = 0
  private var currentMode: ModeLinear = Multiply
  private var currentScalingFactorK: Int = 0 // For pre/post-scaling
  private var originalDivisorIsNegative: Boolean = false // New flag

  // Inputs
  private var startFlag: Boolean = false
  private var inputA: BigInt = BigInt(0) // For multiplication: A in A*B; For division: Dividend A in A/B
  private var inputB: BigInt = BigInt(0) // For multiplication: B in A*B; For division: Divisor B in A/B

  // Outputs
  private var doneFlag: Boolean = false
  private var productResult: BigInt = BigInt(0)
  private var quotientResult: BigInt = BigInt(0)

  def reset(): Unit = {
    state = Idle
    x = BigInt(0)
    y = BigInt(0)
    z = BigInt(0)
    iter = 0
    currentMode = Multiply
    currentScalingFactorK = 0
    originalDivisorIsNegative = false // Reset flag
    startFlag = false
    inputA = BigInt(0)
    inputB = BigInt(0)
    doneFlag = false
    productResult = BigInt(0)
    quotientResult = BigInt(0)
  }

  def setInputs(
    start: Boolean,
    modeIn: ModeLinear,
    a: BigInt, // For Multiply: Multiplicand; For Divide: Dividend
    b: BigInt  // For Multiply: Multiplier;  For Divide: Divisor
  ): Unit = {
    startFlag = start
    currentMode = modeIn
    inputA = a
    inputB = b

    if (currentMode == Divide) {
      require(inputB != 0, "Division by zero is not allowed.")
      // Add more specific range/overflow checks based on width and fractionalBits if necessary
    }
    // Add checks for multiplication overflow potential if desired, e.g., based on integerBits of inputs vs output.
  }

  def step(): Unit = {
    //println(s"Current state: $state, iter: $iter") // General state print
    state match {
      case Idle =>
        doneFlag = false
        productResult = BigInt(0)
        quotientResult = BigInt(0)
        currentScalingFactorK = 0 // Reset scaling factor
        originalDivisorIsNegative = false // Reset here before use

        if (startFlag) {
          iter = 0
          var actualInputA = inputA
          var actualInputB = inputB

          if (currentMode == Multiply) {
            // Scale inputB (multiplier) to be within [-R, R] for z_cordic
            var tempBabs = actualInputB.abs
            while (tempBabs > CORDIC_RANGE_LIMIT_R_FIXED && currentScalingFactorK < width) {
              tempBabs >>= 1
              currentScalingFactorK += 1
            }
            val scaledB = actualInputB >> currentScalingFactorK // Arithmetic shift preserves sign

            x = actualInputA       // Multiplicand (A)
            y = BigInt(0)        // Accumulator for product
            z = scaledB          // Scaled Multiplier (B')
            //println(s"[Idle->Busy Mult] A=${fixedToDouble(actualInputA)}, B=${fixedToDouble(actualInputB)}, B_scaled=${fixedToDouble(z)}, k=$currentScalingFactorK")
          } else { // Divide A/B
            // Scale inputA (dividend) such that expected quotient (A_scaled / B) is within [-R, R]
            // (A_scaled / B) < R  => A_scaled < R * B => (A >> k) < R * B
            // A_abs >> k < R_fixed * B_abs / 2^fb
            // (A_abs * 2^fb) >> k < R_fixed * B_abs  (Incorrect scaling logic here previously)
            // We want (A / 2^k) / B approx Q_target in [-R,R]. So A / (B * 2^k) approx Q_target.
            // Or, (A >> k) / B.  So |A >> k| < |B| * R
            originalDivisorIsNegative = actualInputB < 0
            val absDivisor = actualInputB.abs

            var tempAabs = actualInputA.abs
            // Calculate limit for |A_scaled| = |B| * R
            // This multiplication needs care to avoid overflow before the shift if B is large
            // (B_abs * R_fixed) can be B_double*2^fb * R_double*2^fb = B_double*R_double*2^(2fb)
            // So we need to shift by fractionalBits after multiplication
            val limitForAScaledAbs = if (absDivisor == 0) BigInt(0) else (absDivisor * CORDIC_RANGE_LIMIT_R_FIXED) >> fractionalBits
            
            currentScalingFactorK = 0 // Ensure k is reset for division scaling calculation
            while (tempAabs > limitForAScaledAbs && currentScalingFactorK < width && limitForAScaledAbs >=0) { // limit can be 0 if B is 0, but pre-check for B!=0
                 if (limitForAScaledAbs == 0 && tempAabs > 0) { // If B is very small, R*B becomes 0 fixed, but A isn't. Scale A down.
                    // This case means B is so small that R*B is < 1 LSB for fixed point.
                    // Any non-zero A will be > limitForAScaledAbs.
                    // This can happen if B is e.g. 1 fixed, R_fixed is 130k, (1*130k)>>16 is 1.
                    // If B fixed is 0 after scaling (very small B), this logic is tricky.
                    // But B itself (divisor) is not scaled.
                 }
                tempAabs >>= 1
                currentScalingFactorK += 1
            }
            val scaledA = actualInputA >> currentScalingFactorK

            x = absDivisor // Use absolute value of divisor for CORDIC x
            y = scaledA          // Scaled Dividend (A')
            z = BigInt(0)        // Accumulator for quotient
            //println(s"[Idle->Busy Div] A=${fixedToDouble(actualInputA)}, B=${fixedToDouble(actualInputB)}, A_scaled=${fixedToDouble(y)}, k=$currentScalingFactorK, LimitForAscaledAbs=${fixedToDouble(limitForAScaledAbs)}")
          }
          //println(s"[Idle->Busy] Mode: $currentMode Initial x=${fixedToDouble(x)}, y=${fixedToDouble(y)}, z=${fixedToDouble(z)} (fixed: x=$x, y=$y, z=$z)")
          state = Busy
        }
      
      case Busy if iter < cycleCount =>
        val shiftAmount = iter 
        
        // Simplified d for clarity, assuming BigInt.signum returns 0 for 0, 1 for positive, -1 for negative.
        val direction = if (currentMode == Divide) {
          if (y == 0) -1 else -y.signum // If y is already 0, continue accumulating z. Arbitrary d for y part.
        } else { // Multiply
          if (z == 0) 1 else z.signum   // If z is already 0, y should be stable. Arbitrary d for z part.
        }

        // PLEASE MAKE SURE TO MAKE THESE SHIFTS A SINGLE SHIFT IN THE CHISEL CODE THAT JUST ACCUMULATES EACH CYCLE, NO NEED FOR VARIABLE SHIFTS
        val termXShifted = x >> shiftAmount // x_i * 2^(-i)
        val termOneShifted = ONE_FIXED >> shiftAmount // 1.0 * 2^(-i) in fixed point

        val y_new = y + direction * termXShifted // y_i+1 = y_i + d_i * x_i * 2^(-i)
        
        val z_new = z - direction * termOneShifted
        
        y = y_new
        z = z_new

        println(s"[Busy] Iter $iter. x=${fixedToDouble(x)}, y=${fixedToDouble(y)}, z=${fixedToDouble(z)} (fixed: x=$x, y=$y, z=$z)")
        
        iter += 1
        
        if (iter == cycleCount) {
          state = Done
        }
      
      case Done =>
        var rawResult: BigInt = 0
        if (currentMode == Multiply) {
          rawResult = y // This is A * B_scaled
          productResult = clamp(rawResult << currentScalingFactorK)
        } else { // Divide
          rawResult = z // This is A_scaled / B
          var finalQuotient = rawResult << currentScalingFactorK
          if (originalDivisorIsNegative) {
            finalQuotient = -finalQuotient
          }
          quotientResult = clamp(finalQuotient)
        }
        //println(s"[Done] Mode: $currentMode, RawRes=${fixedToDouble(rawResult)}, k=$currentScalingFactorK, FinalProd=${fixedToDouble(productResult)}, FinalQuot=${fixedToDouble(quotientResult)}")
        doneFlag = true
        state = Idle
      
      case _ => // Should not happen
        state = Idle
    }
  }

  private def clamp(value: BigInt): BigInt = {
    val maxVal = (BigInt(1) << (width - 1)) - 1
    val minVal = -(BigInt(1) << (width - 1))
    value.min(maxVal).max(minVal)
  }

  // Output accessors
  def done: Boolean = doneFlag
  def product: BigInt = productResult
  def quotient: BigInt = quotientResult
}
