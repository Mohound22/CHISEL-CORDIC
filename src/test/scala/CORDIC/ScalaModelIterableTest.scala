// This file contains unit tests for the Scala software models of the CORDIC algorithms. It verifies the correctness of the models themselves.
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

  /**
    * Runs a single trigonometric test using the Scala model.
    * @param model The TrigCordicModel instance to test.
    * @param mode The operation mode (SinCos or ArctanMagnitude).
    * @param angle The input angle for SinCos mode.
    * @param xIn The input x-coordinate for ArctanMagnitude mode.
    * @param yIn The input y-coordinate for ArctanMagnitude mode.
    * @param checker A function that takes the two double-precision outputs and performs checks.
    */
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

  /**
    * Runs a single linear test using the Scala model.
    * @param model The LinearCordicModel instance to test.
    * @param mode The operation mode (Multiply or Divide).
    * @param inputA The first input value (multiplicand or dividend).
    * @param inputB The second input value (multiplier or divisor).
    * @param checker A function that takes the double-precision output and performs checks.
    */
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

  /**
    * Runs a single hyperbolic test using the Scala model.
    * @param model The HyperCordicModel instance to test.
    * @param mode The operation mode (SinhCosh or AtanhMagnitudeHyper).
    * @param theta The input theta for SinhCosh mode.
    * @param xIn The input x-coordinate for AtanhMagnitudeHyper mode.
    * @param yIn The input y-coordinate for AtanhMagnitudeHyper mode.
    * @param checker A function that takes the two double-precision outputs and performs checks.
    */
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
      //("DESCRIPTION", mode, angle, x, y, expected1, expected2)
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

  behavior of "LinearCordicModel"

  val linearModel = new LinearCordicModel(testWidth, testCycles, testIntegerBits)
  val multiplicationTestCases = Seq(
      //("DESCRIPTION", a, b, expected)
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
      //("DESCRIPTION", a, b, expected)
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

  val hyperTestCases = Seq(
    //("DESCRIPTION", mode, theta, x, y, expected1, expected2)
    ("calculate atanh for positive y/x ratio", AtanhMagnitudeHyper, 0.0, 1.0, 0.5, atanh(0.5), sqrt(0.75)),
    ("calculate atanh for negative y/x ratio", AtanhMagnitudeHyper, 0.0, 1.0, -0.5, atanh(-0.5), sqrt(0.75)),
    ("calculate sinh/cosh for positive theta", SinhCosh, 0.5, 0.0, 0.0, cosh(0.5), sinh(0.5)),
    ("calculate sinh/cosh for negative theta", SinhCosh, -0.5, 0.0, 0.0, cosh(-0.5), sinh(-0.5))
  )

  for((desc, mode, theta, x, y, expected1, expected2) <- hyperTestCases){
    it should desc in {
      runHyperTest(hyperModelWithCorrection, mode, theta, x, y, (res1, res2) => {
        res1 should be (expected1 +- tolerance)
        res2 should be (expected2 +- tolerance)
      })
    }
  }
} 