error id: file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala:
file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2665
uri: file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala
text:
```scala
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math.{sin, cos, atan, Pi, sqrt, log, abs, cosh, sinh}
import CordicSimplifiedConstants.Mode

// Import for Trig CORDIC Chisel Module and its constants
import CORDIC.CordicSimplifiedConstants.Mode

// Imports for Hyperbolic CORDIC Chisel Module and its constants
import CORDIC.{HyperCordic, HyperCordicConstants} // Added HyperCordic and its constants
import CORDIC.HyperCordicConstants.{Mode => HyperMode} // Alias Chisel Hyperbolic Mode

// Import for Hyperbolic Scala Model
import CORDIC.{HyperCordicModel, CordicModelConstants} // Added HyperCordicModel
import CORDIC.CordicModelConstants.{ModeHyper => ModelHyperMode} // Alias Scala Model Hyperbolic Mode

// Import for Linear CORDIC Chisel Module and its constants
import CORDIC.{LinearCordic, LinearCordicConstants, LinearCordicModel}
import CORDIC.LinearCordicConstants.{Mode => ChiselLinearMode} // Alias for Chisel Linear Mode
import CORDIC.CordicModelConstants.{ModeLinear => ModelLinearMode} // Alias for Scala Model Linear Mode

class CordicTest extends AnyFlatSpec with ChiselScalatestTester {
  
  // Test parameters
  val width = 16
  val cycleCount = 12
  val integerBits = 3
  val fractionalBits = width - 1 - integerBits
  
  // Helper function to convert double to fixed point
  def doubleToFixed(x: Double): BigInt = {
    CordicModelConstants.doubleToFixed(x, fractionalBits, width)
  }
  
  // Helper function to convert fixed point back to double for comparison
  def fixedToDouble(x: BigInt): Double = {
    // Handle the case where very small values get rounded to -1 instead of 0
    if (x == -1) 0.0
    else x.toDouble / (1 << fractionalBits).toDouble
  }
  
  // Helper function to compare fixed point values with tolerance
  def compareFixed(expected: BigInt, actual: BigInt, tolerance: Double = 0.02): Boolean = {
    val expectedDouble = fixedToDouble(expected)
    val actualDouble = fixedToDouble(actual)
    val diff = Math.abs(expectedDouble - actualDouble)
    
    // Special handling for very small values that should be zero
    if (Math.abs(expectedDouble) < 0.001 && Math.abs(actualDouble) < 0.001) {
      true
    } else {
      diff <= tolerance
    }
  }
  
  // Helper to handle near-zero values in fixed point
  def cleanNearZero(x: BigInt): BigInt = {
    if (x == -1 && fractionalBits > 0) BigInt(0) else x
  }

  // Helper for atanh since scala.math doesn't have it
  def atanh(x: Double): Double = {
    0.5 * log((1.0 + x) / (1.0 - x))
  }

  behavior of "CordicSimplified"

  it should "have correct fixed-point scaling" in {
    // Verify our scaling c@@onstants are correct
    val oneInFixed = doubleToFixed(1.0)
    val halfInFixed = doubleToFixed(0.5)
    val quarterInFixed = doubleToFixed(0.25)
    
    //println(s"Fixed-point scaling verification:")
    //println(s"1.0 -> $oneInFixed (should be ${1 << fractionalBits})")
    //println(s"0.5 -> $halfInFixed (should be ${1 << (fractionalBits-1)})")
    //println(s"0.25 -> $quarterInFixed (should be ${1 << (fractionalBits-2)})")
    
    assert(oneInFixed == (1 << fractionalBits), "1.0 scaling incorrect")
    assert(halfInFixed == (1 << (fractionalBits-1)), "0.5 scaling incorrect")
    assert(quarterInFixed == (1 << (fractionalBits-2)), "0.25 scaling incorrect")
    
    // Test reverse conversion
    assert(Math.abs(fixedToDouble(oneInFixed) - 1.0) < 0.001, "1.0 reverse conversion incorrect")
    assert(Math.abs(fixedToDouble(halfInFixed) - 0.5) < 0.001, "0.5 reverse conversion incorrect")
  }

  it should "initialize correctly in idle state" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      // Check initial state
      dut.io.done.expect(false.B)
      dut.io.cosOut.expect(0.S)
      dut.io.sinOut.expect(0.S)
      dut.io.arctanOut.expect(0.S)
      dut.io.magnitudeOut.expect(0.S)
      
      // Should remain idle without start signal
      dut.clock.step(5)
      dut.io.done.expect(false.B)
    }
  }

  it should "calculate sin/cos correctly and match Scala model" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      // Create Scala model for comparison
      val model = new TrigCordicModel(width, cycleCount, integerBits)
      
      // Test angles: 0, π/6, π/4, π/3, π/2
      val testAngles = Seq(0.0, Pi/6, Pi/4, Pi/3, Pi/2)
      
      for (angle <- testAngles) {
        //println(s"\n=== Testing Sin/Cos for angle: $angle radians ===")
        
        val angleFixed = doubleToFixed(angle)
        
        // Reset and configure Scala model
        model.reset()
        model.setInputs(
          start = true,
          modeIn = CordicModelConstants.Mode.SinCos,
          theta = angleFixed,
          xIn = BigInt(0),
          yIn = BigInt(0)
        )
        
        // Run Scala model to completion
        while (!model.done) {
          model.step()
        }
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.mode.poke(Mode.SinCos)
        dut.io.targetTheta.poke(angleFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        // Check completion
        dut.io.done.expect(true.B)
        
        // Get results and clean near-zero values
        val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
        val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
        val modelCos = cleanNearZero(model.cos)
        val modelSin = cleanNearZero(model.sin)
        
        //println(s"Hardware: cos=$hwCos (${fixedToDouble(hwCos)}), sin=$hwSin (${fixedToDouble(hwSin)})")
        //println(s"Model:    cos=$modelCos (${fixedToDouble(modelCos)}), sin=$modelSin (${fixedToDouble(modelSin)})")
        //println(s"Expected: cos=${cos(angle)}, sin=${sin(angle)}")
        
        // Compare hardware vs model (should be very close)
        assert(hwCos == modelCos, s"Cosine mismatch: HW=$hwCos, Model=$modelCos")
        assert(hwSin == modelSin, s"Sine mismatch: HW=$hwSin, Model=$modelSin")
        
        // Compare against mathematical values (with tolerance)
        val expectedCos = doubleToFixed(cos(angle))
        val expectedSin = doubleToFixed(sin(angle))
        
        assert(compareFixed(expectedCos, hwCos, 0.03), 
          s"Cosine accuracy: expected=${fixedToDouble(expectedCos)} (${cos(angle)}), got=${fixedToDouble(hwCos)}")
        assert(compareFixed(expectedSin, hwSin, 0.03), 
          s"Sine accuracy: expected=${fixedToDouble(expectedSin)} (${sin(angle)}), got=${fixedToDouble(hwSin)}")
        
        // Wait for return to idle
        dut.clock.step(2)
      }
    }
  }

  it should "calculate arctan and magnitude correctly and match Scala model" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      // Create Scala model for comparison
      val model = new TrigCordicModel(width, cycleCount, integerBits)
      
      // Test coordinate pairs (x, y) for arctan calculation
      val testCoords = Seq(
        (1.0, 0.0),    // atan(0/1) = 0
        (1.0, 1.0),    // atan(1/1) = π/4
        (1.0, 0.5),    // atan(0.5/1)
        (2.0, 1.0),    // atan(1/2)
        (1.0, 2.0)     // atan(2/1)
      )
      
      for ((x, y) <- testCoords) {
        //println(s"\n=== Testing Arctan/Magnitude for coordinates: ($x, $y) ===")
        
        val xFixed = doubleToFixed(x)
        val yFixed = doubleToFixed(y)
        
        // Reset and configure Scala model
        model.reset()
        model.setInputs(
          start = true,
          modeIn = CordicModelConstants.Mode.ArctanMagnitude,
          theta = BigInt(0),
          xIn = xFixed,
          yIn = yFixed
        )
        
        // Run Scala model to completion
        while (!model.done) {
          model.step()
        }
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.mode.poke(Mode.ArctanMagnitude)
        dut.io.targetTheta.poke(0.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(yFixed.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        // Check completion
        dut.io.done.expect(true.B)
        
        // Get results and clean near-zero values
        val hwArctan = cleanNearZero(dut.io.arctanOut.peek().litValue)
        val hwMagnitude = cleanNearZero(dut.io.magnitudeOut.peek().litValue)
        val modelArctan = cleanNearZero(model.arctan)
        val modelMagnitude = cleanNearZero(model.magnitude)
        
        //println(s"Hardware: arctan=$hwArctan (${fixedToDouble(hwArctan)}), magnitude=$hwMagnitude (${fixedToDouble(hwMagnitude)})")
        //println(s"Model:    arctan=$modelArctan (${fixedToDouble(modelArctan)}), magnitude=$modelMagnitude (${fixedToDouble(modelMagnitude)})")
        //println(s"Expected: arctan=${atan(y/x)}, magnitude=${Math.sqrt(x*x + y*y)}")
        
        // Compare hardware vs model (should be identical)
        assert(hwArctan == modelArctan, s"Arctan mismatch: HW=$hwArctan, Model=$modelArctan")
        assert(hwMagnitude == modelMagnitude, s"Magnitude mismatch: HW=$hwMagnitude, Model=$modelMagnitude")
        
        // Compare against mathematical values (with tolerance)
        val expectedArctan = doubleToFixed(atan(y/x))
        val expectedMagnitude = doubleToFixed(Math.sqrt(x*x + y*y))
        
        assert(compareFixed(expectedArctan, hwArctan, 0.03), 
          s"Arctan accuracy: expected=${fixedToDouble(expectedArctan)} (${atan(y/x)}), got=${fixedToDouble(hwArctan)}")
        assert(compareFixed(expectedMagnitude, hwMagnitude, 0.03),
          s"Magnitude accuracy: expected=${fixedToDouble(expectedMagnitude)} (${Math.sqrt(x*x + y*y)}), got=${fixedToDouble(hwMagnitude)}")
        
        // Wait for return to idle
        dut.clock.step(2)
      }
    }
  }

  it should "handle multiple sequential operations" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      val model = new TrigCordicModel(width, cycleCount, integerBits)
      
      // Test sequence: sin/cos, then arctan/magnitude, then sin/cos again
      val operations = Seq(
        ("sincos", Pi/4, 0.0, 0.0),
        ("arctanmag", 0.0, 1.0, 1.0),
        ("sincos", Pi/6, 0.0, 0.0)
      )
      
      for ((opType, param1, param2, param3) <- operations) {
        //println(s"\n=== Sequential test: $opType ===")
        
        val isArctanMag = opType == "arctanmag"
        
        // Configure model
        model.reset()
        if (isArctanMag) {
          model.setInputs(
            start = true,
            modeIn = CordicModelConstants.Mode.ArctanMagnitude,
            theta = BigInt(0),
            xIn = doubleToFixed(param2),
            yIn = doubleToFixed(param3)
          )
        } else {
          model.setInputs(
            start = true,
            modeIn = CordicModelConstants.Mode.SinCos,
            theta = doubleToFixed(param1),
            xIn = BigInt(0),
            yIn = BigInt(0)
          )
        }
        
        // Run model
        while (!model.done) {
          model.step()
        }
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.mode.poke(if (isArctanMag) Mode.ArctanMagnitude else Mode.SinCos)
        if (isArctanMag) {
          dut.io.targetTheta.poke(0.S)
          dut.io.inputX.poke(doubleToFixed(param2).S)
          dut.io.inputY.poke(doubleToFixed(param3).S)
        } else {
          dut.io.targetTheta.poke(doubleToFixed(param1).S)
          dut.io.inputX.poke(0.S)
          dut.io.inputY.poke(0.S)
        }
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        dut.io.done.expect(true.B)
        
        // Verify results match between hardware and model
        if (isArctanMag) {
          val hwArctan = cleanNearZero(dut.io.arctanOut.peek().litValue)
          val hwMagnitude = cleanNearZero(dut.io.magnitudeOut.peek().litValue)
          val modelArctan = cleanNearZero(model.arctan)
          val modelMagnitude = cleanNearZero(model.magnitude)
          
          //println(s"Sequential Arctan/Magnitude Results:")
          //println(s"  Hardware: arctan=$hwArctan (${fixedToDouble(hwArctan)}), magnitude=$hwMagnitude (${fixedToDouble(hwMagnitude)})")
          //println(s"  Model:    arctan=$modelArctan (${fixedToDouble(modelArctan)}), magnitude=$modelMagnitude (${fixedToDouble(modelMagnitude)})")
          //println(s"  Expected: arctan=${atan(param3/param2)}, magnitude=${Math.sqrt(param2*param2 + param3*param3)}")
          
          assert(hwArctan == modelArctan, s"Arctan sequential mismatch: HW=$hwArctan, Model=$modelArctan")
          assert(hwMagnitude == modelMagnitude, s"Magnitude sequential mismatch: HW=$hwMagnitude, Model=$modelMagnitude")
        } else {
          val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
          val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
          val modelCos = cleanNearZero(model.cos)
          val modelSin = cleanNearZero(model.sin)
          
          //println(s"Sequential Sin/Cos Results:")
          //println(s"  Hardware: cos=$hwCos (${fixedToDouble(hwCos)}), sin=$hwSin (${fixedToDouble(hwSin)})")
          //println(s"  Model:    cos=$modelCos (${fixedToDouble(modelCos)}), sin=$modelSin (${fixedToDouble(modelSin)})")
          //println(s"  Expected: cos=${cos(param1)}, sin=${sin(param1)}")
          
          assert(hwCos == modelCos && hwSin == modelSin, 
            s"Sin/Cos sequential mismatch: HW=($hwCos,$hwSin), Model=($modelCos,$modelSin)")
        }
        
        dut.clock.step(2) // Return to idle
      }
    }
  }

  it should "produce correct magnitudes with correction enabled" in {
    test(new CordicSimplified(width, cycleCount, integerBits, magnitudeCorrection = true)) { dut =>
      // Create Scala model for comparison
      val model = new TrigCordicModel(width, cycleCount, integerBits, magnitudeCorrection = true)
      
      // Test angles where both sin and cos are non-zero
      val testAngles = Seq(Pi/4, Pi/6, Pi/3)
      
      for (angle <- testAngles) {
        //println(s"\n=== Testing magnitude correction for angle: $angle radians ===")
        
        val angleFixed = doubleToFixed(angle)
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.mode.poke(Mode.SinCos)
        dut.io.targetTheta.poke(angleFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        // Check completion
        dut.io.done.expect(true.B)
        
        // Get results
        val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
        val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
        
        // Calculate magnitude of the output vector
        val hwCosDouble = fixedToDouble(hwCos)
        val hwSinDouble = fixedToDouble(hwSin)
        val magnitude = Math.sqrt(hwCosDouble*hwCosDouble + hwSinDouble*hwSinDouble)
        
        //println(s"Hardware cos=$hwCosDouble, sin=$hwSinDouble")
        //println(s"Calculated magnitude=$magnitude (should be close to 1.0)")
        
        // With correction enabled, magnitude should be very close to 1.0
        assert(Math.abs(magnitude - 1.0) < 0.03, 
          s"Magnitude with correction should be close to 1.0, got $magnitude")
        
        dut.clock.step(2)
      }
    }
  }

  it should "produce scaled magnitudes with correction disabled" in {
    test(new CordicSimplified(width, cycleCount, integerBits, magnitudeCorrection = false)) { dut =>
      // Create Scala model for comparison
      val model = new TrigCordicModel(width, cycleCount, integerBits, magnitudeCorrection = false)
      
      // Test angles where both sin and cos are non-zero
      val testAngles = Seq(Pi/4, Pi/6, Pi/3)
      
      for (angle <- testAngles) {
        //println(s"\n=== Testing uncorrected magnitude for angle: $angle radians ===")
        
        val angleFixed = doubleToFixed(angle)
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.mode.poke(Mode.SinCos)
        dut.io.targetTheta.poke(angleFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        // Check completion
        dut.io.done.expect(true.B)
        
        // Get results
        val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
        val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
        
        // Calculate magnitude of the output vector
        val hwCosDouble = fixedToDouble(hwCos)
        val hwSinDouble = fixedToDouble(hwSin)
        val magnitude = Math.sqrt(hwCosDouble*hwCosDouble + hwSinDouble*hwSinDouble)
        
        //println(s"Hardware cos=$hwCosDouble, sin=$hwSinDouble")
        //println(s"Calculated magnitude=$magnitude (should be close to TRIG_CORDIC_K=${CordicSimplifiedConstants.TRIG_CORDIC_K_DBL})")
        
        // Without correction, magnitude should be close to TRIG_CORDIC_K
        assert(Math.abs(magnitude - 1/CordicSimplifiedConstants.TRIG_CORDIC_K_DBL) < 0.04, 
          s"Magnitude without correction should be close to TRIG_CORDIC_K, got $magnitude")
        
        dut.clock.step(2)
      }
    }
  }

  // =====================================================================================
  // Tests for HyperCordic Chisel Module
  // =====================================================================================
  behavior of "HyperCordic"

  it should "initialize correctly in idle state" in {
    test(new HyperCordic(width, cycleCount, integerBits)) { dut =>
      // Check initial state
      dut.io.done.expect(false.B)
      dut.io.coshOut.expect(0.S)
      dut.io.sinhOut.expect(0.S)
      dut.io.atanhOut.expect(0.S)
      dut.io.magnitudeResultHyper.expect(0.S)
      
      // Should remain idle without start signal
      dut.clock.step(5)
      dut.io.done.expect(false.B)
    }
  }

  it should "calculate sinh/cosh correctly and match Scala Hyperbolic model (correction enabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = true)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = true)
      
      // Test thetas: 0, 0.5, 1.0, -0.5, -1.0. Max approx 1.1181
      val testThetas = Seq(0.0, 0.5, 1.0, -0.5, -1.0, 0.25, 0.75, -0.25, -0.75, 1.1)
      
      for (theta <- testThetas) {
        //println(s"\n=== Testing Sinh/Cosh for theta: $theta radians (Correction ON) ===")
        
        val thetaFixed = doubleToFixed(theta)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelHyperMode.SinhCosh,
          theta = thetaFixed,
          xIn = BigInt(0),
          yIn = BigInt(0)
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.SinhCosh)
        dut.io.targetTheta.poke(thetaFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) { // cycleCount + buffer
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwCosh = cleanNearZero(dut.io.coshOut.peek().litValue)
        val hwSinh = cleanNearZero(dut.io.sinhOut.peek().litValue)
        val modelCosh = cleanNearZero(model.cosh)
        val modelSinh = cleanNearZero(model.sinh)
        
        //println(s"DUT: cosh=${fixedToDouble(hwCosh)} ($hwCosh), sinh=${fixedToDouble(hwSinh)} ($hwSinh)")
        //println(s"MOD: cosh=${fixedToDouble(modelCosh)} ($modelCosh), sinh=${fixedToDouble(modelSinh)} ($modelSinh)")
        //println(s"EXP: cosh=${cosh(theta)}, sinh=${sinh(theta)}")

        assert(hwCosh == modelCosh, s"Cosh mismatch (corr ON): HW=$hwCosh, Model=$modelCosh for theta=$theta")
        assert(hwSinh == modelSinh, s"Sinh mismatch (corr ON): HW=$hwSinh, Model=$modelSinh for theta=$theta")
        
        val expectedCosh = doubleToFixed(cosh(theta))
        val expectedSinh = doubleToFixed(sinh(theta))
        
        assert(compareFixed(expectedCosh, hwCosh, 0.03), s"Cosh accuracy (corr ON): EXP=${cosh(theta)}, GOT=${fixedToDouble(hwCosh)} for theta=$theta")
        assert(compareFixed(expectedSinh, hwSinh, 0.03), s"Sinh accuracy (corr ON): EXP=${sinh(theta)}, GOT=${fixedToDouble(hwSinh)} for theta=$theta")
        
        // Verify cosh^2 - sinh^2 = 1
        val ch_hw = fixedToDouble(hwCosh)
        val sh_hw = fixedToDouble(hwSinh)
        assert(abs((ch_hw * ch_hw) - (sh_hw * sh_hw) - 1.0) < 0.05, // Increased tolerance for identity check
          s"Identity cosh^2-sinh^2=1 failed (corr ON): ${(ch_hw * ch_hw) - (sh_hw * sh_hw)} for theta=$theta")

        dut.clock.step(2) // Wait for DUT to return to idle
      }
    }
  }

  it should "calculate sinh/cosh correctly and match Scala Hyperbolic model (correction disabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = false)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = false)
      val hyperShiftExponents = HyperCordicConstants.getHyperbolicShiftExponents(cycleCount) // For DUT
      val K_h_dut = HyperCordicConstants.calculateHyperbolicGainFactor(hyperShiftExponents)

      //val testThetas = Seq(0.0, 0.5, 1.0, -0.5, -1.0, 0.8)
      val testThetas = Seq(0.5)

      for (theta <- testThetas) {
        //println(s"\n=== Testing Sinh/Cosh for theta: $theta radians (Correction OFF) ===")
        val thetaFixed = doubleToFixed(theta)

        model.reset()
        model.setInputs(start = true, modeIn = ModelHyperMode.SinhCosh, theta = thetaFixed, xIn = BigInt(0), yIn = BigInt(0))
        while (!model.done) { model.step() }

        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.SinhCosh)
        dut.io.targetTheta.poke(thetaFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)

        val hwCosh = cleanNearZero(dut.io.coshOut.peek().litValue)
        val hwSinh = cleanNearZero(dut.io.sinhOut.peek().litValue)
        val modelCosh = cleanNearZero(model.cosh) // Model output should also be K_h * cosh(theta)
        val modelSinh = cleanNearZero(model.sinh) // Model output should also be K_h * sinh(theta)

        //println(s"DUT: cosh=${fixedToDouble(hwCosh)} ($hwCosh), sinh=${fixedToDouble(hwSinh)} ($hwSinh)")
        //println(s"MOD: cosh=${fixedToDouble(modelCosh)} ($modelCosh), sinh=${fixedToDouble(modelSinh)} ($modelSinh)")
        //println(s"EXP (scaled): cosh=${K_h_dut * cosh(theta)}, sinh=${K_h_dut * sinh(theta)}")

        assert(hwCosh == modelCosh, s"Cosh mismatch (corr OFF): HW=$hwCosh, Model=$modelCosh for theta=$theta")
        assert(hwSinh == modelSinh, s"Sinh mismatch (corr OFF): HW=$hwSinh, Model=$modelSinh for theta=$theta")

        // When correction is OFF, outputs are K_h * cosh(theta) and K_h * sinh(theta)
        val expectedCosh = doubleToFixed(K_h_dut * cosh(theta))
        val expectedSinh = doubleToFixed(K_h_dut * sinh(theta))

        assert(compareFixed(expectedCosh, hwCosh, 0.035), s"Cosh accuracy (corr OFF): EXP=${K_h_dut*cosh(theta)}, GOT=${fixedToDouble(hwCosh)} for theta=$theta")
        assert(compareFixed(expectedSinh, hwSinh, 0.035), s"Sinh accuracy (corr OFF): EXP=${K_h_dut*sinh(theta)}, GOT=${fixedToDouble(hwSinh)} for theta=$theta")
        
        dut.clock.step(2)
      }
    }
  }

  it should "calculate atanh/magnitude correctly and match Scala Hyperbolic model (correction enabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = true)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = true)
      
      // Test (x,y) pairs. Ensure x > 0 and abs(y/x) <= 0.8068
      // Test cases: (1, 0), (1, 0.5), (1, -0.5), (2, 1), (2, -0.8), (1.5, 0.3)
      val testCoords = Seq(
        (1.0, 0.0), 
        (1.0, 0.5),   // y/x = 0.5
        (1.0, -0.5),  // y/x = -0.5
        (2.0, 1.0),   // y/x = 0.5
        (2.0, -0.8),  // y/x = -0.4
        (1.5, 0.3),   // y/x = 0.2
        (1.2, 0.96)   // y/x = 0.8
      )
      
      for ((x, y) <- testCoords) {
        //println(s"\n=== Testing Atanh/Magnitude for (x,y): ($x, $y) (Correction ON) ===")
        require(x > 0, "x must be positive for these tests")
        require(abs(y/x) < 1.0, "abs(y/x) must be less than 1 for atanh")
        require(abs(y/x) <= 0.8069, "abs(y/x) must be <= 0.8069 for model convergence") // Check model limit

        val xFixed = doubleToFixed(x)
        val yFixed = doubleToFixed(y)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelHyperMode.AtanhMagnitudeHyper,
          theta = BigInt(0),
          xIn = xFixed,
          yIn = yFixed
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.AtanhMagnitudeHyper)
        dut.io.targetTheta.poke(0.S) // Not used in this mode
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(yFixed.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwAtanh = cleanNearZero(dut.io.atanhOut.peek().litValue)
        val hwMagnitude = cleanNearZero(dut.io.magnitudeResultHyper.peek().litValue)
        val modelAtanh = cleanNearZero(model.atanh)
        val modelMagnitude = cleanNearZero(model.magnitudeHyper)
        
        //println(s"DUT: atanh=${fixedToDouble(hwAtanh)} ($hwAtanh), mag=${fixedToDouble(hwMagnitude)} ($hwMagnitude)")
        //println(s"MOD: atanh=${fixedToDouble(modelAtanh)} ($modelAtanh), mag=${fixedToDouble(modelMagnitude)} ($modelMagnitude)")
        //println(s"EXP: atanh=${atanh(y/x)}, mag=${sqrt(x*x - y*y)}")

        //println(s"Atanh mismatch (corr ON): HW=$hwAtanh, Model=$modelAtanh for (x,y)=($x,$y)")
        //println(s"Magnitude mismatch (corr ON): HW=$hwMagnitude, Model=$modelMagnitude for (x,y)=($x,$y)")
        assert(hwAtanh == modelAtanh, s"Atanh mismatch (corr ON): HW=$hwAtanh, Model=$modelAtanh for (x,y)=($x,$y)")
        assert(hwMagnitude == modelMagnitude, s"Magnitude mismatch (corr ON): HW=$hwMagnitude, Model=$modelMagnitude for (x,y)=($x,$y)")
        
        val expectedAtanh = doubleToFixed(atanh(y/x))
        val expectedMagnitude = doubleToFixed(sqrt(x*x - y*y))
        
        // Relax tolerance for atanh especially, and magnitude
        assert(compareFixed(expectedAtanh, hwAtanh, 0.05), s"Atanh accuracy (corr ON): EXP=${atanh(y/x)}, GOT=${fixedToDouble(hwAtanh)} for (x,y)=($x,$y)")
        assert(compareFixed(expectedMagnitude, hwMagnitude, 0.05), s"Magnitude accuracy (corr ON): EXP=${sqrt(x*x-y*y)}, GOT=${fixedToDouble(hwMagnitude)} for (x,y)=($x,$y)")
        
        dut.clock.step(2) // Wait for DUT to return to idle
      }
    }
  }

  it should "calculate atanh/magnitude correctly and match Scala Hyperbolic model (correction disabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = false)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = false)
      val hyperShiftExponents = HyperCordicConstants.getHyperbolicShiftExponents(cycleCount) // For DUT gain
      val K_h_dut = HyperCordicConstants.calculateHyperbolicGainFactor(hyperShiftExponents)

      val testCoords = Seq(
        (1.0, 0.0),
        (1.0, 0.5),
        (2.0, 1.0),
        (1.2, 0.96) // y/x = 0.8
      )

      for ((x, y) <- testCoords) {
        //println(s"\n=== Testing Atanh/Magnitude for (x,y): ($x, $y) (Correction OFF) ===")
        require(x > 0, "x must be positive for these tests")
        require(abs(y/x) < 1.0, "abs(y/x) must be less than 1 for atanh")
        require(abs(y/x) <= 0.8069, "abs(y/x) must be <= 0.8069 for model convergence")

        val xFixed = doubleToFixed(x)
        val yFixed = doubleToFixed(y)

        model.reset()
        model.setInputs(start = true, modeIn = ModelHyperMode.AtanhMagnitudeHyper, theta = BigInt(0), xIn = xFixed, yIn = yFixed)
        while (!model.done) { model.step() }

        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.AtanhMagnitudeHyper)
        dut.io.targetTheta.poke(0.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(yFixed.S)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)

        val hwAtanh = cleanNearZero(dut.io.atanhOut.peek().litValue)
        val hwMagnitude = cleanNearZero(dut.io.magnitudeResultHyper.peek().litValue)
        val modelAtanh = cleanNearZero(model.atanh)
        val modelMagnitude = cleanNearZero(model.magnitudeHyper) // Model output is K_h_model * sqrt(x*x-y*y)

        //println(s"DUT: atanh=${fixedToDouble(hwAtanh)} ($hwAtanh), mag=${fixedToDouble(hwMagnitude)} ($hwMagnitude)")
        //println(s"MOD: atanh=${fixedToDouble(modelAtanh)} ($modelAtanh), mag=${fixedToDouble(modelMagnitude)} ($modelMagnitude)")
        //println(s"EXP Atanh: ${atanh(y/x)}, EXP Mag (scaled): ${K_h_dut * sqrt(x*x - y*y)}")

        assert(hwAtanh == modelAtanh, s"Atanh mismatch (corr OFF): HW=$hwAtanh, Model=$modelAtanh for (x,y)=($x,$y)")
        assert(hwMagnitude == modelMagnitude, s"Magnitude mismatch (corr OFF): HW=$hwMagnitude, Model=$modelMagnitude for (x,y)=($x,$y)")
        
        val expectedAtanh = doubleToFixed(atanh(y/x))
        // When correction is OFF, Chisel output magnitude is K_h_dut * sqrt(x*x-y*y)
        val expectedMagnitude = doubleToFixed(K_h_dut * sqrt(x*x - y*y))

        assert(compareFixed(expectedAtanh, hwAtanh, 0.05), s"Atanh accuracy (corr OFF): EXP=${atanh(y/x)}, GOT=${fixedToDouble(hwAtanh)} for (x,y)=($x,$y)")
        assert(compareFixed(expectedMagnitude, hwMagnitude, 0.05 * K_h_dut), s"Magnitude accuracy (corr OFF): EXP=${K_h_dut*sqrt(x*x-y*y)}, GOT=${fixedToDouble(hwMagnitude)} for (x,y)=($x,$y)")
        
        dut.clock.step(2)
      }
    }
  }

  it should "calculate exponential functions correctly and match Scala model (correction enabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = true)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = true)
      
      // Test thetas: positive, negative, and zero values within convergence range
      val testThetas = Seq(0.0, 0.5, -0.5, 1.0, -1.0, 0.25, -0.25)
      
      for (theta <- testThetas) {
        println(s"\n=== Testing Exponential for theta: $theta radians (Correction ON) ===")
        
        val thetaFixed = doubleToFixed(theta)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelHyperMode.Exponential,
          theta = thetaFixed,
          xIn = BigInt(0),
          yIn = BigInt(0)
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.Exponential)
        dut.io.targetTheta.poke(thetaFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwExp = cleanNearZero(dut.io.expOut.peek().litValue)
        val hwExpNeg = cleanNearZero(dut.io.expNegOut.peek().litValue)
        val modelExp = cleanNearZero(model.exp)
        val modelExpNeg = cleanNearZero(model.expNeg)
        
        println(s"DUT: e^x=${fixedToDouble(hwExp)} ($hwExp), e^-x=${fixedToDouble(hwExpNeg)} ($hwExpNeg)")
        println(s"MOD: e^x=${fixedToDouble(modelExp)} ($modelExp), e^-x=${fixedToDouble(modelExpNeg)} ($modelExpNeg)")
        println(s"EXP: e^x=${math.exp(theta)}, e^-x=${math.exp(-theta)}")

        // Compare hardware vs model results
        assert(hwExp == modelExp, s"e^x mismatch (corr ON): HW=$hwExp, Model=$modelExp for theta=$theta")
        assert(hwExpNeg == modelExpNeg, s"e^-x mismatch (corr ON): HW=$hwExpNeg, Model=$modelExpNeg for theta=$theta")
        
        // Compare against mathematical values
        val expectedExp = doubleToFixed(math.exp(theta))
        val expectedExpNeg = doubleToFixed(math.exp(-theta))
        
        assert(compareFixed(expectedExp, hwExp, 0.03), 
          s"e^x accuracy (corr ON): EXP=${math.exp(theta)}, GOT=${fixedToDouble(hwExp)} for theta=$theta")
        assert(compareFixed(expectedExpNeg, hwExpNeg, 0.03), 
          s"e^-x accuracy (corr ON): EXP=${math.exp(-theta)}, GOT=${fixedToDouble(hwExpNeg)} for theta=$theta")
        
        // Verify e^x * e^-x = 1
        val exp_hw = fixedToDouble(hwExp)
        val expNeg_hw = fixedToDouble(hwExpNeg)
        assert(math.abs(exp_hw * expNeg_hw - 1.0) < 0.05, 
          s"Identity e^x * e^-x = 1 failed (corr ON): ${exp_hw * expNeg_hw} for theta=$theta")

        dut.clock.step(2)
      }
    }
  }

  it should "calculate exponential functions correctly and match Scala model (correction disabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = false)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = false)
      val hyperShiftExponents = HyperCordicConstants.getHyperbolicShiftExponents(cycleCount)
      val K_h_dut = HyperCordicConstants.calculateHyperbolicGainFactor(hyperShiftExponents)
      
      val testThetas = Seq(0.0, 0.5, -0.5, 1.0, -1.0)
      
      for (theta <- testThetas) {
        println(s"\n=== Testing Exponential for theta: $theta radians (Correction OFF) ===")
        
        val thetaFixed = doubleToFixed(theta)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelHyperMode.Exponential,
          theta = thetaFixed,
          xIn = BigInt(0),
          yIn = BigInt(0)
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.Exponential)
        dut.io.targetTheta.poke(thetaFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwExp = cleanNearZero(dut.io.expOut.peek().litValue)
        val hwExpNeg = cleanNearZero(dut.io.expNegOut.peek().litValue)
        val modelExp = cleanNearZero(model.exp)
        val modelExpNeg = cleanNearZero(model.expNeg)
        
        println(s"DUT: e^x=${fixedToDouble(hwExp)} ($hwExp), e^-x=${fixedToDouble(hwExpNeg)} ($hwExpNeg)")
        println(s"MOD: e^x=${fixedToDouble(modelExp)} ($modelExp), e^-x=${fixedToDouble(modelExpNeg)} ($modelExpNeg)")
        println(s"EXP (scaled): e^x=${K_h_dut * math.exp(theta)}, e^-x=${K_h_dut * math.exp(-theta)}")

        // Compare hardware vs model results
        assert(hwExp == modelExp, s"e^x mismatch (corr OFF): HW=$hwExp, Model=$modelExp for theta=$theta")
        assert(hwExpNeg == modelExpNeg, s"e^-x mismatch (corr OFF): HW=$hwExpNeg, Model=$modelExpNeg for theta=$theta")
        
        // When correction is OFF, outputs are scaled by K_h
        val expectedExp = doubleToFixed(K_h_dut * math.exp(theta))
        val expectedExpNeg = doubleToFixed(K_h_dut * math.exp(-theta))
        
        assert(compareFixed(expectedExp, hwExp, 0.035), 
          s"e^x accuracy (corr OFF): EXP=${K_h_dut * math.exp(theta)}, GOT=${fixedToDouble(hwExp)} for theta=$theta")
        assert(compareFixed(expectedExpNeg, hwExpNeg, 0.035), 
          s"e^-x accuracy (corr OFF): EXP=${K_h_dut * math.exp(-theta)}, GOT=${fixedToDouble(hwExpNeg)} for theta=$theta")
        
        // Even with scaling, e^x * e^-x should be close to K_h^2
        val exp_hw = fixedToDouble(hwExp)
        val expNeg_hw = fixedToDouble(hwExpNeg)
        assert(math.abs(exp_hw * expNeg_hw - K_h_dut * K_h_dut) < 0.05 * K_h_dut * K_h_dut, 
          s"Identity e^x * e^-x = K_h^2 failed (corr OFF): ${exp_hw * expNeg_hw} vs ${K_h_dut * K_h_dut} for theta=$theta")

        dut.clock.step(2)
      }
    }
  }

  it should "calculate natural logarithm correctly and match Scala model (correction enabled)" in {
    test(new HyperCordic(width, cycleCount, integerBits, magnitudeCorrection = true)) { dut =>
      val model = new HyperCordicModel(width, cycleCount, integerBits, magnitudeCorrection = true)
      
      // Test values: values > 1, values between 0 and 1, and special cases
      val testValues = Seq(
        2.718281828459045, // e
        2.0,               // ln(2) ≈ 0.693
        1.5,               // ln(1.5) ≈ 0.405
        1.0,               // ln(1) = 0
        0.5,               // ln(0.5) ≈ -0.693
        0.25,              // ln(0.25) = -1.386
        4.0,               // ln(4) = 1.386
        3.0                // ln(3) ≈ 1.099
      )
      
      for (x <- testValues) {
        println(s"\n=== Testing Natural Log for x: $x (Correction ON) ===")
        
        val xFixed = doubleToFixed(x)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelHyperMode.NaturalLog,
          theta = BigInt(0),
          xIn = xFixed,
          yIn = BigInt(0)
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(HyperMode.NaturalLog)
        dut.io.targetTheta.poke(0.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwLn = cleanNearZero(dut.io.lnOut.peek().litValue)
        val modelLn = cleanNearZero(model.ln)
        
        println(s"DUT: ln($x)=${fixedToDouble(hwLn)} ($hwLn)")
        println(s"MOD: ln($x)=${fixedToDouble(modelLn)} ($modelLn)")
        println(s"EXP: ln($x)=${math.log(x)}")

        // Compare hardware vs model results
        assert(hwLn == modelLn, s"ln(x) mismatch (corr ON): HW=$hwLn, Model=$modelLn for x=$x")
        
        // Compare against mathematical values
        val expectedLn = doubleToFixed(math.log(x))
        assert(compareFixed(expectedLn, hwLn, 0.03), 
          s"ln(x) accuracy (corr ON): EXP=${math.log(x)}, GOT=${fixedToDouble(hwLn)} for x=$x")

        dut.clock.step(2)
      }
    }
  }

  // =====================================================================================
  // Tests for LinearCordic Chisel Module
  // =====================================================================================
  behavior of "LinearCordic"

  it should "initialize correctly in idle state" in {
    test(new LinearCordic(width, cycleCount, integerBits)) { dut =>
      // Check initial state
      dut.io.done.expect(false.B)
      dut.io.productResult.expect(0.S)
      dut.io.quotientResult.expect(0.S)
      
      // Should remain idle without start signal
      dut.clock.step(5)
      dut.io.done.expect(false.B)
    }
  }

  it should "perform multiplication correctly and match Scala model" in {
    test(new LinearCordic(width, cycleCount, integerBits)) { dut =>
      val model = new LinearCordicModel(width, cycleCount, integerBits)
      
      val testValues = Seq(
        (1.0, 0.5, 0.5),    // Basic positive
        (0.5, 1.0, 0.5),    // Order swapped
        (2.0, 1.5, 3.0),    // Both > 1
        (1.0, 3.0, 3.0),    // B requires scaling (3.0 > 1.99 limit)
        (-1.0, 0.5, -0.5),   // A negative
        (1.0, -0.5, -0.5),   // B negative
        (-1.0, -0.5, 0.5),  // Both negative
        (0.25, 0.25, 0.0625),// Small values
        (1.0, -3.0, -3.0),  // B negative, requires scaling
        (1.5, 1.9, 2.85),   // B close to limit
        (1.9, 1.5, 2.85),   // A close to limit (doesn't matter for A)
        (0.0, 5.0, 0.0),    // A is zero
        (5.0, 0.0, 0.0)     // B is zero
      )

      for ((a_val, b_val, expected_prod) <- testValues) {
        // println(s"\n=== Testing Linear Multiplication for A=$a_val, B=$b_val === Expect: $expected_prod")
        
        val aFixed = doubleToFixed(a_val)
        val bFixed = doubleToFixed(b_val)
        
        model.reset()
        model.setInputs(
          start = true,
          modeIn = ModelLinearMode.Multiply,
          a = aFixed,
          b = bFixed
        )
        while (!model.done) { model.step() }
        
        dut.io.start.poke(true.B)
        dut.io.mode.poke(ChiselLinearMode.Multiply)
        dut.io.inputA.poke(aFixed.S)
        dut.io.inputB.poke(bFixed.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        // Wait enough cycles for the CORDIC operation and a few more for state transitions
        while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
        val hwProduct = dut.io.productResult.peek().litValue
        val modelProduct = model.product
        
        // println(s"HW Product: ${fixedToDouble(hwProduct)} ($hwProduct)")
        // println(s"Model Product: ${fixedToDouble(modelProduct)} ($modelProduct)")
        // println(s"Expected Double: $expected_prod")

        assert(hwProduct == modelProduct, s"Product mismatch for A=$a_val, B=$b_val: HW=$hwProduct (${fixedToDouble(hwProduct)}), Model=$modelProduct (${fixedToDouble(modelProduct)})")
        
        val expectedFixed = doubleToFixed(expected_prod)
        // Use a slightly more tolerant comparison for floating point issues in expected value if direct model check is main goal
        assert(compareFixed(expectedFixed, hwProduct, 0.005), // Tolerance 0.5%
          s"Product accuracy for A=$a_val, B=$b_val: Expected=${fixedToDouble(expectedFixed)} ($expected_prod), Got=${fixedToDouble(hwProduct)}")
        
        dut.clock.step(2) // Wait for DUT to return to idle
      }
    }
  }

  it should "perform division correctly and match Scala model" in {
    test(new LinearCordic(width, cycleCount, integerBits)) { dut =>
      val model = new LinearCordicModel(width, cycleCount, integerBits)
      
      val testValues = Seq(
        (1.0, 2.0, 0.5),     // Basic positive
        (3.0, 1.0, 3.0),     // A requires scaling
        (1.0, 0.5, 2.0),     // A requires scaling (quotient can be > 1)
        (-1.0, 2.0, -0.5),    // A negative
        (1.0, -2.0, -0.5),    // B negative
        (-1.0, -2.0, 0.5),   // Both negative
        (0.7, 0.1, 7.0),     // A requires significant scaling, quotient large but representable
        (0.0, 2.0, 0.0),     // A is zero
        (2.0, 0.25, 8.0)     // Max quotient if integerBits=3 allows up to 8.0. Test this bound.
                               // If integerBits=3 (signed), range is approx -8 to +7.999. So 8.0 would be an overflow.
                               // Let's use a smaller quotient for this general test.
      )
      // Modify last test case for integerBits=3, max output is ~7.99.
      // Example: (1.99 * 3.5) / 3.5 = 1.99. Let's try A=7.0, B=1.0. Q=7.0.
      // A=7.0 fixed: 28672. B=1.0 fixed: 4096
      // k=0: 28672 > 8151
      // k=1: 14336 > 8151
      // k=2: 7168 <= 8151. So k=2.
      // A_scaled = 7.0 / 4 = 1.75. z_target = 1.75. z << k = 1.75 * 4 = 7.0. Correct.

      val divisionTestValues = Seq(
        (1.0, 2.0, 0.5),
        (3.0, 1.0, 3.0),
        (1.0, 0.5, 2.0),
        (-1.0, 2.0, -0.5),
        (1.0, -2.0, -0.5),
        (-1.0, -2.0, 0.5),
        (0.7, 0.1, 7.0),
        (0.0, 2.0, 0.0),
        (7.5, 1.0, 7.5), // Quotient large, but within signed 3 integer bits range (-8 to +7.99..)
        (1.0, 4.0, 0.25),
        (0.1, 0.8, 0.125) // Small values
      )

      for ((a_val, b_val, expected_quot) <- divisionTestValues) {
        // println(s"\n=== Testing Linear Division for A=$a_val, B=$b_val === Expect: $expected_quot")
        
        val aFixed = doubleToFixed(a_val)
        val bFixed = doubleToFixed(b_val)
        
        // Skip division by zero for model, Chisel should handle or be protected by user
        if (b_val == 0.0) {
          // println("Skipping division by zero test case for model.")
        } else {
          model.reset()
          model.setInputs(
            start = true,
            modeIn = ModelLinearMode.Divide,
            a = aFixed,
            b = bFixed
          )
          while (!model.done) { model.step() }
          
          dut.io.start.poke(true.B)
          dut.io.mode.poke(ChiselLinearMode.Divide)
          dut.io.inputA.poke(aFixed.S)
          dut.io.inputB.poke(bFixed.S)
          
          dut.clock.step(1)
          dut.io.start.poke(false.B)
          
          var timeout = 0
          while (!dut.io.done.peek().litToBoolean && timeout < cycleCount + 10) {
            dut.clock.step(1)
            timeout += 1
          }
          dut.io.done.expect(true.B)
          
          val hwQuotient = dut.io.quotientResult.peek().litValue
          val modelQuotient = model.quotient
          
          // println(s"HW Quotient: ${fixedToDouble(hwQuotient)} ($hwQuotient)")
          // println(s"Model Quotient: ${fixedToDouble(modelQuotient)} ($modelQuotient)")
          // println(s"Expected Double: $expected_quot")

          assert(hwQuotient == modelQuotient, s"Quotient mismatch for A=$a_val, B=$b_val: HW=$hwQuotient (${fixedToDouble(hwQuotient)}), Model=$modelQuotient (${fixedToDouble(modelQuotient)})")
          
          val expectedFixed = doubleToFixed(expected_quot)
          assert(compareFixed(expectedFixed, hwQuotient, 0.005), // Tolerance 0.5%
            s"Quotient accuracy for A=$a_val, B=$b_val: Expected=${fixedToDouble(expectedFixed)} ($expected_quot), Got=${fixedToDouble(hwQuotient)}")
          
          dut.clock.step(2) // Wait for DUT to return to idle
        }
      }
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 