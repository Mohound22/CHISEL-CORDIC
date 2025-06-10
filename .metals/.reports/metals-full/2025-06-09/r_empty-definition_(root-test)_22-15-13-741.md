error id: file://<WORKSPACE>/src/test/scala/CORDIC/ChiselVsScalaModelTests.scala:start
file://<WORKSPACE>/src/test/scala/CORDIC/ChiselVsScalaModelTests.scala
empty definition using pc, found symbol in pc: start
found definition using semanticdb; symbol chiseltest/package.testableBool().
empty definition using fallback
non-local guesses:

offset: 7117
uri: file://<WORKSPACE>/src/test/scala/CORDIC/ChiselVsScalaModelTests.scala
text:
```scala
package CORDIC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.math._

trait ChiselVsScalaTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  val testWidth = 32
  val testCycles = 15
  val testIntegerBits = 3
  val testFractionalBits = testWidth - 1 - testIntegerBits
  val tolerance = 0.02

  def doubleToFixed(value: Double): BigInt =
    CordicModelConstants.doubleToFixed(value, testFractionalBits, testWidth)

  def fixedToDouble(value: BigInt): Double = {
    value.toDouble / (1L << testFractionalBits)
  }
}

class TrigChiselVsScalaTest extends ChiselVsScalaTester {

  def runTest(
      dut: CordicSimplified,
      model: TrigCordicModel,
      mode: CordicSimplifiedConstants.Mode.Type,
      angle: Double = 0.0,
      xIn: Double = 0.0,
      yIn: Double = 0.0
  ): Unit = {
        val angleFixed = doubleToFixed(angle)
    val xFixed = doubleToFixed(xIn)
    val yFixed = doubleToFixed(yIn)
        
        model.reset()
        model.setInputs(
          start = true,
      modeIn = if (mode == CordicSimplifiedConstants.Mode.SinCos) CordicModelConstants.Mode.SinCos else CordicModelConstants.Mode.ArctanMagnitude,
          theta = angleFixed,
          xIn = xFixed,
          yIn = yFixed
        )
    while (!model.done) model.step()

        dut.io.start.poke(true.B)
    dut.io.mode.poke(mode)
    dut.io.targetTheta.poke(angleFixed.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(yFixed.S)
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
        while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
    if (mode == CordicSimplifiedConstants.Mode.SinCos) {
      fixedToDouble(dut.io.cosOut.peek().litValue) should be (fixedToDouble(model.cos) +- tolerance)
      fixedToDouble(dut.io.sinOut.peek().litValue) should be (fixedToDouble(model.sin) +- tolerance)
        } else {
      fixedToDouble(dut.io.arctanOut.peek().litValue) should be (fixedToDouble(model.arctan) +- tolerance)
      fixedToDouble(dut.io.magnitudeOut.peek().litValue) should be (fixedToDouble(model.magnitude) +- tolerance)
    }
    dut.clock.step(2)
  }

  behavior of "TrigChiselVsScala"

  it should "calculate sin/cos correctly and match Scala model" in {
    test(new CordicSimplified(testWidth, testCycles, testIntegerBits)) { dut =>
      val model = new TrigCordicModel(testWidth, testCycles, testIntegerBits)
      val testAngles = Seq(0.0, Pi/6, Pi/4, Pi/3, Pi/2)
      for (angle <- testAngles) {
        runTest(dut, model, CordicSimplifiedConstants.Mode.SinCos, angle = angle)
      }
    }
  }

  it should "calculate arctan and magnitude correctly and match Scala model" in {
    test(new CordicSimplified(testWidth, testCycles, testIntegerBits)) { dut =>
      val model = new TrigCordicModel(testWidth, testCycles, testIntegerBits)
      val testCoords = Seq((1.0, 0.0), (1.0, 1.0), (1.0, 0.5))
      for ((x, y) <- testCoords) {
        runTest(dut, model, CordicSimplifiedConstants.Mode.ArctanMagnitude, xIn = x, yIn = y)
      }
    }
  }
}

class LinearChiselVsScalaTest extends ChiselVsScalaTester {
    def runTest(
      dut: LinearCordic,
      model: LinearCordicModel,
      mode: LinearCordicConstants.Mode.Type,
      aIn: Double,
      bIn: Double
  ): Unit = {
    val aFixed = doubleToFixed(aIn)
    val bFixed = doubleToFixed(bIn)
        
        model.reset()
        model.setInputs(
          start = true,
      modeIn = if (mode == LinearCordicConstants.Mode.Multiply) CordicModelConstants.ModeLinear.Multiply else CordicModelConstants.ModeLinear.Divide,
      a = aFixed,
      b = bFixed
    )
    while(!model.done) model.step()
        
        dut.io.start.poke(true.B)
    dut.io.mode.poke(mode)
    dut.io.inputA.poke(aFixed.S)
    dut.io.inputB.poke(bFixed.S)
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
        var timeout = 0
    while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
        dut.io.done.expect(true.B)
        
    if (mode == LinearCordicConstants.Mode.Multiply) {
        fixedToDouble(dut.io.productResult.peek().litValue) should be (fixedToDouble(model.product) +- tolerance)
    } else {
        fixedToDouble(dut.io.quotientResult.peek().litValue) should be (fixedToDouble(model.quotient) +- tolerance)
    }
        dut.clock.step(2)
  }

  behavior of "LinearChiselVsScala"

  it should "calculate multiplication correctly and match Scala model" in {
    test(new LinearCordic(testWidth, testCycles, testIntegerBits)) { dut =>
        val model = new LinearCordicModel(testWidth, testCycles, testIntegerBits)
        val testValues = Seq((2.5, 3.0), (-2.5, 3.0), (0.125, 0.5))
        for ((a,b) <- testValues) {
            runTest(dut, model, LinearCordicConstants.Mode.Multiply, a, b)
        }
    }
  }

  it should "calculate division correctly and match Scala model" in {
    test(new LinearCordic(testWidth, testCycles, testIntegerBits)) { dut =>
        val model = new LinearCordicModel(testWidth, testCycles, testIntegerBits)
        val testValues = Seq((7.5, 2.5), (-7.5, 2.5), (1.0, 4.0))
        for ((a,b) <- testValues) {
            runTest(dut, model, LinearCordicConstants.Mode.Divide, a, b)
        }
    }
  }
}

class HyperChiselVsScalaTest extends ChiselVsScalaTester {
    def runTest(
      dut: HyperCordic,
      model: HyperCordicModel,
      mode: HyperCordicConstants.Mode.Type,
      theta: Double = 0.0,
      xIn: Double = 0.0,
      yIn: Double = 0.0
  ): Unit = {
    println(s"\\n=== [Hyper Test] Mode: $mode, Theta: $theta, xIn: $xIn, yIn: $yIn ===")
        val thetaFixed = doubleToFixed(theta)
    val xFixed = doubleToFixed(xIn)
    val yFixed = doubleToFixed(yIn)
    println(s"Fixed-point inputs -> theta: $thetaFixed, x: $xFixed, y: $yFixed")
        
        model.reset()
        model.setInputs(
          start = true,
        modeIn = if (mode == HyperCordicConstants.Mode.SinhCosh) CordicModelConstants.ModeHyper.SinhCosh else CordicModelConstants.ModeHyper.AtanhMagnitudeHyper,
        theta = if (mode == HyperCordicConstants.Mode.SinhCosh) thetaFixed else BigInt(0),
        xIn = if (mode == HyperCordicConstants.Mode.AtanhMagnitudeHyper) xFixed else BigInt(0),
        yIn = if (mode == HyperCordicConstants.Mode.AtanhMagnitudeHyper) yFixed else BigInt(0)
        )
    while (!model.done) model.step()
    
    println("Scala model finished.")
    if (mode == HyperCordicConstants.Mode.SinhCosh) {
        println(s"  Model cosh: ${fixedToDouble(model.cosh)} (${model.cosh})")
        println(s"  Model sinh: ${fixedToDouble(model.sinh)} (${model.sinh})")
    } else {
        println(s"  Model atanh: ${fixedToDouble(model.atanh)} (${model.atanh})")
        println(s"  Model magnitude: ${fixedToDouble(model.magnitudeHyper)} (${model.magnitudeHyper})")
    }
        dut.io.mode.poke(mode)
        dut.io.s@@tart.poke(true.B)
    
    if (mode == HyperCordicConstants.Mode.SinhCosh) {
        dut.io.targetTheta.poke(thetaFixed.S)
        dut.io.inputX.poke(0.S)
        dut.io.inputY.poke(0.S)
    } else {
        dut.io.targetTheta.poke(0.S)
        dut.io.inputX.poke(xFixed.S)
        dut.io.inputY.poke(yFixed.S)
    }
        dut.clock.step(1)
        dut.io.start.poke(false.B)
        
    println("DUT started. Waiting for completion...")
        var timeout = 0
    while (!dut.io.done.peek().litToBoolean && timeout < 50) {
          dut.clock.step(1)
          timeout += 1
        }
    println(s"DUT finished in $timeout cycles. dut.io.done=${dut.io.done.peek().litToBoolean}")
        dut.io.done.expect(true.B)
        
    if (mode == HyperCordicConstants.Mode.SinhCosh) {
        val dutCosh = dut.io.coshOut.peek().litValue
        val dutSinh = dut.io.sinhOut.peek().litValue
        println(s"  DUT cosh: ${fixedToDouble(dutCosh)} ($dutCosh)")
        println(s"  DUT sinh: ${fixedToDouble(dutSinh)} ($dutSinh)")
        fixedToDouble(dutCosh) should be (fixedToDouble(model.cosh) +- tolerance)
        fixedToDouble(dutSinh) should be (fixedToDouble(model.sinh) +- tolerance)
    } else {
        val dutAtanh = dut.io.atanhOut.peek().litValue
        val dutMag = dut.io.magnitudeResultHyper.peek().litValue
        println(s"  DUT atanh: ${fixedToDouble(dutAtanh)} ($dutAtanh)")
        println(s"  DUT magnitude: ${fixedToDouble(dutMag)} ($dutMag)")
        fixedToDouble(dutAtanh) should be (fixedToDouble(model.atanh) +- tolerance)
        fixedToDouble(dutMag) should be (fixedToDouble(model.magnitudeHyper) +- tolerance)
    }
  }

  behavior of "HyperChiselVsScala"

  it should "calculate sinh/cosh correctly and match Scala model" in {
    test(new HyperCordic(testWidth, testCycles, testIntegerBits, magnitudeCorrection = true)) { dut =>
        val model = new HyperCordicModel(testWidth, testCycles, testIntegerBits, magnitudeCorrection = true)
        val testThetas = Seq(0.0, 0.5, 1.0, -0.5)
        for (theta <- testThetas) {
            runTest(dut, model, HyperCordicConstants.Mode.SinhCosh, theta = theta)
        }
    }
  }

  it should "calculate atanh/magnitude correctly and match Scala model" in {
    test(new HyperCordic(testWidth, testCycles, testIntegerBits, magnitudeCorrection = true)) { dut =>
        val model = new HyperCordicModel(testWidth, testCycles, testIntegerBits, magnitudeCorrection = true)
        val testCoords = Seq((1.2, 0.5), (2.0, 1.0), (1.5, -0.3))
        for ((x, y) <- testCoords) {
            if (x > abs(y)) {
                runTest(dut, model, HyperCordicConstants.Mode.AtanhMagnitudeHyper, xIn = x, yIn = y)
        }
      }
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: start