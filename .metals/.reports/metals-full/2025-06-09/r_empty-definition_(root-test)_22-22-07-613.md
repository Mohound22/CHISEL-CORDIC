error id: file://<WORKSPACE>/src/test/scala/CORDIC/ScalaModelIterableTest.scala:
file://<WORKSPACE>/src/test/scala/CORDIC/ScalaModelIterableTest.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 4239
uri: file://<WORKSPACE>/src/test/scala/CORDIC/ScalaModelIterableTest.scala
text:
```scala
package CORDIC

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CordicModelConstants._
import CordicModelConstants.Mode._
import CordicModelConstants.ModeHyper._
import CordicModelConstants.ModeLinear._
import scala.math._

class ScalaModelIterableTest extends AnyFlatSpec with Matchers {

  val testWidth = 32
  val testFractionalBits = 16
  val testIntegerBits = testWidth - 1 - testFractionalBits
  val testCycles = 20
  val tolerance = 0.001

  def doubleToFixed(value: Double): BigInt =
    CordicModelConstants.doubleToFixed(value, testFractionalBits, testWidth)

  def fixedToDouble(value: BigInt): Double = {
    value.toDouble / pow(2, testFractionalBits)
  }

  def runTrigTest(
      model: TrigCordicModel,
      mode: Mode,
      angle: Double = 0.0,
      xIn: Double = 0.0,
      yIn: Double = 0.0,
      checker: (Double, Double) => Unit
  ): Unit = {
    model.reset()
    model.setInputs(
      start = true,
      modeIn = mode,
      theta = if (mode == SinCos) doubleToFixed(angle) else BigInt(0),
      xIn = if (mode == ArctanMagnitude) doubleToFixed(xIn) else BigInt(0),
      yIn = if (mode == ArctanMagnitude) doubleToFixed(yIn) else BigInt(0)
    )
    while (!model.done) model.step()
    if (mode == SinCos) {
      checker(fixedToDouble(model.cos), fixedToDouble(model.sin))
    } else {
      checker(fixedToDouble(model.arctan), fixedToDouble(model.magnitude))
    }
  }

  def runLinearTest(
      model: LinearCordicModel,
      mode: ModeLinear,
      inputA: Double,
      inputB: Double,
      checker: (Double) => Unit
  ): Unit = {
    model.reset()
    model.setInputs(
      start = true,
      modeIn = mode,
      a = doubleToFixed(inputA),
      b = doubleToFixed(inputB)
    )
    var safetyCounter = 0
    while (!model.done && safetyCounter < testCycles + 5) {
      model.step()
      safetyCounter += 1
    }
    if (mode == Multiply) {
      checker(fixedToDouble(model.product))
    } else {
      checker(fixedToDouble(model.quotient))
    }
  }

  def runHyperTest(
    model: HyperCordicModel,
    mode: ModeHyper,
    theta: Double = 0.0,
    xIn: Double = 0.0,
    yIn: Double = 0.0,
    checker: (Double, Double) => Unit
    ): Unit = {
        model.reset()
        model.setInputs(
            start = true,
            modeIn = mode,
            theta = if (mode == SinhCosh) doubleToFixed(theta) else BigInt(0),
            xIn = if (mode == AtanhMagnitudeHyper) doubleToFixed(xIn) else BigInt(0),
            yIn = if (mode == AtanhMagnitudeHyper) doubleToFixed(yIn) else BigInt(0)
        )
        while(!model.done) {
            model.step()
        }
        if (mode == SinhCosh) {
            checker(fixedToDouble(model.cosh), fixedToDouble(model.sinh))
        } else {
            checker(fixedToDouble(model.atanh), fixedToDouble(model.magnitudeHyper))
        }
    }

  behavior of "TrigCordicModel"

  val trigModel = new TrigCordicModel(testWidth, testCycles, testIntegerBits)
  val trigTestCases = Seq(
      ("calculate arctangent for positive coordinates", ArctanMagnitude, 0.0, 1.0, 1.0, Pi/4, 0.0),
      ("handle zero Y input in vectoring mode", ArctanMagnitude, 0.0, 1.0, 0.0, 0.0, 0.0),
      ("handle zero X input in vectoring mode", ArctanMagnitude, 0.0, 0.0, 1.0, Pi/2, 0.0),
      ("handle negative Y coordinate in vectoring mode", ArctanMagnitude, 0.0, 1.0, -1.0, -Pi/4, 0.0),
      ("calculate sine/cosine for zero angle", SinCos, 0.0, 0.0, 0.0, 1.0, 0.0),
      ("calculate sine/cosine for Pi/2 angle", SinCos, Pi/2, 0.0, 0.0, 0.0, 1.0),
      ("calculate sine/cosine for negative angle", SinCos, -Pi/4, 0.0, 0.0, sqrt(2)/2, -sqrt(2)/2),
      ("calculate sine/cosine with proper scaling", SinCos, Pi/4, 0.0, 0.0, sqrt(2)/2, sqrt(2)/2)
  )

  for((desc, mode, angle, x, y, expected1, expected2) <- trigTestCases){
      it should desc in {
          runTrigTest(trigModel, mode, angle, x, y, (res1, res2) => {
              if (mode == SinCos) {
                  res1 should be (expected1 +- tolerance)
                  res2 should be (expected2 +- tolerance)
              } else {
                  res1 should be (expected1 +- tolerance)
              }
          })
      }
  }

  behavior of "@@LinearCordicModel"

  val linearModel = new LinearCordicModel(testWidth, testCycles, testIntegerBits)
  val multiplicationTestCases = Seq(
      ("positive numbers", 2.5, 3.0, 7.5),
      ("one negative number", -2.5, 3.0, -7.5),
      ("two negative numbers", -2.5, -3.0, 7.5),
      ("with zero", 2.5, 0.0, 0.0),
      ("small numbers", 0.125, 0.5, 0.0625),
      ("one number being 1.0", 5.75, 1.0, 5.75)
  )

  for((desc, a, b, expected) <- multiplicationTestCases){
      it should s"calculate multiplication for $desc" in {
          runLinearTest(linearModel, Multiply, a, b, res => res should be (expected +- tolerance))
      }
  }

  val divisionTestCases = Seq(
      ("positive numbers", 7.5, 2.5, 3.0),
      ("negative dividend", -7.5, 2.5, -3.0),
      ("negative divisor", 7.5, -2.5, -3.0),
      ("both negative numbers", -7.5, -2.5, 3.0),
      ("zero dividend", 0.0, 2.5, 0.0),
      ("resulting in a fraction", 1.0, 4.0, 0.25),
      ("dividend smaller than divisor", 2.0, 8.0, 0.25),
      ("division by 1.0", 5.75, 1.0, 5.75)
  )

  for((desc, a, b, expected) <- divisionTestCases){
      it should s"calculate division for $desc" in {
          runLinearTest(linearModel, Divide, a, b, res => res should be (expected +- tolerance))
      }
  }

  behavior of "HyperCordicModel"

  val hyperModelWithCorrection = new HyperCordicModel(testWidth, testCycles, testIntegerBits, magnitudeCorrection = true)
  val hyperModelNoCorrection = new HyperCordicModel(testWidth, testCycles, testIntegerBits, magnitudeCorrection = false)

  it should "calculate atanh for positive y/x ratio" in {
      val testX = 1.0
      val testY = 0.5
      runHyperTest(hyperModelWithCorrection, AtanhMagnitudeHyper, xIn = testX, yIn = testY, checker = (atanhRes, magRes) => {
          atanhRes should be (atanh(testY/testX) +- tolerance)
          magRes should be (sqrt(testX*testX - testY*testY) +- tolerance)
      })
  }

  it should "calculate atanh for negative y/x ratio" in {
      val testX = 1.0
      val testY = -0.5
      runHyperTest(hyperModelWithCorrection, AtanhMagnitudeHyper, xIn = testX, yIn = testY, checker = (atanhRes, magRes) => {
          atanhRes should be (atanh(testY/testX) +- tolerance)
          magRes should be (sqrt(testX*testX - testY*testY) +- tolerance)
      })
  }

  it should "calculate sinh/cosh for positive theta" in {
      val testTheta = 0.5
      runHyperTest(hyperModelWithCorrection, SinhCosh, theta = testTheta, checker = (coshRes, sinhRes) => {
          coshRes should be (cosh(testTheta) +- tolerance)
          sinhRes should be (sinh(testTheta) +- tolerance)
      })
  }

  it should "calculate sinh/cosh for negative theta" in {
      val testTheta = -0.5
      runHyperTest(hyperModelWithCorrection, SinhCosh, theta = testTheta, checker = (coshRes, sinhRes) => {
          coshRes should be (cosh(testTheta) +- tolerance)
          sinhRes should be (sinh(testTheta) +- tolerance)
      })
  }

  it should "produce scaled magnitude in AtanhMagnitudeHyper with correction disabled" in {
      val hyperShiftExponents = CordicModelConstants.getHyperbolicShiftExponents(testCycles)
      val k_h = CordicModelConstants.calculateHyperbolicGainFactor(hyperShiftExponents)
      val testX = 1.2
      val testY = 0.5
      runHyperTest(hyperModelNoCorrection, AtanhMagnitudeHyper, xIn = testX, yIn = testY, checker = (atanhRes, magRes) => {
          val expectedRawXOutput = k_h * sqrt(testX*testX - testY*testY)
          magRes should be (expectedRawXOutput +- tolerance * k_h * 5)
          atanhRes should be (atanh(testY/testX) +- tolerance * 5)
      })
  }
} 
```


#### Short summary: 

empty definition using pc, found symbol in pc: 