package CORDIC

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CordicModelConstants._ // Assuming this is correctly set up from the main CORDIC package
import CordicModelConstants.ModeLinear._ // Import the Linear CORDIC modes
import scala.math.pow

class LinearCordicModelTest extends AnyFlatSpec with Matchers {
  // Test parameters
  val width = 32 // Increased width for better precision with multiplication/division
  val fractionalBits = 16 // Increased fractional bits
  val integerBits = width - 1 - fractionalBits // sign bit, integerBits, fractionalBits
  val testCycles = 20 // Number of iterations, ensure enough for convergence for the chosen fractionalBits
  val precision = 0.001 // Fixed-point precision tolerance

  // Instantiate model with test parameters
  val model = new LinearCordicModel(
    width = width,
    cycleCount = testCycles,
    integerBits = integerBits
  )

  // Helper conversions
  def doubleToFixed(x: Double): BigInt =
    CordicModelConstants.doubleToFixed(x, fractionalBits, width)

  def fixedToDouble(x: BigInt): Double = {
    x.toDouble / pow(2, fractionalBits)
  }

  // Helper function to run test case
  def runTest(
      inputA: Double,
      inputB: Double,
      mode: ModeLinear
  ): Unit = {
    model.reset()
    model.setInputs(
      start = true,
      modeIn = mode,
      a = doubleToFixed(inputA),
      b = doubleToFixed(inputB)
    )
    var safetyCounter = 0
    while (!model.done && safetyCounter < testCycles + 5) { // Added safety counter
      model.step()
      safetyCounter += 1
    }
    if (!model.done) {
        //println(s"Warning: Model did not reach 'done' state for mode $mode, A=$inputA, B=$inputB after $safetyCounter steps.")
    }
  }

  // --- Multiplication Tests ---
  "LinearCordicModel" should "calculate multiplication for positive numbers" in {
    runTest(inputA = 2.5, inputB = 3.0, mode = Multiply)
    fixedToDouble(model.product) should be(7.5 +- precision)
  }

  it should "calculate multiplication with one negative number" in {
    runTest(inputA = -2.5, inputB = 3.0, mode = Multiply)
    fixedToDouble(model.product) should be(-7.5 +- precision)
    runTest(inputA = 2.5, inputB = -3.0, mode = Multiply)
    fixedToDouble(model.product) should be(-7.5 +- precision)
  }

  it should "calculate multiplication with two negative numbers" in {
    runTest(inputA = -2.5, inputB = -3.0, mode = Multiply)
    fixedToDouble(model.product) should be(7.5 +- precision)
  }

  it should "calculate multiplication with zero" in {
    runTest(inputA = 2.5, inputB = 0.0, mode = Multiply)
    fixedToDouble(model.product) should be(0.0 +- precision)
    runTest(inputA = 0.0, inputB = 3.0, mode = Multiply)
    fixedToDouble(model.product) should be(0.0 +- precision)
  }
  
  it should "calculate multiplication with small numbers" in {
    runTest(inputA = 0.125, inputB = 0.5, mode = Multiply)
    fixedToDouble(model.product) should be (0.0625 +- precision)
  }

  it should "calculate multiplication with one number being 1.0" in {
    runTest(inputA = 5.75, inputB = 1.0, mode = Multiply)
    fixedToDouble(model.product) should be (5.75 +- precision)
  }

  // --- Division Tests ---
  it should "calculate division for positive numbers" in {
    runTest(inputA = 7.5, inputB = 2.5, mode = Divide) // A/B = 7.5 / 2.5 = 3.0
    fixedToDouble(model.quotient) should be(3.0 +- precision)
  }

  it should "calculate division with negative dividend" in {
    runTest(inputA = -7.5, inputB = 2.5, mode = Divide)
    fixedToDouble(model.quotient) should be(-3.0 +- precision)
  }

  it should "calculate division with negative divisor" in {
    runTest(inputA = 7.5, inputB = -2.5, mode = Divide)
    fixedToDouble(model.quotient) should be(-3.0 +- precision)
  }

  it should "calculate division with both negative numbers" in {
    runTest(inputA = -7.5, inputB = -2.5, mode = Divide)
    fixedToDouble(model.quotient) should be(3.0 +- precision)
  }

  it should "calculate division with zero dividend" in {
    runTest(inputA = 0.0, inputB = 2.5, mode = Divide)
    fixedToDouble(model.quotient) should be(0.0 +- precision)
  }
  
  it should "calculate division resulting in a fraction" in {
    runTest(inputA = 1.0, inputB = 4.0, mode = Divide) // 1.0 / 4.0 = 0.25
    fixedToDouble(model.quotient) should be (0.25 +- precision)
  }

  it should "calculate division where dividend is smaller than divisor" in {
    runTest(inputA = 2.0, inputB = 8.0, mode = Divide) // 2.0 / 8.0 = 0.25
    fixedToDouble(model.quotient) should be (0.25 +- precision)
  }

  it should "calculate division by 1.0" in {
    runTest(inputA = 5.75, inputB = 1.0, mode = Divide)
    fixedToDouble(model.quotient) should be (5.75 +- precision)
  }

  // Multiple operations test
  it should "handle consecutive operations correctly" in {
    // First operation - Multiply
    runTest(inputA = 2.0, inputB = 4.0, mode = Multiply)
    fixedToDouble(model.product) should be(8.0 +- precision)

    // Second operation - Divide
    runTest(inputA = fixedToDouble(model.product), inputB = 2.0, mode = Divide) // 8.0 / 2.0 = 4.0
    fixedToDouble(model.quotient) should be(4.0 +- precision)
  }

  // Error condition tests
  it should "not complete when not started" in {
    model.reset()
    model.done should be(false)
    model.step() // Step without start
    model.done should be(false)
  }
  
  it should "throw an error for division by zero" in {
    assertThrows[IllegalArgumentException] {
      model.setInputs(start = true, modeIn = Divide, a = doubleToFixed(5.0), b = doubleToFixed(0.0))
    }
  }
} 