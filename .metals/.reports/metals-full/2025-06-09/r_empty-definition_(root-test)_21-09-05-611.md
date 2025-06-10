error id: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICcoreTester.scala:
file://<WORKSPACE>/src/test/scala/CORDIC/CORDICcoreTester.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 4525
uri: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICcoreTester.scala
text:
```scala
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._

class CORDICCoreTester extends AnyFlatSpec with ChiselScalatestTester {

  // Helper to convert Double to BigInt for SInt poking
  def doubleToBigInt(value: Double, fractionalBits: Int, width: Int): BigInt = {
    CORDICCore.doubleToFixed(value, fractionalBits, width)
  }

  // Helper to convert BigInt from SInt peeking to Double
  def bigIntToDouble(value: BigInt, fractionalBits: Int): Double = {
    value.doubleValue / (1L << fractionalBits)
  }

  // Helper to check equality with tolerance
  def checkClose(actual: Double, expected: Double, tolerance: Double, message: String = ""): Unit = {
    assert(abs(actual - expected) < tolerance, s"$message: Expected $expected, got $actual (tolerance $tolerance)")
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
    * @param inputX The value for inputX.
    * @param inputY The value for inputY.
    * @param inputTheta The value for inputTheta.
    * @param expectOutput1 Whether to wait for output1 to be valid.
    * @param expectOutput2 Whether to wait for output2 to be valid.
    * @param checker A function that takes the two double-precision outputs and performs checks.
    */
  def runTest(
    dut: CORDICCore,
    mode: CORDICCore.CORDICModeAll.Type,
    inputX: Double = 0.0,
    inputY: Double = 0.0,
    inputTheta: Double = 0.0,
    expectOutput1: Boolean = true,
    expectOutput2: Boolean = true,
    checker: (Double, Double) => Unit
  ): Unit = {
    // Poke inputs
    dut.io.mode.valid.poke(true.B)
    dut.io.mode.bits.poke(mode)
    dut.io.inputX.poke(doubleToBigInt(inputX, testFractionalBits, testWidth).S(testWidth.W))
    dut.io.inputY.poke(doubleToBigInt(inputY, testFractionalBits, testWidth).S(testWidth.W))
    dut.io.inputTheta.poke(doubleToBigInt(inputTheta, testFractionalBits, testWidth).S(testWidth.W))

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

    it should s"perform Trig SinCos correctly with gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
        val angle_rad = Pi / 6 // 30 degrees
        runTest(
          dut, CORDICCore.CORDICModeAll.TrigSinCos, inputTheta = angle_rad,
          checker = (cosOut, sinOut) => {
            val expectedCos = cos(angle_rad)
            val expectedSin = sin(angle_rad)
            val k_gain = CORDICCore.TRIG_CORDIC_K_DBL
            val expectedCosVal = if (gainCorrection) expectedCos else expectedCos / k_gain
            val expectedSinVal = if (gainCorrection) expectedSin else expectedSin / k_gain
            
            checkClose(cosOut, expectedCosVal, tolerance, "Cos output mismatch")
            checkClose(sinOut, expectedSinVal, tolerance, "Sin output mismatch")
          }
        )
      }
    }
  }

  it should "perform Linear Mu@@ltiply correctly" in {
    val gainCorrection = false // Not applicable to linear
    test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
      val inputA_val = 0.5
      val inputB_val = 1.2
      runTest(
        dut, CORDICCore.CORDICModeAll.LinearMultiply, inputX = inputA_val, inputY = inputB_val, expectOutput2 = false,
        checker = (productOut, _) => {
          val expectedProduct = inputA_val * inputB_val
          checkClose(productOut, expectedProduct, tolerance, "Product output mismatch")
        }
      )
    }
  
  
  for (gainCorrection <- Seq(true, false)) {
    it should s"perform Hyperbolic SinhCosh correctly with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val angle_rad = 0.5
        runTest(
          dut, CORDICCore.CORDICModeAll.HyperSinhCosh, inputTheta = angle_rad,
          checker = (coshOut, sinhOut) => {
            val expectedCosh = cosh(angle_rad)
            val expectedSinh = sinh(angle_rad)

            val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
            var k_h_gain = 1.0
            if (hyperShiftExponents.nonEmpty) {
               k_h_gain = CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents)
            }
            
            val expectedCoshVal = if (gainCorrection) expectedCosh else expectedCosh * k_h_gain
            val expectedSinhVal = if (gainCorrection) expectedSinh else expectedSinh * k_h_gain
            
            checkClose(coshOut, expectedCoshVal, tolerance, "Cosh output mismatch")
            checkClose(sinhOut, expectedSinhVal, tolerance, "Sinh output mismatch")
          }
        )
      }
    }
  }

  it should "perform Linear Division correctly" in {
    val gainCorrection = false // Not applicable to linear
    test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
      val dividend_val = 1.5 // A
      val divisor_val = 0.5  // B
      runTest(
        dut, CORDICCore.CORDICModeAll.LinearDivide, inputX = dividend_val, inputY = divisor_val, expectOutput2 = false,
        checker = (quotientOut, _) => {
          val expectedQuotient = dividend_val / divisor_val
          checkClose(quotientOut, expectedQuotient, tolerance, "Quotient output mismatch")
        }
      )
    }
  }

  for (gainCorrection <- Seq(true, false)) {
    it should s"perform Trig ArctanMagnitude correctly with gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
        val x_val = 1.0
        val y_val = 0.5
        runTest(
          dut, CORDICCore.CORDICModeAll.TrigArctanMagnitude, inputX = x_val, inputY = y_val,
          checker = (arctanOut, magnitudeOut) => {
            val expectedArctan = atan2(y_val, x_val)
            val rawMagnitude = sqrt(x_val * x_val + y_val * y_val)
            val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude / CORDICCore.TRIG_CORDIC_K_DBL
            
            checkClose(arctanOut, expectedArctan, tolerance, "Arctan output mismatch")
            checkClose(magnitudeOut, expectedMagnitude, tolerance, "Magnitude output mismatch")
          }
        )
      }
    }
  }

  for (gainCorrection <- Seq(true, false)) {
    it should s"perform Hyperbolic AtanhMagnitude correctly with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val x_val = 1.2
        val y_val = 0.5
        runTest(
          dut, CORDICCore.CORDICModeAll.HyperAtanhMagnitude, inputX = x_val, inputY = y_val,
          checker = (atanhOut, magnitudeOut) => {
            val expectedAtanh = 0.5 * log((x_val + y_val)/(x_val - y_val))
            val rawMagnitude = sqrt(x_val * x_val - y_val * y_val)
            
            val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
            val k_h_gain = if (hyperShiftExponents.nonEmpty) CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents) else 1.0
            
            val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude * k_h_gain

            checkClose(atanhOut, expectedAtanh, tolerance, "Atanh output mismatch")
            checkClose(magnitudeOut, expectedMagnitude, tolerance, "Magnitude output mismatch")
          }
        )
      }
    }
  }

  for (gainCorrection <- Seq(true, false)) {
    it should s"perform Exponential function correctly with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testValues = Seq(0.5, -0.5, 1.0, -1.0, 0.0)
        
        val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
        val k_h_gain = if (hyperShiftExponents.nonEmpty) CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents) else 1.0
        
        for (x_val <- testValues) {
          runTest(
            dut, CORDICCore.CORDICModeAll.Exponential, inputTheta = x_val,
            checker = (expOut, expNegOut) => {
              val expectedExp = exp(x_val)
              val expectedExpNeg = exp(-x_val)
              
              val expectedExpWithGain = if (gainCorrection) expectedExp else expectedExp * k_h_gain
              val expectedExpNegWithGain = if (gainCorrection) expectedExpNeg else expectedExpNeg * k_h_gain

              checkClose(expOut, expectedExpWithGain, tolerance, s"e^x output mismatch for x=$x_val")
              checkClose(expNegOut, expectedExpNegWithGain, tolerance, s"e^(-x) output mismatch for x=$x_val")

              val expectedProduct = if (gainCorrection) 1.0 else k_h_gain * k_h_gain
              checkClose(expOut * expNegOut, expectedProduct, tolerance, s"e^x * e^(-x) should be approximately ${expectedProduct} for x=$x_val")
            }
          )
        }
      }
    }
  }

  for (gainCorrection <- Seq(true, false)) {
    it should s"perform Natural Logarithm correctly with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
      test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
        val testValues = Seq(2.71828, 2.0, 1.5, 1.0, 0.5, 0.25, 4.0)

        for (x_val <- testValues) {
          runTest(
            dut, CORDICCore.CORDICModeAll.NaturalLog, inputX = x_val, expectOutput2 = false,
            checker = (lnOut, _) => {
              val expectedLn = log(x_val)
              checkClose(lnOut, expectedLn, tolerance, s"Natural log output mismatch for x=$x_val")
            }
          )
        }
      }
    }
  }
} 
```


#### Short summary: 

empty definition using pc, found symbol in pc: 