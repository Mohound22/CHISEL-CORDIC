error id: file://<WORKSPACE>/src/main/scala/CORDIC/scalaLinearModelIterable.scala:local1
file://<WORKSPACE>/src/main/scala/CORDIC/scalaLinearModelIterable.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol local1
empty definition using fallback
non-local guesses:

offset: 4157
uri: file://<WORKSPACE>/src/main/scala/CORDIC/scalaLinearModelIterable.scala
text:
```scala
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
    state match {
      case Idle =>
        doneFlag = false
        productResult = BigInt(0) // Clear previous results
        quotientResult = BigInt(0)
        if (startFlag) {
          iter = 0
          if (currentMode == Multiply) {
            x = inputA       // Multiplicand
            y = BigInt(0)    // Accumulator for product
            z = inputB       // Multiplier (driven to 0)
          } else { // Divide
            x = inputB       // Divisor
            y = inputA       // Dividend (driven to 0)
            z = BigInt(0)    // Accumulator for quotient
          }
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
        val termXShifted = x >> 1 // x_i * 2^(-i)
        val termOneShifted = ONE_FIXED >> shiftAmount // 1.0 * 2^(-i) in fixed point

        val y_new = y + direction * termXShifted // y_i+1 = y_i + d_i * x_i * 2^(-i)
        
          z - direction * termOneShifted
        } else { // Divide: z_i+1 = z_i - d_i * 2^(-i) where d = -sign(y_i)
          z - di@@rection * termOneShifted // This accumulates quotient: z + sign(y) * (1.0>>iter)
        }
        
        x = termXShifted
        y = y_new
        z = z_new
        
        iter += 1
        
        if (iter == cycleCount) {
          state = Done
        }
      
      case Done =>
        if (currentMode == Multiply) {
          productResult = clamp(y)
        } else { // Divide
          quotientResult = clamp(z)
        }
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: 