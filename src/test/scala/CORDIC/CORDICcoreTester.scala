// This file contains tests for the CORDICcore Chisel module, verifying its functionality for trigonometric, linear, and hyperbolic operations.
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.math._

class CORDICCoreTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  // Helper to convert Double to BigInt for SInt poking
  def doubleToBigInt(value: Double, fractionalBits: Int, width: Int): BigInt = {
    CORDICCore.doubleToFixed(value, fractionalBits, width)
  }

  // Helper to convert BigInt from SInt peeking to Double
  def bigIntToDouble(value: BigInt, fractionalBits: Int): Double = {
    value.doubleValue / (1L << fractionalBits)
  }

  // Test parameters
  val testWidth = 32
  val testIntegerBits = 3 // Gives 32-1-3 = 28 fractional bits
  val testFractionalBits = testWidth - 1 - testIntegerBits
  val testCycleCount = 16 // Sufficient for reasonable precision
  val tolerance = 0.001 // Tolerance for floating point comparisons

  /**
    * Runs a single CORDIC operation test.
    * @param dut The CORDICCore instance.
    * @param mode The CORDIC operation mode.
    * @param xIn The value for inputX.
    * @param yIn The value for inputY.
    * @param thetaIn The value for inputTheta.
    * @param expectOutput1 Whether to wait for output1 to be valid.
    * @param expectOutput2 Whether to wait for output2 to be valid.
    * @param checker A function that takes the two double-precision outputs and performs checks.
    */
  def runTest(
    dut: CORDICCore,
    mode: CORDICCore.CORDICModeAll.Type,
    xIn: Double = 0.0,
    yIn: Double = 0.0,
    thetaIn: Double = 0.0,
    expectOutput1: Boolean = true,
    expectOutput2: Boolean = true,
    checker: (Double, Double) => Unit
  ): Unit = {
    // Poke inputs
    dut.io.mode.valid.poke(true.B)
    dut.io.mode.bits.poke(mode)
    dut.io.inputX.poke(doubleToBigInt(xIn, testFractionalBits, testWidth).S(testWidth.W))
    dut.io.inputY.poke(doubleToBigInt(yIn, testFractionalBits, testWidth).S(testWidth.W))
    dut.io.inputTheta.poke(doubleToBigInt(thetaIn, testFractionalBits, testWidth).S(testWidth.W))

    // Wait for mode to be accepted
    assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
    dut.clock.step(1)
    dut.io.mode.valid.poke(false.B)

    // Wait for CORDIC computation
    dut.clock.step(testCycleCount + 5)

    // Wait for valid output
    dut.io.output1.ready.poke(true.B)
    dut.io.output2.ready.poke(true.B)

    var cyclesWaiting = 0
    def outputIsValid(): Boolean = {
      (!expectOutput1 || dut.io.output1.valid.peek().litToBoolean) &&
      (!expectOutput2 || dut.io.output2.valid.peek().litToBoolean)
    }

    while(!outputIsValid() && cyclesWaiting < 10) {
      dut.clock.step(1)
      cyclesWaiting += 1
    }
    assert(cyclesWaiting < 10, s"Timeout waiting for valid outputs in mode $mode")

    // Peek outputs
    val out1Fixed = dut.io.output1.bits.peek().litValue
    val out2Fixed = dut.io.output2.bits.peek().litValue
    val out1Double = bigIntToDouble(out1Fixed, testFractionalBits)
    val out2Double = bigIntToDouble(out2Fixed, testFractionalBits)

    // Run checker
    checker(out1Double, out2Double)

    // Cleanup
    dut.clock.step(1)
    dut.io.output1.ready.poke(false.B)
    dut.io.output2.ready.poke(false.B)
  }

  behavior of "CORDICCore"

  it should "perform Trig SinCos correctly" in {
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
        val testAngles = Seq(0.0, Pi / 6, Pi / 4, Pi / 3, -Pi/4, Pi/2)
        for (angle <- testAngles) {
          runTest(
            dut, CORDICCore.CORDICModeAll.TrigSinCos, thetaIn = angle,
            checker = (cosOut, sinOut) => {
              val expectedCos = cos(angle)
              val expectedSin = sin(angle)
              val kGain = CORDICCore.TRIGONOMETRIC_GAIN
              val expectedCosVal = if (gainCorrection) expectedCos else expectedCos / kGain
              val expectedSinVal = if (gainCorrection) expectedSin else expectedSin / kGain
              
              cosOut should be (expectedCosVal +- tolerance)
              sinOut should be (expectedSinVal +- tolerance)
            }
          )
        }
      }
    }
  }

  it should "perform Linear Multiply correctly" in {
    val gainCorrection = false // Not applicable to linear
    test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
      val testValues = Seq(
        (0.5, 1.2),
        (1.0, 0.5),
        (-0.5, 1.2),
        (0.5, -1.2),
        (-0.5, -1.2),
        (0.0, 1.5),
        (1.5, 0.0)
      )
      for ((a, b) <- testValues) {
        runTest(
          dut, CORDICCore.CORDICModeAll.LinearMultiply, xIn = a, yIn = b, expectOutput2 = false,
          checker = (productOut, _) => {
            val expectedProduct = a * b
            productOut should be (expectedProduct +- tolerance)
          }
        )
      }
    }
  }
  
  it should "perform Hyperbolic SinhCosh correctly" in {
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testAngles = Seq(0.0, 0.5, 1.0, -0.5, -1.0)
        for(angle <- testAngles) {
          runTest(
            dut, CORDICCore.CORDICModeAll.HyperSinhCosh, thetaIn = angle,
            checker = (coshOut, sinhOut) => {
              val expectedCosh = cosh(angle)
              val expectedSinh = sinh(angle)

              val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
              var kGain = 1.0
              if (hyperShiftExponents.nonEmpty) {
                 kGain = CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents)
              }
              
              val expectedCoshVal = if (gainCorrection) expectedCosh else expectedCosh * kGain
              val expectedSinhVal = if (gainCorrection) expectedSinh else expectedSinh * kGain
              
              coshOut should be (expectedCoshVal +- tolerance)
              sinhOut should be (expectedSinhVal +- tolerance)
            }
          )
        }
      }
    }
  }

  it should "perform Linear Division correctly" in {
    val gainCorrection = false // Not applicable to linear
    test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
      val testValues = Seq(
        (1.5, 0.5),
        (1.0, 2.0),
        (-1.5, 0.5),
        (1.5, -0.5),
        (-1.5, -0.5),
        (0.0, 2.0)
      )
      for ((dividend, divisor) <- testValues) {
        runTest(
          dut, CORDICCore.CORDICModeAll.LinearDivide, xIn = dividend, yIn = divisor, expectOutput2 = false,
          checker = (quotientOut, _) => {
            val expectedQuotient = dividend / divisor
            quotientOut should be (expectedQuotient +- tolerance)
          }
        )
      }
    }
  }

  it should "perform Trig ArctanMagnitude correctly" in {
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
        val testCoords = Seq(
          (1.0, 0.5),
          (1.0, 1.0),
          (0.5, 1.0),
          (1.0, -0.5),
          (1.0, -1.0)
        )
        for ((x, y) <- testCoords) {
          runTest(
            dut, CORDICCore.CORDICModeAll.TrigArctanMagnitude, xIn = x, yIn = y,
            checker = (arctanOut, magnitudeOut) => {
              val expectedArctan = atan2(y, x)
              val rawMagnitude = sqrt(x * x + y * y)
              val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude / CORDICCore.TRIGONOMETRIC_GAIN
              
              arctanOut should be (expectedArctan +- tolerance)
              magnitudeOut should be (expectedMagnitude +- tolerance)
            }
          )
        }
      }
    }
  }

  it should "perform Hyperbolic AtanhMagnitude correctly" in {
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testCoords = Seq(
          (1.2, 0.5),
          (1.5, 0.3),
          (2.0, -1.0),
          (1.8, 0.0)
        )
        for ((x, y) <- testCoords) {
          runTest(
            dut, CORDICCore.CORDICModeAll.HyperAtanhMagnitude, xIn = x, yIn = y,
            checker = (atanhOut, magnitudeOut) => {
              val expectedAtanh = 0.5 * log((x + y)/(x - y))
              val rawMagnitude = sqrt(x * x - y * y)
              
              val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
              val kGain = if (hyperShiftExponents.nonEmpty) CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents) else 1.0
              
              val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude * kGain

              atanhOut should be (expectedAtanh +- tolerance)
              magnitudeOut should be (expectedMagnitude +- tolerance)
            }
          )
        }
      }
    }
  }

  it should "perform Exponential function correctly" in {
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testValues = Seq(0.5, -0.5, 1.0, -1.0, 0.0)
        
        val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
        val kGain = if (hyperShiftExponents.nonEmpty) CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents) else 1.0
        
        for (x <- testValues) {
          runTest(
            dut, CORDICCore.CORDICModeAll.Exponential, thetaIn = x,
            checker = (expOut, expNegOut) => {
              val expectedExp = exp(x)
              val expectedExpNeg = exp(-x)
              
              val expectedExpWithGain = if (gainCorrection) expectedExp else expectedExp * kGain
              val expectedExpNegWithGain = if (gainCorrection) expectedExpNeg else expectedExpNeg * kGain

              expOut should be (expectedExpWithGain +- tolerance)
              expNegOut should be (expectedExpNegWithGain +- tolerance)

              val expectedProduct = if (gainCorrection) 1.0 else kGain * kGain
              expOut * expNegOut should be (expectedProduct +- tolerance)
            }
          )
        }
      }
    }
  }

  it should "perform Natural Logarithm correctly" in {
    // Note: gainCorrection does not affect the NaturalLog operation, but we test both DUT configurations.
    for (gainCorrection <- Seq(true, false)) {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testValues = Seq(2.71828, 2.0, 1.5, 1.0, 0.5, 0.25, 4.0)

        for (x <- testValues) {
          runTest(
            dut, CORDICCore.CORDICModeAll.NaturalLog, xIn = x, expectOutput2 = false,
            checker = (lnOut, _) => {
              val expectedLn = log(x)
              lnOut should be (expectedLn +- tolerance)
            }
          )
        }
      }
    }
  }
} 