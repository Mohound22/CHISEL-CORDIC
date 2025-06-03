error id: file://<WORKSPACE>/src/main/scala/CORDIC/scalaHyperModelIterable.scala:scala/Int#`unary_-`().
file://<WORKSPACE>/src/main/scala/CORDIC/scalaHyperModelIterable.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol scala/Int#`unary_-`().
empty definition using fallback
non-local guesses:

offset: 4290
uri: file://<WORKSPACE>/src/main/scala/CORDIC/scalaHyperModelIterable.scala
text:
```scala
package CORDIC

import scala.math.{pow, round} // sinh, cosh, atanh are not directly used here but pow and round might be.

class HyperCordicModel(width: Int, cycleCount: Int, integerBits: Int, magnitudeCorrection: Boolean = true) {
  import CordicModelConstants._
  import CordicModelConstants.ModeHyper._ // Use the new Hyperbolic Mode

  // Print K_H_ACTUAL_GAIN_DOUBLE for debugging (can be removed later)
  // println(s"[HyperCordicModel Init] cycleCount: $cycleCount")
  private val fractionalBits = width - 1 - integerBits
  
  private val hyperShiftExponents = getHyperbolicShiftExponents(cycleCount)
  // println(s"[HyperCordicModel Init] hyperShiftExponents: ${hyperShiftExponents.mkString(",")}")
  private val actualIterations = hyperShiftExponents.length 
  // println(s"[HyperCordicModel Init] actualIterations: $actualIterations")

  // This K_H_ACTUAL_GAIN_DOUBLE is the gain if all iterations complete with d!=0 for z (rotation) or y (vectoring)
  // For vectoring, if y becomes 0 early, this full gain is not applicable to x.
  private val K_H_TOTAL_ITER_GAIN_DOUBLE = calculateHyperbolicGainFactor(hyperShiftExponents)
  // println(s"[HyperCordicModel Init] K_H_TOTAL_ITER_GAIN_DOUBLE: $K_H_TOTAL_ITER_GAIN_DOUBLE")
  // println(s"[HyperCordicModel Init] 1.0 / K_H_TOTAL_ITER_GAIN_DOUBLE: ${1.0 / K_H_TOTAL_ITER_GAIN_DOUBLE}")

  private val X_INIT_HYPER = if (magnitudeCorrection) {
    doubleToFixed(1.0 / K_H_TOTAL_ITER_GAIN_DOUBLE, fractionalBits, width)
  } else {
    doubleToFixed(1.0, fractionalBits, width)
  }
  private val Y_INIT_HYPER = BigInt(0)
  
  private val atanHyperLUT_val = getAtanHyperLUT(fractionalBits, width, hyperShiftExponents)

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

  private def debugFixedToDouble(fixedVal: BigInt, fBits: Int): Double = {
    fixedVal.toDouble / (1L << fBits).toDouble
  }

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
    inputX = xIn
    inputY = yIn
  }

  def step(): Unit = {
    state match {
      case Idle =>
        doneFlag = false
        effectiveGainStages = 0 // Reset for the new operation
        if (startFlag) {
          if (mode == AtanhMagnitudeHyper) {
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
        val d = if (mode == AtanhMagnitudeHyper) {
          if (y.signum == 0) -1 else -y.signum 
        } else { 
          if (z.signum == 0) -1 else z.signum   
        }

        if (d != 0) { // This iteration contributes to gain changes for x,y if d is non-zero
            effectiveGainStages = iter + 1 
        }
        
        val x_prev = x 
        val y_prev = y 
        val z_prev = z 

        val xShifted = x >> currentShiftExponent else x << -curren@@tShiftExponent
        val yShifted = if (currentShiftExponent >= 0) y >> currentShiftExponent else y << -currentShiftExponent
        
        val x_new = x + d * yShifted 
        val y_new = y + d * xShifted
        
        val deltaZ_val = atanHyperLUT_val(iter)
        val deltaZ = d * deltaZ_val 
        val z_new = z - deltaZ 

        // Remove or conditionalize detailed iterative prints once fix is verified
          println(s"[Iter $iter] mode=$mode, s_i=$currentShiftExponent, d=$d, effectiveGainStages=$effectiveGainStages")
          println(s"  x_prev=$x_prev (${debugFixedToDouble(x_prev, fractionalBits)}) y_prev=$y_prev (${debugFixedToDouble(y_prev, fractionalBits)}) z_prev=$z_prev (${debugFixedToDouble(z_prev, fractionalBits)}) ")
          println(s"  xShifted=$xShifted (${debugFixedToDouble(xShifted, fractionalBits)}) yShifted=$yShifted (${debugFixedToDouble(yShifted, fractionalBits)}) deltaZ_lut=${atanHyperLUT_val(iter)} (${debugFixedToDouble(atanHyperLUT_val(iter), fractionalBits)}) ")
          println(s"  x_new=$x_new (${debugFixedToDouble(x_new, fractionalBits)}) y_new=$y_new (${debugFixedToDouble(y_new, fractionalBits)}) z_new=$z_new (${debugFixedToDouble(z_new, fractionalBits)}) deltaZ=$deltaZ")
        
        x = x_new
        y = y_new
        z = z_new
        
        iter += 1
        
        if (iter == actualIterations) {
          state = Done
        }
      
      case Done =>
        if (mode == AtanhMagnitudeHyper) {
          atanhResult = z
          if (magnitudeCorrection){
            val exponentsForGain = hyperShiftExponents.take(effectiveGainStages)
            println(s"[Done State Correction] effectiveGainStages: $effectiveGainStages, using ${exponentsForGain.length} exponents for gain.")
            
            val K_eff_gain_double = calculateHyperbolicGainFactor(exponentsForGain)
            println(s"[Done State Correction] K_eff_gain_double: $K_eff_gain_double")

            val inv_K_eff_double = if (K_eff_gain_double == 0.0 || K_eff_gain_double == 1.0) {
                                     // If gain is 0 (error) or 1 (no scaling, e.g. 0 stages), inverse is 1.
                                     1.0 
                                   } else {
                                     1.0 / K_eff_gain_double
                                   }
            println(s"[Done State Correction] inv_K_eff_double: $inv_K_eff_double")
            val inv_K_eff_fixed = doubleToFixed(inv_K_eff_double, fractionalBits, width)
            println(s"[Done State Correction] x_final_iter_fixed: $x, inv_K_eff_fixed: $inv_K_eff_fixed")

            magnitudeResultHyper = clamp((x * inv_K_eff_fixed) >> fractionalBits)
            println(s"[Done State Correction] magnitudeResultHyper_fixed: $magnitudeResultHyper (${debugFixedToDouble(magnitudeResultHyper, fractionalBits)})")
          } else {
            magnitudeResultHyper = clamp(x)
          }
        } else { 
          coshResult = clamp(x)
          sinhResult = clamp(y)
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
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 