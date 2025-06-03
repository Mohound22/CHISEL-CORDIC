file://<WORKSPACE>/src/test/scala/CORDIC/scalaHyperTestIterable.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:

offset: 410
uri: file://<WORKSPACE>/src/test/scala/CORDIC/scalaHyperTestIterable.scala
text:
```scala
package CORDIC

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CordicModelConstants._
import CordicModelConstants.Mode._
import scala.math.{Pi, sqrt}

class CordicHyperModelTest extends AnyFlatSpec with Matchers {
  // Test parameters
  val width = 16
  val fractionalBits = 12
  val integerBits = 3
  val testCycles = 14
  val precision = 0.001 // Fixed-point p@@recision tolerance
  
  // Instantiate model with test parameters
  val model = new TrigCordicModel(
    width = width,
    cycleCount = testCycles,
    integerBits = integerBits
  )

  // Helper conversions
  def doubleToFixed(x: Double): BigInt = 
    CordicModelConstants.doubleToFixed(x, fractionalBits, width)
    
  def fixedToDouble(x: BigInt): Double = {
    // Handle the case where very small values get rounded to -1 instead of 0
    if (x == -1) 0.0
    else x.toDouble / (1 << fractionalBits).toDouble
  }

  // Calculate CORDIC gain for verification
  def calculateAn(cycles: Int): Double =
    (0 until cycles).map(i => math.sqrt(1 + math.pow(2, -2*i))).product

  val An = calculateAn(testCycles)
  val K = 1.0 / An  // CORDIC scaling factor

  // Helper function to run test case
  def runTest(angle: Double = 0.0, x: Double = 0.0, y: Double = 0.0, 
              mode: Mode = SinCos): Unit = {
    model.reset()
    model.setInputs(
      start = true,
      modeIn = mode,
      theta = if (mode == SinCos) doubleToFixed(angle) else BigInt(0),
      xIn = if (mode == ArctanMagnitude) doubleToFixed(x) else BigInt(0),
      yIn = if (mode == ArctanMagnitude) doubleToFixed(y) else BigInt(0)
    )
    while(!model.done) model.step()
  }

  // Basic functionality tests
  it should "calculate arctangent for positive coordinates" in {
    runTest(x = 1.0, y = 1.0, mode = ArctanMagnitude)
    fixedToDouble(model.arctan) should be (Pi/4 +- precision)
  }

  it should "calculate sine/cosine with proper scaling" in {
    runTest(angle = Pi/4, mode = SinCos)
    fixedToDouble(model.cos) should be (sqrt(2)/2 +- precision)
    fixedToDouble(model.sin) should be (sqrt(2)/2 +- precision)
  }

  // Edge cases for vectoring mode (arctan)
  it should "handle zero Y input in vectoring mode" in {
    runTest(x = 1.0, y = 0.0, mode = ArctanMagnitude)
    fixedToDouble(model.arctan) should be (0.0 +- precision)
  }

  it should "handle zero X input in vectoring mode" in {
    runTest(x = 0.0, y = 1.0, mode = ArctanMagnitude)
    fixedToDouble(model.arctan) should be (Pi/2 +- precision)
  }

  it should "handle negative Y coordinate in vectoring mode" in {
    runTest(x = 1.0, y = -1.0, mode = ArctanMagnitude)
    fixedToDouble(model.arctan) should be (-Pi/4 +- precision)
  }

  // Edge cases for rotation mode (sin/cos)
  it should "calculate sine/cosine for zero angle" in {
    runTest(angle = 0.0, mode = SinCos)
    fixedToDouble(model.cos) should be (1.0 +- precision)
    fixedToDouble(model.sin) should be (0.0 +- precision)
  }

  it should "calculate sine/cosine for Pi/2 angle" in {
    runTest(angle = Pi/2, mode = SinCos)
    fixedToDouble(model.cos) should be (0.0 +- precision)
    fixedToDouble(model.sin) should be (1.0 +- precision)
  }

  it should "calculate sine/cosine for negative angle" in {
    runTest(angle = -Pi/4, mode = SinCos)
    fixedToDouble(model.cos) should be (sqrt(2)/2 +- precision)
    fixedToDouble(model.sin) should be (-sqrt(2)/2 +- precision)
  }

  // Input boundary tests
  it should "handle maximum input values" in {
    val maxVal = (1 << (width - 1)) - 1
    runTest(angle = fixedToDouble(maxVal), mode = SinCos)
    // Just verify the model completes without error
    model.done should be (true)
  }

  it should "handle minimum input values" in {
    val minVal = -(1 << (width - 1))
    runTest(angle = fixedToDouble(minVal), mode = SinCos)
    // Just verify the model completes without error
    model.done should be (true)
  }

  // Multiple operations test
  it should "handle consecutive operations correctly" in {
    // First operation - sin/cos
    runTest(angle = Pi/3, mode = SinCos)
    fixedToDouble(model.cos) should be (0.5 +- precision)
    fixedToDouble(model.sin) should be (sqrt(3)/2 +- precision)

    // Second operation - arctan
    runTest(x = 1.0, y = sqrt(3), mode = ArctanMagnitude)
    fixedToDouble(model.arctan) should be (Pi/3 +- precision)
  }

  // Error condition tests
  it should "not complete when not started" in {
    model.reset()
    model.done should be (false)
    model.step()
    model.done should be (false)
  }

  // Add new test cases for magnitude correction
  it should "produce correct magnitudes with correction enabled" in {
    val modelWithCorrection = new TrigCordicModel(
      width = width,
      cycleCount = testCycles,
      integerBits = integerBits,
      magnitudeCorrection = true
    )

    // Test angles where both sin and cos are non-zero
    val testAngles = Seq(Pi/4, Pi/6, Pi/3)
    
    for (angle <- testAngles) {
      runTest(angle = angle, mode = SinCos)
      val cos = fixedToDouble(model.cos)
      val sin = fixedToDouble(model.sin)
      
      // Calculate the actual magnitude of the output vector
      val magnitude = sqrt(cos*cos + sin*sin)
      // With correction, magnitude should be very close to 1.0
      magnitude should be (1.0 +- precision)
    }
  }

  it should "produce scaled magnitudes with correction disabled" in {
    val modelWithoutCorrection = new TrigCordicModel(
      width = width,
      cycleCount = testCycles,
      integerBits = integerBits,
      magnitudeCorrection = false
    )

    // Test angles where both sin and cos are non-zero
    val testAngles = Seq(Pi/4, Pi/6, Pi/3)
    
    for (angle <- testAngles) {
      // Use the uncorrected model
      modelWithoutCorrection.reset()
      modelWithoutCorrection.setInputs(
        start = true,
        modeIn = SinCos,
        theta = doubleToFixed(angle),
        xIn = BigInt(0),
        yIn = BigInt(0)
      )
      while(!modelWithoutCorrection.done) modelWithoutCorrection.step()

      val cos = fixedToDouble(modelWithoutCorrection.cos)
      val sin = fixedToDouble(modelWithoutCorrection.sin)
      
      // Calculate the actual magnitude of the output vector
      val magnitude = sqrt(cos*cos + sin*sin)
      // Without correction, magnitude should be close to K (CORDIC gain)
      magnitude should be (1/K +- precision)
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 