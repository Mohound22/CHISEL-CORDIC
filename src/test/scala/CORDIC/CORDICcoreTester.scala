package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import scala.math._

class CORDICCoreTester extends AnyFreeSpec with ChiselScalatestTester {

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

  "CORDICCore" - {
    "it should perform Trig SinCos correctly" - {
      for (gainCorrection <- Seq(true, false)) {
        s"with gainCorrection = $gainCorrection" in {
          test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
            dut.io.mode.valid.poke(true.B)
            dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.TrigSinCos)
            
            val angle_rad = Pi / 6 // 30 degrees
            val expectedCos = cos(angle_rad)
            val expectedSin = sin(angle_rad)

            dut.io.inputTheta.poke(doubleToBigInt(angle_rad, testFractionalBits, testWidth).S(testWidth.W))
            // inputX and inputY are not used for TrigSinCos, poke 0 or any value
            dut.io.inputX.poke(0.S(testWidth.W))
            dut.io.inputY.poke(0.S(testWidth.W))

            // Wait for mode to be accepted (ready should be high in sIdle)
            assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
            dut.clock.step(1) // Mode and data latched, core moves to sBusy
            dut.io.mode.valid.poke(false.B)

            // Wait for CORDIC computation (cycleCount + a few for state transitions)
            dut.clock.step(testCycleCount + 5)

            // Set output ready and wait for valid output
            dut.io.output1.ready.poke(true.B)
            dut.io.output2.ready.poke(true.B)

            var cyclesWaiting = 0
            while(!(dut.io.output1.valid.peek().litToBoolean && dut.io.output2.valid.peek().litToBoolean) && cyclesWaiting < 10) {
              dut.clock.step(1)
              cyclesWaiting += 1
            }
            assert(cyclesWaiting < 10, "Timeout waiting for valid outputs")

            val cosOutFixed = dut.io.output1.bits.peek().litValue
            val sinOutFixed = dut.io.output2.bits.peek().litValue

            val cosOutDouble = bigIntToDouble(cosOutFixed, testFractionalBits)
            val sinOutDouble = bigIntToDouble(sinOutFixed, testFractionalBits)

            val k_gain = 0.6072529350088813
            val expectedCosVal = if (gainCorrection) expectedCos else expectedCos / k_gain
            val expectedSinVal = if (gainCorrection) expectedSin else expectedSin / k_gain
            
            checkClose(cosOutDouble, expectedCosVal, tolerance, "Cos output mismatch")
            checkClose(sinOutDouble, expectedSinVal, tolerance, "Sin output mismatch")

            dut.clock.step(1) // Ensure state machine returns to idle
            dut.io.output1.ready.poke(false.B)
            dut.io.output2.ready.poke(false.B)
          }
        }
      }
    }

    "it should perform Linear Multiply correctly" - {
      val gainCorrection = false // Not applicable to linear
      s"with includeLinear = true" in {
        test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
          dut.io.mode.valid.poke(true.B)
          dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.LinearMultiply)
          
          val inputA_val = 0.5
          val inputB_val = 1.2
          val expectedProduct = inputA_val * inputB_val

          dut.io.inputX.poke(doubleToBigInt(inputA_val, testFractionalBits, testWidth).S(testWidth.W)) // inputA
          dut.io.inputY.poke(doubleToBigInt(inputB_val, testFractionalBits, testWidth).S(testWidth.W)) // inputB
          dut.io.inputTheta.poke(0.S(testWidth.W)) // Not used

          assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
          dut.clock.step(1) 
          dut.io.mode.valid.poke(false.B)

          dut.clock.step(testCycleCount + 5)

          dut.io.output1.ready.poke(true.B)
          dut.io.output2.ready.poke(true.B) // Output 2 not used, but ready must be high

          var cyclesWaiting = 0
          while(!dut.io.output1.valid.peek().litToBoolean && cyclesWaiting < 10) { // Only output1 carries result for multiply
            dut.clock.step(1)
            cyclesWaiting += 1
          }
          assert(cyclesWaiting < 10, "Timeout waiting for valid output1")

          val productOutFixed = dut.io.output1.bits.peek().litValue
          val productOutDouble = bigIntToDouble(productOutFixed, testFractionalBits)
          
          checkClose(productOutDouble, expectedProduct, tolerance, "Product output mismatch")

          dut.clock.step(1) 
          dut.io.output1.ready.poke(false.B)
          dut.io.output2.ready.poke(false.B)
        }
      }
    }
    
    "it should perform Hyperbolic SinhCosh correctly" - {
      for (gainCorrection <- Seq(true, false)) {
        s"with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
          test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
            dut.io.mode.valid.poke(true.B)
            dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.HyperSinhCosh)
            
            val angle_rad = 0.5 
            val expectedCosh = cosh(angle_rad)
            val expectedSinh = sinh(angle_rad)

            dut.io.inputTheta.poke(doubleToBigInt(angle_rad, testFractionalBits, testWidth).S(testWidth.W))
            dut.io.inputX.poke(0.S(testWidth.W)) 
            dut.io.inputY.poke(0.S(testWidth.W))

            assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
            dut.clock.step(1) 
            dut.io.mode.valid.poke(false.B)

            dut.clock.step(testCycleCount + 5) 

            dut.io.output1.ready.poke(true.B)
            dut.io.output2.ready.poke(true.B)

            var cyclesWaiting = 0
            while(!(dut.io.output1.valid.peek().litToBoolean && dut.io.output2.valid.peek().litToBoolean) && cyclesWaiting < 10) {
              dut.clock.step(1)
              cyclesWaiting += 1
            }
            assert(cyclesWaiting < 10, "Timeout waiting for valid outputs")

            val coshOutFixed = dut.io.output1.bits.peek().litValue
            val sinhOutFixed = dut.io.output2.bits.peek().litValue

            val coshOutDouble = bigIntToDouble(coshOutFixed, testFractionalBits)
            val sinhOutDouble = bigIntToDouble(sinhOutFixed, testFractionalBits)

            // Calculate expected hyperbolic gain (K_H) for the given cycleCount
            val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
            var k_h_gain = 1.0
            if (hyperShiftExponents.nonEmpty) {
               k_h_gain = CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents)
            }
            
            val expectedCoshVal = if (gainCorrection) expectedCosh else expectedCosh * k_h_gain
            val expectedSinhVal = if (gainCorrection) expectedSinh else expectedSinh * k_h_gain
            
            checkClose(coshOutDouble, expectedCoshVal, tolerance, "Cosh output mismatch")
            checkClose(sinhOutDouble, expectedSinhVal, tolerance, "Sinh output mismatch")

            dut.clock.step(1) 
            dut.io.output1.ready.poke(false.B)
            dut.io.output2.ready.poke(false.B)
          }
        }
      }
    }

    "it should perform Linear Division correctly" - {
      val gainCorrection = false // Not applicable to linear
      s"with includeLinear = true" in {
        test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = true, includeHyperbolic = false)) { dut =>
          dut.io.mode.valid.poke(true.B)
          dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.LinearDivide)
          
          val dividend_val = 1.5 // A
          val divisor_val = 0.5  // B
          val expectedQuotient = dividend_val / divisor_val

          dut.io.inputX.poke(doubleToBigInt(dividend_val, testFractionalBits, testWidth).S(testWidth.W)) // dividend (A)
          dut.io.inputY.poke(doubleToBigInt(divisor_val, testFractionalBits, testWidth).S(testWidth.W))  // divisor (B)
          dut.io.inputTheta.poke(0.S(testWidth.W)) // Not used

          assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
          dut.clock.step(1) 
          dut.io.mode.valid.poke(false.B)

          dut.clock.step(testCycleCount + 5)

          dut.io.output1.ready.poke(true.B)
          dut.io.output2.ready.poke(true.B) // Output 2 not used, but ready must be high

          var cyclesWaiting = 0
          while(!dut.io.output1.valid.peek().litToBoolean && cyclesWaiting < 10) {
            dut.clock.step(1)
            cyclesWaiting += 1
          }
          assert(cyclesWaiting < 10, "Timeout waiting for valid output1")

          val quotientOutFixed = dut.io.output1.bits.peek().litValue
          val quotientOutDouble = bigIntToDouble(quotientOutFixed, testFractionalBits)
          
          checkClose(quotientOutDouble, expectedQuotient, tolerance, "Quotient output mismatch")

          dut.clock.step(1) 
          dut.io.output1.ready.poke(false.B)
          dut.io.output2.ready.poke(false.B)
        }
      }
    }

    "it should perform Trig ArctanMagnitude correctly" - {
      for (gainCorrection <- Seq(true, false)) {
        s"with gainCorrection = $gainCorrection" in {
          test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = false)) { dut =>
            dut.io.mode.valid.poke(true.B)
            dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.TrigArctanMagnitude)
            
            val x_val = 1
            val y_val = 0.5
            val expectedArctan = atan2(y_val, x_val)
            val rawMagnitude = sqrt(x_val * x_val + y_val * y_val)
            val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude / CORDICCore.TRIG_CORDIC_K_DBL

            dut.io.inputX.poke(doubleToBigInt(x_val, testFractionalBits, testWidth).S(testWidth.W))
            dut.io.inputY.poke(doubleToBigInt(y_val, testFractionalBits, testWidth).S(testWidth.W))
            dut.io.inputTheta.poke(0.S(testWidth.W)) // Not used for arctan mode

            assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
            dut.clock.step(1)
            dut.io.mode.valid.poke(false.B)

            dut.clock.step(testCycleCount + 5)

            dut.io.output1.ready.poke(true.B)
            dut.io.output2.ready.poke(true.B)

            var cyclesWaiting = 0
            while(!(dut.io.output1.valid.peek().litToBoolean && dut.io.output2.valid.peek().litToBoolean) && cyclesWaiting < 10) {
              dut.clock.step(1)
              cyclesWaiting += 1
            }
            assert(cyclesWaiting < 10, "Timeout waiting for valid outputs")

            val arctanOutFixed = dut.io.output1.bits.peek().litValue
            val magnitudeOutFixed = dut.io.output2.bits.peek().litValue

            val arctanOutDouble = bigIntToDouble(arctanOutFixed, testFractionalBits)
            val magnitudeOutDouble = bigIntToDouble(magnitudeOutFixed, testFractionalBits)

            checkClose(arctanOutDouble, expectedArctan, tolerance, "Arctan output mismatch")
            checkClose(magnitudeOutDouble, expectedMagnitude, tolerance, "Magnitude output mismatch")

            dut.clock.step(1)
            dut.io.output1.ready.poke(false.B)
            dut.io.output2.ready.poke(false.B)
          }
        }
      }
    }

    "it should perform Hyperbolic AtanhMagnitude correctly" - {
      for (gainCorrection <- Seq(true, false)) {
        s"with includeHyperbolic = true, gainCorrection = $gainCorrection" in {
          test(new CORDICCore(testWidth, testCycleCount, testIntegerBits, gainCorrection, includeLinear = false, includeHyperbolic = true)) { dut =>
            dut.io.mode.valid.poke(true.B)
            dut.io.mode.bits.poke(CORDICCore.CORDICModeAll.HyperAtanhMagnitude)
            
            val x_val = 1.2
            val y_val = 0.5
            val expectedAtanh = 0.5 * log((x_val + y_val)/(x_val - y_val))
            val rawMagnitude = sqrt(x_val * x_val - y_val * y_val)
            
            // Calculate hyperbolic gain factor
            val hyperShiftExponents = CORDICCore.getHyperbolicShiftExponents(testCycleCount)
            val k_h_gain = if (hyperShiftExponents.nonEmpty) {
              CORDICCore.calculateHyperbolicGainFactor(hyperShiftExponents)
            } else 1.0
            
            val expectedMagnitude = if (gainCorrection) rawMagnitude else rawMagnitude * k_h_gain

            dut.io.inputX.poke(doubleToBigInt(x_val, testFractionalBits, testWidth).S(testWidth.W))
            dut.io.inputY.poke(doubleToBigInt(y_val, testFractionalBits, testWidth).S(testWidth.W))
            dut.io.inputTheta.poke(0.S(testWidth.W)) // Not used for atanh mode

            assert(dut.io.mode.ready.peek().litToBoolean, "DUT mode.ready should be high in sIdle")
            dut.clock.step(1)
            dut.io.mode.valid.poke(false.B)

            dut.clock.step(testCycleCount + 5)

            dut.io.output1.ready.poke(true.B)
            dut.io.output2.ready.poke(true.B)

            var cyclesWaiting = 0
            while(!(dut.io.output1.valid.peek().litToBoolean && dut.io.output2.valid.peek().litToBoolean) && cyclesWaiting < 10) {
              dut.clock.step(1)
              cyclesWaiting += 1
            }
            assert(cyclesWaiting < 10, "Timeout waiting for valid outputs")

            val atanhOutFixed = dut.io.output1.bits.peek().litValue
            val magnitudeOutFixed = dut.io.output2.bits.peek().litValue

            val atanhOutDouble = bigIntToDouble(atanhOutFixed, testFractionalBits)
            val magnitudeOutDouble = bigIntToDouble(magnitudeOutFixed, testFractionalBits)

            checkClose(atanhOutDouble, expectedAtanh, tolerance, "Atanh output mismatch")
            checkClose(magnitudeOutDouble, expectedMagnitude, tolerance, "Magnitude output mismatch")

            dut.clock.step(1)
            dut.io.output1.ready.poke(false.B)
            dut.io.output2.ready.poke(false.B)
          }
        }
      }
    }
  }
} 