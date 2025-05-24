error id: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala:java/lang/AssertionError#
file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
empty definition using pc, found symbol in pc: java/lang/AssertionError#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -CORDIC.AssertionError#
	 -AssertionError#
	 -scala/Predef.AssertionError#
offset: 811
uri: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
text:
```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CORDIC._

class TrigScalaModelTestMutated extends AnyFlatSpec with Matchers {
  val precision = 1e-4 // Precision for floating point comparisons
  val testCycles = 16 // Number of cycles to use in tests

  // Helper function to calculate the CORDIC gain K_n for a specific number of cycles
  def calculateCordicGain(cycles: Int): Double = {
    (0 until cycles).map(i => math.sqrt(1.0 + math.pow(2.0, -2.0 * i))).product
  }

  val K_testCycles: Double = calculateCordicGain(testCycles) // CORDIC gain for 'testCycles' iterations

  
  it should "throw assertion error for angles outside [-π/2, π/2] in sin/cos mode when calling iterateTowards" in {
    val model = new scalaModelOneCycle(testCycles)
    an [Asserti@@onError] should be thrownBy model.iterateTowards(math.Pi, 0.0, 0.0, false)
    an [AssertionError] should be thrownBy model.iterateTowards(-math.Pi, 0.0, 0.0, false)
  }
  
  it should "correctly compute arctangent for positive y/x when calling iterateTowards" in {
    val model = new trigScalascalaModelOneCycleModel(testCycles)
    val x = 1.0
    val y = 1.0
    val (_, _, angle_out) = model.iterateTowards(0.0, x, y, true) // angle_out is totalTheta
    
    angle_out should be (math.atan2(y, x) +- precision)
  }
  
  it should "correctly compute arctangent for negative y/x when calling iterateTowards" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = -1.0
    val (_, _, angle_out) = model.iterateTowards(0.0, x, y, true)
    
    angle_out should be (math.atan2(y, x) +- precision)
  }
  
  it should "throw assertion error for negative x in arctan mode when calling iterateTowards" in {
    val model = new trigScalaModel(testCycles)
    an [AssertionError] should be thrownBy model.iterateTowards(0.0, -1.0, 1.0, true)
  }

  // --- New tests for trigScalaModel.calcSinCos ---
  "trigScalaModel.calcSinCos" should "correctly compute sin=0 and cos=1 for 0 radians with gain correction" in {
    val model = new trigScalaModel(testCycles)
    val (sin, cos) = model.calcSinCos(0.0, gainCorrection = true)
    sin should be(math.sin(0.0) +- precision)
    cos should be(math.cos(0.0) +- precision)
  }

  it should "correctly compute sin and cos for π/4 radians with gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi / 4
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = true)
    sin should be(math.sin(angle) +- precision)
    cos should be(math.cos(angle) +- precision)
  }

  it should "correctly compute sin and cos for π/6 radians with gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi / 6
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = true)
    sin should be(math.sin(angle) +- precision)
    cos should be(math.cos(angle) +- precision)
  }

  it should "correctly compute sin and cos for -π/3 radians with gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = -math.Pi / 3
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = true)
    sin should be(math.sin(angle) +- precision)
    cos should be(math.cos(angle) +- precision)
  }

  it should "correctly compute sin=0 and cos=K_n for 0 radians without gain correction" in {
    val model = new trigScalaModel(testCycles)
    val (sin, cos) = model.calcSinCos(0.0, gainCorrection = false)
    sin should be(K_testCycles * math.sin(0.0) +- precision)
    cos should be(K_testCycles * math.cos(0.0) +- precision)
  }

  it should "correctly compute K_n*sin and K_n*cos for π/4 radians without gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi / 4
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = false)
    sin should be(K_testCycles * math.sin(angle) +- precision)
    cos should be(K_testCycles * math.cos(angle) +- precision)
  }

  it should "correctly compute K_n*sin and K_n*cos for π/6 radians without gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi / 6
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = false)
    sin should be(K_testCycles * math.sin(angle) +- precision)
    cos should be(K_testCycles * math.cos(angle) +- precision)
  }

  it should "correctly compute K_n*sin and K_n*cos for -π/3 radians without gain correction" in {
    val model = new trigScalaModel(testCycles)
    val angle = -math.Pi / 3
    val (sin, cos) = model.calcSinCos(angle, gainCorrection = false)
    sin should be(K_testCycles * math.sin(angle) +- precision)
    cos should be(K_testCycles * math.cos(angle) +- precision)
  }

  it should "throw assertion error for angles outside [-π/2, π/2] when calling calcSinCos" in {
    val model = new trigScalaModel(testCycles)
    an [AssertionError] should be thrownBy model.calcSinCos(math.Pi, gainCorrection = true)
    an [AssertionError] should be thrownBy model.calcSinCos(-math.Pi, gainCorrection = false)
  }

  // --- New tests for trigScalaModel.calcArcTan ---
  "trigScalaModel.calcArcTan" should "correctly compute arctangent for (1,1)" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = 1.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }

  it should "correctly compute arctangent for (1,-1)" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = -1.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }

  it should "correctly compute arctangent for (1,0)" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = 0.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }

  it should "correctly compute arctangent for (0,1)" in {
    val model = new trigScalaModel(testCycles)
    val x = 0.0 
    val y = 1.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }
  
  it should "correctly compute arctangent for (0,-1)" in {
    val model = new trigScalaModel(testCycles)
    val x = 0.0 
    val y = -1.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }

  it should "correctly compute arctangent for (0,0)" in {
    val model = new trigScalaModel(testCycles)
    val x = 0.0
    val y = 0.0
    val angle = model.calcArcTan(x, y)
    angle should be(math.atan2(y, x) +- precision)
  }
  
  it should "throw assertion error for negative x when calling calcArcTan" in {
    val model = new trigScalaModel(testCycles)
    an [AssertionError] should be thrownBy model.calcArcTan(-1.0, 1.0)
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/lang/AssertionError#