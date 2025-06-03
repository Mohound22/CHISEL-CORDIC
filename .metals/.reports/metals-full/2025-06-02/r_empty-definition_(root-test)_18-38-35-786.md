error id: file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala:local197
file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol local197
empty definition using fallback
non-local guesses:

offset: 27444
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
    // Verify our scaling constants are correct
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
        
        println(s"DUT: cosh=${fixedToDouble(hwCosh)} ($hwCosh), sinh=${fixedToDouble(hwSinh)} ($hwSinh)")
        println(s"MOD: cosh=${fixedToDouble(modelCosh)} ($modelCosh), sinh=${fixedToDouble(modelSinh)} ($modelSinh)")
        println(s"EXP: cosh=${cosh(theta)}, sinh=${sinh(theta)}")

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

      val testThetas = Seq(0.0, 0.5, 1.0, -0.5, -1.0, 0.8)

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

        println(s"DUT: cosh=${fixedToDouble(hwCosh)} ($hwCosh), sinh=${fixedToDouble(hwSinh)} ($hwSinh)")
        println(s"MOD: cosh=${fixedToDouble(modelCosh)} ($modelCosh), sinh=${fixedToDouble(modelSinh)} ($modelSinh)")
        println(s"EXP (scaled): cosh=${K_h_dut * cosh(theta)}, sinh=${K_h_dut * sinh(theta)}")

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
        println(s"\n=== Testing Atanh/Magnitude for (x,y): ($x, $y) (Correction ON) ===")
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
        
        println(s"DUT: atanh=${fixedToDouble(hwAtanh)} ($hwAtanh), mag=${fixedToDouble(hwMagnitude)} ($hwMagnitude)")
        println(s"MOD: atanh=${fixedToDouble(modelAtanh)} ($modelAtanh), mag=${fixedToDouble(modelMagnitude)} ($modelMagnitude)")
        println(s"EXP: atanh=${atanh(y/x)}, mag=${sqrt(x*x - y*y)}")

        //println(s"")
        assert(hwAtanh == modelAtanh, s"Atanh mismatch (corr ON): HW=$hwAtanh, Model=$modelAtanh for (x,y)=($x,$y)")
        assert(hwMagnitude == modelMagnitude, s"Magnitude mismatch (corr ON): HW=$hwMagnitude, @@Model=$modelMagnitude for (x,y)=($x,$y)")
        
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
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 