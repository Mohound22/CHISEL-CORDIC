error id: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable%20copy.scala:CORDIC/CordicModelConstants.doubleToFixed().
file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable%20copy.scala
empty definition using pc, found symbol in pc: CORDIC/CordicModelConstants.doubleToFixed().
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -CordicModelConstants.doubleToFixed.
	 -CordicModelConstants.doubleToFixed#
	 -CordicModelConstants.doubleToFixed().
	 -doubleToFixed.
	 -doubleToFixed#
	 -doubleToFixed().
	 -scala/Predef.doubleToFixed.
	 -scala/Predef.doubleToFixed#
	 -scala/Predef.doubleToFixed().
offset: 303
uri: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable%20copy.scala
text:
```scala
package CORDIC

import scala.math.{atan, pow, round}

class TrigCordicModel(width: Int, cycleCount: Int, integerBits: Int) {
  import CordicModelConstants._

  private val fractionalBits = width - 1 - integerBits
  private val K = doubleToFixed(CORDIC_K, fractionalBits, width)
  private val X_INIT = do@@ubleToFixed(1.0, fractionalBits, width)
  private val Y_INIT = BigInt(0)
  private val atanLUT = getAtanLUT(fractionalBits, width, cycleCount)

  // State machine
  private sealed trait State
  private case object Idle extends State
  private case object Busy extends State
  private case object Done extends State

  private var state: State = Idle
  private var x, y, z: BigInt = BigInt(0)
  private var iter: Int = 0
  private var modeArctan: Boolean = false

  // Inputs
  private var startFlag: Boolean = false
  private var targetTheta: BigInt = BigInt(0)
  private var inputX: BigInt = BigInt(0)
  private var inputY: BigInt = BigInt(0)

  // Outputs
  private var doneFlag: Boolean = false
  private var cosResult: BigInt = BigInt(0)
  private var sinResult: BigInt = BigInt(0)
  private var arctanResult: BigInt = BigInt(0)

  def reset(): Unit = {
    state = Idle
    x = BigInt(0)
    y = BigInt(0)
    z = BigInt(0)
    iter = 0
    modeArctan = false
    startFlag = false
    targetTheta = BigInt(0)
    inputX = BigInt(0)
    inputY = BigInt(0)
    doneFlag = false
    cosResult = BigInt(0)
    sinResult = BigInt(0)
    arctanResult = BigInt(0)
  }

  def setInputs(
    start: Boolean,
    arctanMode: Boolean,
    theta: BigInt,
    xIn: BigInt,
    yIn: BigInt
  ): Unit = {
    startFlag = start
    modeArctan = arctanMode
    targetTheta = theta
    inputX = xIn
    inputY = yIn
  }

  def step(): Unit = {
    state match {
      case Idle =>
        doneFlag = false
        if (startFlag) {
          if (modeArctan) {
            x = inputX
            y = inputY
            z = BigInt(0)
          } else {
            x = X_INIT
            y = Y_INIT
            z = targetTheta
          }
          iter = 0
          state = Busy
        }
      
      case Busy if iter < cycleCount =>
        val shift = iter
        val d = if (modeArctan) {
          if (y >= 0) 1 else -1
        } else {
          if (z >= 0) 1 else -1
        }
        
        val xShifted = x >> shift
        val yShifted = y >> shift
        
        val (xNew, yNew) = if (modeArctan) {
          (x + d * yShifted, y - d * xShifted)
        } else {
          (x - d * yShifted, y + d * xShifted)
        }
        
        val deltaZ = d * atanLUT(iter)
        z += (if (modeArctan) deltaZ else -deltaZ)
        
        x = xNew
        y = yNew
        iter += 1
        
        if (iter == cycleCount) {
          state = Done
        }
      
      case Done =>
        if (modeArctan) {
          arctanResult = z
        } else {
          val cosUnscaled = x
          val sinUnscaled = y
          
          cosResult = clamp((x * K) >> fractionalBits)
          sinResult = clamp((y * K) >> fractionalBits)
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

  // Output accessors
  def done: Boolean = doneFlag
  def cos: BigInt = cosResult
  def sin: BigInt = sinResult
  def arctan: BigInt = arctanResult
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: CORDIC/CordicModelConstants.doubleToFixed().