package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math.{sin, cos, atan, Pi}
import CordicSimplifiedConstants.Mode

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
    if (x == -1) BigInt(0) else x
  }

  behavior of "CordicSimplified"

  it should "have correct fixed-point scaling" in {
    // Verify our scaling constants are correct
    val oneInFixed = doubleToFixed(1.0)
    val halfInFixed = doubleToFixed(0.5)
    val quarterInFixed = doubleToFixed(0.25)
    
    println(s"Fixed-point scaling verification:")
    println(s"1.0 -> $oneInFixed (should be ${1 << fractionalBits})")
    println(s"0.5 -> $halfInFixed (should be ${1 << (fractionalBits-1)})")
    println(s"0.25 -> $quarterInFixed (should be ${1 << (fractionalBits-2)})")
    
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
        println(s"\n=== Testing Sin/Cos for angle: $angle radians ===")
        
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
        
        println(s"Hardware: cos=$hwCos (${fixedToDouble(hwCos)}), sin=$hwSin (${fixedToDouble(hwSin)})")
        println(s"Model:    cos=$modelCos (${fixedToDouble(modelCos)}), sin=$modelSin (${fixedToDouble(modelSin)})")
        println(s"Expected: cos=${cos(angle)}, sin=${sin(angle)}")
        
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
        println(s"\n=== Testing Arctan/Magnitude for coordinates: ($x, $y) ===")
        
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
        
        println(s"Hardware: arctan=$hwArctan (${fixedToDouble(hwArctan)}), magnitude=$hwMagnitude (${fixedToDouble(hwMagnitude)})")
        println(s"Model:    arctan=$modelArctan (${fixedToDouble(modelArctan)}), magnitude=$modelMagnitude (${fixedToDouble(modelMagnitude)})")
        println(s"Expected: arctan=${atan(y/x)}, magnitude=${Math.sqrt(x*x + y*y)}")
        
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
        println(s"\n=== Sequential test: $opType ===")
        
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
          
          println(s"Sequential Arctan/Magnitude Results:")
          println(s"  Hardware: arctan=$hwArctan (${fixedToDouble(hwArctan)}), magnitude=$hwMagnitude (${fixedToDouble(hwMagnitude)})")
          println(s"  Model:    arctan=$modelArctan (${fixedToDouble(modelArctan)}), magnitude=$modelMagnitude (${fixedToDouble(modelMagnitude)})")
          println(s"  Expected: arctan=${atan(param3/param2)}, magnitude=${Math.sqrt(param2*param2 + param3*param3)}")
          
          assert(hwArctan == modelArctan, s"Arctan sequential mismatch: HW=$hwArctan, Model=$modelArctan")
          assert(hwMagnitude == modelMagnitude, s"Magnitude sequential mismatch: HW=$hwMagnitude, Model=$modelMagnitude")
        } else {
          val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
          val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
          val modelCos = cleanNearZero(model.cos)
          val modelSin = cleanNearZero(model.sin)
          
          println(s"Sequential Sin/Cos Results:")
          println(s"  Hardware: cos=$hwCos (${fixedToDouble(hwCos)}), sin=$hwSin (${fixedToDouble(hwSin)})")
          println(s"  Model:    cos=$modelCos (${fixedToDouble(modelCos)}), sin=$modelSin (${fixedToDouble(modelSin)})")
          println(s"  Expected: cos=${cos(param1)}, sin=${sin(param1)}")
          
          assert(hwCos == modelCos && hwSin == modelSin, 
            s"Sin/Cos sequential mismatch: HW=($hwCos,$hwSin), Model=($modelCos,$modelSin)")
        }
        
        dut.clock.step(2) // Return to idle
      }
    }
  }
}