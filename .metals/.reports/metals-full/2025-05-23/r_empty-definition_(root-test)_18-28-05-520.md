error id: file://<WORKSPACE>/src/test/scala/CORDIC/scalaTestIterable.scala:CORDIC/CordicModel#setInputs().
file://<WORKSPACE>/src/test/scala/CORDIC/scalaTestIterable.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol CORDIC/CordicModel#setInputs().
empty definition using fallback
non-local guesses:

offset: 1686
uri: file://<WORKSPACE>/src/test/scala/CORDIC/scalaTestIterable.scala
text:
```scala

package CORDIC

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CordicModelConstants._


class CordicModelTest extends AnyFlatSpec with Matchers {
  // Test parameters
  val width = 16
  val fractionalBits = 12
  val integerBits = 3
  val testCycles = 14
  val precision = 0.001 // Fixed-point precision tolerance
  
  // Instantiate model with test parameters
  val model = new CordicModel(
    width = width,
    cycleCount = testCycles,
    integerBits = integerBits
  )

  // Helper conversions
  def doubleToFixed(x: Double): BigInt = 
    CordicModelConstants.doubleToFixed(x, fractionalBits, width)
    
  def fixedToDouble(x: BigInt): Double = 
    x.toDouble / (1L << fractionalBits)

  // Calculate CORDIC gain for verification
  def calculateAn(cycles: Int): Double =
    (0 until cycles).map(i => math.sqrt(1 + math.pow(2, -2*i))).product

  val An = calculateAn(testCycles)
  val K = 1.0 / An  // CORDIC scaling factor

  // Test cases
  // "CordicModel" should "reject invalid inputs in rotation mode" in {
  //   // Test angles beyond π/2 (fixed-point representation)
  //   val piOver2 = doubleToFixed(math.Pi/2)
  //   val justOverPiOver2 = piOver2 + 1
  //   an [AssertionError] should be thrownBy 
  //     model.setInputs(true, false, justOverPiOver2, 0, 0)
  // }

  // it should "reject negative X in vectoring mode" in {
  //   val negativeX = doubleToFixed(-1.0)
  //   an [AssertionError] should be thrownBy 
  //     model.setInputs(true, true, 0, negativeX, 0)
  // }

  it should "calculate arctangent for positive coordinates" in {
    val x = doubleToFixed(1.0)
    val y = doubleToFixed(1.0)
    
    model.@@setInputs(true, true, 0, x, y)
    while(!model.done) model.step()
    
    val expected = math.atan2(1.0, 1.0)
    fixedToDouble(model.arctan) should be (expected +- precision)
    println(s"Post-test state: ${model.state}")
  }

  it should "calculate sine/cosine with proper scaling" in {
    val angle = math.Pi/4  // 45 degrees
    val fixedAngle = doubleToFixed(angle)
    
    model.setInputs(true, false, fixedAngle, 0, 0)
    while(!model.done) model.step()
    
    val expectedCos = math.cos(angle)
    val expectedSin = math.sin(angle)

    fixedToDouble(model.cos) should be (expectedCos +- precision)
    fixedToDouble(model.sin) should be (expectedSin +- precision)
    println(s"Post-test state: ${model.state}")
  }

  it should "handle zero Y input in vectoring mode" in {
    
    val x = doubleToFixed(1.0)
    
    model.setInputs(true, true, 0, x, 0)
    while(!model.done) model.step()
    
    fixedToDouble(model.arctan) should be (0.0 +- precision)
    println(s"Post-test state: ${model.state}")
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 