error id: file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala:inputY
file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala
empty definition using pc, found symbol in pc: inputY
found definition using semanticdb; symbol chiseltest/package.testableSInt().
empty definition using fallback
non-local guesses:

offset: 6961
uri: file://<WORKSPACE>/src/test/scala/CORDIC/scalaCHISELtest.scala
text:
```scala
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math.{sin, cos, atan, Pi}

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
      
      // Should remain idle without start signal
      dut.clock.step(5)
      dut.io.done.expect(false.B)
    }
  }

  it should "calculate sin/cos correctly and match Scala model" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      // Create Scala model for comparison
      val model = new CordicModel(width, cycleCount, integerBits)
      
      // Test angles: 0, π/6, π/4, π/3, π/2
      val testAngles = Seq(0.0, Pi/6, Pi/4, Pi/3, Pi/2)
      
      for (angle <- testAngles) {
        println(s"\n=== Testing Sin/Cos for angle: $angle radians ===")
        
        val angleFixed = doubleToFixed(angle)
        
        // Reset and configure Scala model
        model.reset()
        model.setInputs(
          start = true,
          arctanMode = false,
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
        dut.io.doArctan.poke(false.B)
        dut.io.targetTheta.poke(angleFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        while (!dut.io.done.peek().litToBoolean) {
          dut.clock.step(1)
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

  it should "calculate arctan correctly and match Scala model" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      // Create Scala model for comparison
      val model = new CordicModel(width, cycleCount, integerBits)
      
      // Test coordinate pairs (x, y) for arctan calculation
      val testCoords = Seq(
        (1.0, 0.0),    // atan(0/1) = 0
        (1.0, 1.0),    // atan(1/1) = π/4
        (1.0, 0.5),    // atan(0.5/1)
        (2.0, 1.0),    // atan(1/2)
        (1.0, 2.0)     // atan(2/1)
      )
      
      for ((x, y) <- testCoords) {
        println(s"\n=== Testing Arctan for coordinates: ($x, $y) ===")
        
        val xFixed = doubleToFixed(x)
        val yFixed = doubleToFixed(y)
        
        // Reset and configure Scala model
        model.reset()
        model.setInputs(
          start = true,
          arctanMode = true,
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
        dut.io.doArctan.poke(true.B)
        dut.io.targetTheta.poke(0.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.i@@nputY.poke(yFixed.S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        // Wait for completion
        while (!dut.io.done.peek().litToBoolean) {
          dut.clock.step(1)
        }
        
        // Check completion
        dut.io.done.expect(true.B)
        
        // Get results and clean near-zero values
        val hwArctan = cleanNearZero(dut.io.arctanOut.peek().litValue)
        val modelArctan = cleanNearZero(model.arctan)
        
        println(s"Hardware: arctan=$hwArctan (${fixedToDouble(hwArctan)})")
        println(s"Model:    arctan=$modelArctan (${fixedToDouble(modelArctan)})")
        println(s"Expected: arctan=${atan(y/x)}")
        
        // Compare hardware vs model (should be identical)
        assert(hwArctan == modelArctan, s"Arctan mismatch: HW=$hwArctan, Model=$modelArctan")
        
        // Compare against mathematical value (with tolerance)
        val expectedArctan = doubleToFixed(atan(y/x))
        assert(compareFixed(expectedArctan, hwArctan, 0.03), 
          s"Arctan accuracy: expected=${fixedToDouble(expectedArctan)} (${atan(y/x)}), got=${fixedToDouble(hwArctan)}")
        
        // Wait for return to idle
        dut.clock.step(2)
      }
    }
  }

  it should "handle multiple sequential operations" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      val model = new CordicModel(width, cycleCount, integerBits)
      
      // Test sequence: sin/cos, then arctan, then sin/cos again
      val operations = Seq(
        ("sincos", Pi/4, 0.0, 0.0),
        ("arctan", 0.0, 1.0, 1.0),
        ("sincos", Pi/6, 0.0, 0.0)
      )
      
      for ((opType, param1, param2, param3) <- operations) {
        println(s"\n=== Sequential test: $opType ===")
        
        val isArctan = opType == "arctan"
        
        // Configure model
        model.reset()
        if (isArctan) {
          model.setInputs(true, true, BigInt(0), doubleToFixed(param2), doubleToFixed(param3))
        } else {
          model.setInputs(true, false, doubleToFixed(param1), BigInt(0), BigInt(0))
        }
        
        // Run model
        while (!model.done) {
          model.step()
        }
        
        // Configure hardware
        dut.io.start.poke(true.B)
        dut.io.doArctan.poke(isArctan.B)
        if (isArctan) {
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
        if (isArctan) {
          val hwResult = cleanNearZero(dut.io.arctanOut.peek().litValue)
          val modelResult = cleanNearZero(model.arctan)
          assert(hwResult == modelResult, s"Arctan sequential mismatch: HW=$hwResult, Model=$modelResult")
        } else {
          val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
          val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
          val modelCos = cleanNearZero(model.cos)
          val modelSin = cleanNearZero(model.sin)
          assert(hwCos == modelCos && hwSin == modelSin, 
            s"Sin/Cos sequential mismatch: HW=($hwCos,$hwSin), Model=($modelCos,$modelSin)")
        }
        
        dut.clock.step(2) // Return to idle
      }
    }
  }

  it should "handle edge cases correctly" in {
    test(new CordicSimplified(width, cycleCount, integerBits)) { dut =>
      val model = new CordicModel(width, cycleCount, integerBits)
      
      // Test edge cases
      val edgeCases = Seq(
        ("zero_angle", false, 0.0, 0.0, 0.0),
        ("small_angle", false, 0.1, 0.0, 0.0),
        ("unity_vector", true, 0.0, 1.0, 0.0),
        ("small_arctan", true, 0.0, 1.0, 0.1)
      )
      
      for ((testName, isArctan, theta, x, y) <- edgeCases) {
        println(s"\n=== Edge case test: $testName ===")
        
        // Test model
        model.reset()
        model.setInputs(true, isArctan, doubleToFixed(theta), doubleToFixed(x), doubleToFixed(y))
        while (!model.done) {
          model.step()
        }
        
        // Test hardware
        dut.io.start.poke(true.B)
        dut.io.doArctan.poke(isArctan.B)
        dut.io.targetTheta.poke(doubleToFixed(theta).S)
        dut.io.inputX.poke(doubleToFixed(x).S)
        dut.io.inputY.poke(doubleToFixed(y).S)
        
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        
        dut.io.done.expect(true.B)
        
        // Verify consistency
        if (isArctan) {
          val hwResult = cleanNearZero(dut.io.arctanOut.peek().litValue)
          val modelResult = cleanNearZero(model.arctan)
          assert(hwResult == modelResult)
        } else {
          val hwCos = cleanNearZero(dut.io.cosOut.peek().litValue)
          val hwSin = cleanNearZero(dut.io.sinOut.peek().litValue)
          val modelCos = cleanNearZero(model.cos)
          val modelSin = cleanNearZero(model.sin)
          assert(hwCos == modelCos)
          assert(hwSin == modelSin)
        }
        
        dut.clock.step(2)
      }
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: inputY