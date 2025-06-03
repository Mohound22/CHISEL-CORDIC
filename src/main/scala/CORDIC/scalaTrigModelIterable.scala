package CORDIC

import scala.math.{atan, pow, round, log}

object CordicModelConstants {
  val TRIG_CORDIC_K: Double = 0.6072529350088813 // CORDIC gain


  def atanh(x: Double): Double = { // Scala.math does not have atanh, so we define it here
    require(x > -1.0 && x < 1.0, "atanh(x) is only defined for -1 < x < 1")
    0.5 * log((1.0 + x) / (1.0 - x))
  }

  def doubleToFixed(x: Double, fractionalBits: Int, width: Int): BigInt = {
    val scaled = BigDecimal(x) * BigDecimal(BigInt(1) << fractionalBits)
    val rounded = scaled.setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt
    val maxVal = (BigInt(1) << (width - 1)) - 1
    val minVal = -(BigInt(1) << (width - 1))
    rounded.max(minVal).min(maxVal)
  }

  def getAtanLUT(fractionalBits: Int, width: Int, numEntries: Int): Seq[BigInt] = {
    (0 until numEntries).map { i =>
      val angle = atan(pow(2.0, -i))
      doubleToFixed(angle, fractionalBits, width)
    }
  }

  def getAtanHyperLUT(fractionalBits: Int, width: Int, shiftExponents: Seq[Int]): Seq[BigInt] = {
    shiftExponents.map { exp =>
      val angle = atanh(pow(2.0, -exp))
      doubleToFixed(angle, fractionalBits, width)
    }
  }

  // New enum for CORDIC modes
  object Mode extends Enumeration {
    type Mode = Value
    val SinCos, ArctanMagnitude = Value
  }

  // New enum for Hyperbolic CORDIC modes
  object ModeHyper extends Enumeration {
    type ModeHyper = Value
    val SinhCosh, AtanhMagnitudeHyper = Value
  }

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
    
    //println(s"Final sequence of ${exponents.length} exponents: ${exponents.mkString(", ")}\n")
    exponents.toSeq
  }

  // Calculates K_h = Product_i sqrt(1 - 2^(-2*s_i))
  def calculateHyperbolicGainFactor(shiftExponents: Seq[Int]): Double = { //WORKS
    var product = 1.0
    for (exp <- shiftExponents) {
      val term = pow(2.0, -exp)
      product *= scala.math.sqrt(1.0 - term * term)
    }
    //println(s"K_h: $product")
    product // This is K_h
  }
}

class TrigCordicModel(width: Int, cycleCount: Int, integerBits: Int, magnitudeCorrection: Boolean = true) {
  import CordicModelConstants._
  import CordicModelConstants.Mode._

  private val fractionalBits = width - 1 - integerBits
  private val K = doubleToFixed(TRIG_CORDIC_K, fractionalBits, width)
  private val X_INIT = if (magnitudeCorrection) {
    doubleToFixed(TRIG_CORDIC_K, fractionalBits, width)
  } else {
    doubleToFixed(1.0, fractionalBits, width)
  }
  private val Y_INIT = BigInt(0)
  private val atanLUT = getAtanLUT(fractionalBits, width, cycleCount)

  // State machine
  private object State extends Enumeration {
    type State = Value
    val Idle, Busy, Done = Value
  }

  import State._

  private var state: State = Idle
  private var x, y, z: BigInt = BigInt(0)
  private var iter: Int = 0
  private var mode: Mode = SinCos

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
  private var magnitudeResult: BigInt = BigInt(0)

  def reset(): Unit = {
    state = Idle
    x = BigInt(0)
    y = BigInt(0)
    z = BigInt(0)
    iter = 0
    mode = SinCos
    startFlag = false
    targetTheta = BigInt(0)
    inputX = BigInt(0)
    inputY = BigInt(0)
    doneFlag = false
    cosResult = BigInt(0)
    sinResult = BigInt(0)
    arctanResult = BigInt(0)
    magnitudeResult = BigInt(0)
  }

  def setInputs(
    start: Boolean,
    modeIn: Mode,
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
        if (startFlag) {
          if (mode == ArctanMagnitude) {
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
        val d = if (mode == ArctanMagnitude) {
          if (y >= 0) 1 else -1
        } else {
          if (z >= 0) 1 else -1
        }
        
        val xShifted = x >> shift
        val yShifted = y >> shift
        
        val (xNew, yNew) = if (mode == ArctanMagnitude) {
          (x + d * yShifted, y - d * xShifted)
        } else {
          (x - d * yShifted, y + d * xShifted)
        }
        
        val deltaZ = d * atanLUT(iter)
        z += (if (mode == ArctanMagnitude) deltaZ else -deltaZ)
        
        x = xNew
        y = yNew
        iter += 1
        
        if (iter == cycleCount) {
          state = Done
        }
      
      case Done =>
        if (mode == ArctanMagnitude) {
          arctanResult = z
          if (magnitudeCorrection) {
            magnitudeResult = clamp((x * K) >> fractionalBits)
          } else {
            magnitudeResult = clamp(x)
          }
        } else {
          cosResult = clamp(x)
          sinResult = clamp(y)
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
  def magnitude: BigInt = magnitudeResult
}