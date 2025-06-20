package CORDIC

import scala.math.pow

class HyperCordicModel(width: Int, cycleCount: Int, integerBits: Int, magnitudeCorrection: Boolean = true) {
  import CordicModelConstants._
  import CordicModelConstants.ModeHyper._ // Use the new Hyperbolic Mode

  private val fractionalBits = width - 1 - integerBits
  
  private val hyperShiftExponents = getHyperbolicShiftExponents(cycleCount)
  private val actualIterations = hyperShiftExponents.length 

  // K_H_TOTAL_ITER_GAIN_DOUBLE is the gain factor if all iterations are performed.
  private val K_H_TOTAL_ITER_GAIN_DOUBLE = calculateHyperbolicGainFactor(hyperShiftExponents)

  private val X_INIT_HYPER = if (magnitudeCorrection) {
    doubleToFixed(1.0 / K_H_TOTAL_ITER_GAIN_DOUBLE, fractionalBits, width)
  } else {
    doubleToFixed(1.0, fractionalBits, width)
  }
  private val Y_INIT_HYPER = BigInt(0)
  
  private val atanHyperLUT = getAtanHyperLUT(fractionalBits, width, hyperShiftExponents)

  private object State extends Enumeration {
    type State = Value
    val Idle, Busy, Done = Value
  }
  import State._

  private var state: State = Idle
  private var x, y, z: BigInt = BigInt(0)
  private var iter: Int = 0
  private var mode: ModeHyper = SinhCosh
  private var effectiveGainStages: Int = 0 // To track iterations where d != 0 for AtanhMagnitudeHyper

  private var startFlag: Boolean = false
  private var targetTheta: BigInt = BigInt(0)
  private var inputX: BigInt = BigInt(0)
  private var inputY: BigInt = BigInt(0)

  private var doneFlag: Boolean = false
  private var coshResult: BigInt = BigInt(0)
  private var sinhResult: BigInt = BigInt(0)
  private var atanhResult: BigInt = BigInt(0)
  private var magnitudeResultHyper: BigInt = BigInt(0)
  private var expResult: BigInt = BigInt(0)
  private var expNegResult: BigInt = BigInt(0)
  private var lnResult: BigInt = BigInt(0)

  def reset(): Unit = {
    state = Idle
    x = BigInt(0)
    y = BigInt(0)
    z = BigInt(0)
    iter = 0
    mode = SinhCosh
    effectiveGainStages = 0 // Reset this new state variable
    startFlag = false
    targetTheta = BigInt(0)
    inputX = BigInt(0)
    inputY = BigInt(0)
    doneFlag = false
    coshResult = BigInt(0)
    sinhResult = BigInt(0)
    atanhResult = BigInt(0)
    magnitudeResultHyper = BigInt(0)
    expResult = BigInt(0)
    expNegResult = BigInt(0)
    lnResult = BigInt(0)
  }

  def setInputs(
    start: Boolean,
    modeIn: ModeHyper,
    theta: BigInt, 
    xIn: BigInt,   
    yIn: BigInt    
  ): Unit = {
    startFlag = start
    mode = modeIn
    targetTheta = theta
    
    if (mode == SinhCosh || mode == Exponential) {
      require(theta <= doubleToFixed(1.1181, fractionalBits, width), "sinh/cosh only converges for theta <= 1.1181")
      require(theta >= doubleToFixed(-1.1181, fractionalBits, width), "sinh/cosh only converges for theta >= -1.1181")
    }
    
    if (mode == AtanhMagnitudeHyper && xIn != 0) {
      val ratio = yIn.doubleValue / xIn.doubleValue
      require(math.abs(ratio) <= 0.8068, s"AtanhMagnitudeHyper only converges for |yIn/xIn| <= 0.8068, got $ratio")
    }

    if (mode == NaturalLog) {
      require(xIn > 0, "Natural logarithm is only defined for positive numbers")
      // For ln(x), we need to compute atanh((x-1)/(x+1))
      // We'll set up x and y for the atanh computation
      val one = doubleToFixed(1.0, fractionalBits, width)
      val xPlusOne = xIn + one
      val xMinusOne = xIn - one
      inputX = xPlusOne  // denominator
      inputY = xMinusOne // numerator
    } else {
      inputX = xIn
      inputY = yIn
    }
  }

  def step(): Unit = {
    state match {
      case Idle =>
        doneFlag = false
        effectiveGainStages = 0 // Reset for the new operation
        if (startFlag) {
          if (mode == AtanhMagnitudeHyper || mode == NaturalLog) {
            x = inputX
            y = inputY
            z = BigInt(0)
          } else { 
            x = X_INIT_HYPER
            y = Y_INIT_HYPER
            z = targetTheta
          }
          iter = 0
          state = Busy
        }
      
      case Busy if iter < actualIterations => 
        val currentShiftExponent = hyperShiftExponents(iter)
        val d = if (mode == AtanhMagnitudeHyper || mode == NaturalLog) {
          if (y.signum == 0) -1 else -y.signum 
        } else { 
          if (z.signum == 0) -1 else z.signum   
        }

        effectiveGainStages = iter + 1
        
        val xShifted = x >> currentShiftExponent
        val yShifted = y >> currentShiftExponent
        
        val x_new = x + d * yShifted 
        val y_new = y + d * xShifted
        
        val deltaZ_val = atanHyperLUT(iter)
        val deltaZ = d * deltaZ_val 
        val z_new = z - deltaZ 

        x = x_new
        y = y_new
        z = z_new
        
        iter += 1
        
        if (iter == actualIterations) {
          state = Done
        }
      
      case Done =>
        if (mode == AtanhMagnitudeHyper || mode == NaturalLog) {
          atanhResult = z
          if (magnitudeCorrection){
            val exponentsForGain = hyperShiftExponents.take(effectiveGainStages)
            val K_eff_gain_double = calculateHyperbolicGainFactor(exponentsForGain)
            val inv_K_eff_double = if (K_eff_gain_double == 0.0 || K_eff_gain_double == 1.0) {
                                     1.0 
                                   } else {
                                     1.0 / K_eff_gain_double
                                   }
            val inv_K_eff_fixed = doubleToFixed(inv_K_eff_double, fractionalBits, width)
            magnitudeResultHyper = clamp((x * inv_K_eff_fixed) >> fractionalBits)
          } else {
            magnitudeResultHyper = clamp(x)
          }

          if (mode == NaturalLog) {
            // ln(x) = 2 * atanh((x-1)/(x+1))
            // Shift left by 1 to multiply by 2
            lnResult = clamp(atanhResult << 1)
          }
        } else { 
          coshResult = clamp(x)
          sinhResult = clamp(y)
          
          if (mode == Exponential) {
            // e^x = cosh(x) + sinh(x)
            expResult = clamp(coshResult + sinhResult)
            // e^-x = cosh(x) - sinh(x)
            expNegResult = clamp(coshResult - sinhResult)
            
          }
        }
        doneFlag = true
        state = Idle
      
      case _ => 
    }
  }

  private def clamp(value: BigInt): BigInt = {
    val max = (BigInt(1) << (width - 1)) - 1
    val min = -(BigInt(1) << (width - 1))
    value.min(max).max(min)
  }

  def done: Boolean = doneFlag
  def cosh: BigInt = coshResult
  def sinh: BigInt = sinhResult
  def atanh: BigInt = atanhResult
  def magnitudeHyper: BigInt = magnitudeResultHyper
  def exp: BigInt = expResult
  def expNeg: BigInt = expNegResult
  def ln: BigInt = lnResult
}
