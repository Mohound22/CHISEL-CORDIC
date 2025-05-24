error id: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTestChisel.scala:targetTheta
file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTestChisel.scala
empty definition using pc, found symbol in pc: targetTheta
found definition using semanticdb; symbol chiseltest/package.testableSInt().
empty definition using fallback
non-local guesses:

offset: 1053
uri: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTestChisel.scala
text:
```scala
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.ChiselEnum

class CordicSimplifiedTester extends AnyFlatSpec with ChiselScalatestTester {
  "CordicSimplified" should "run without asserts" in {
    test(new CordicSimplified(width = 16, cycleCount = 14, integerBits = 3)) { dut =>
      // Helper function to convert Double to SInt representation
      def doubleToSInt(value: Double, fractionalBits: Int): BigInt = {
        val scaled = value * (1 << fractionalBits)
        BigInt(scaled.round.toLong)
      }

      val fractionalBits = 12 // width - 1 - integerBits = 16 - 1 - 3 = 12
      
      // Initialize inputs
      dut.io.start.poke(false.B)
      dut.io.opModeIsArctan.poke(false.B)
      dut.io.targetTheta.poke(0.S)
      dut.io.inputX.poke(0.S)
      dut.io.inputY.poke(0.S)
      
      // Reset cycle
      dut.clock.step()
      
      // Test rotation mode (sin/cos)
      dut.io.start.poke(true.B)
      dut.io.opModeIsArctan.poke(false.B)
      dut.io.targ@@etTheta.poke(doubleToSInt(0.785398, fractionalBits).S) // ~π/4 (45 degrees)
      
      dut.clock.step()
      dut.io.start.poke(false.B)
      
      // Wait for completion
      while (dut.io.done.peek().litToBoolean == false) {
        dut.clock.step()
      }
      
      // Outputs should now be available
      println(s"cosOut: ${dut.io.cosOut.peek().litValue}")
      println(s"sinOut: ${dut.io.sinOut.peek().litValue}")
      println(s"arctanOut: ${dut.io.arctanOut.peek().litValue}")

      dut.clock.step()
      
      // Test vectoring mode (arctan)
      dut.io.start.poke(true.B)
      dut.io.doArctan.poke(true.B)
      dut.io.inputX.poke(doubleToSInt(1.0, fractionalBits).S)
      dut.io.inputY.poke(doubleToSInt(1.0, fractionalBits).S)
      
      dut.clock.step()
      dut.io.start.poke(false.B)
      
      // Wait for completion
      while (dut.io.done.peek().litToBoolean == false) {
        dut.clock.step()
      }
      
      // Outputs should now be available
      println(s"arctanOut: ${dut.io.arctanOut.peek().litValue}")
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: targetTheta