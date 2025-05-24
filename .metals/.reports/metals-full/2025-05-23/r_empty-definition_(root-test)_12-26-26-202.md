error id: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala:_empty_/TrigScalaModelTest#
file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol _empty_/TrigScalaModelTest#
empty definition using fallback
non-local guesses:

offset: 130
uri: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
text:
```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CORDIC._


class TrigScalaModelTest@@ extends AnyFlatSpec with Matchers {
  val precision = 1e-4 // Precision for floating point comparisons
  val testCycles = 32 // Number of cycles to use in tests
  
  "trigScalaModel" should "correctly compute sine and cosine for 0 radians" in {
    val model = new trigScalaModel(testCycles)
    val (sin, cos, _) = model.iterateTowards(0.0, 0.0, 0.0, false)
    
    sin should be (0.0 +- precision)
    cos should be (model.k +- precision)
  }
  
  it should "correctly compute sine and cosine for π/4 radians" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi/4
    val (sin, cos, _) = model.iterateTowards(angle, 0.0, 0.0, false)
    
    sin should be (math.sin(angle) * model.k +- precision)
    cos should be (math.cos(angle) * model.k +- precision)
  }
  
  it should "correctly compute sine and cosine for π/6 radians" in {
    val model = new trigScalaModel(testCycles)
    val angle = math.Pi/6
    val (sin, cos, _) = model.iterateTowards(angle, 0.0, 0.0, false)
    
    sin should be (math.sin(angle) * model.k +- precision)
    cos should be (math.cos(angle) * model.k +- precision)
  }
  
  it should "correctly compute sine and cosine for negative angles" in {
    val model = new trigScalaModel(testCycles)
    val angle = -math.Pi/3
    val (sin, cos, _) = model.iterateTowards(angle, 0.0, 0.0, false)
    
    sin should be (math.sin(angle) * model.k +- precision)
    cos should be (math.cos(angle) * model.k +- precision)
  }
  
  it should "throw assertion error for angles outside [-π/2, π/2] in sin/cos mode" in {
    val model = new trigScalaModel(testCycles)
    an [AssertionError] should be thrownBy model.iterateTowards(math.Pi, 0.0, 0.0, false)
    an [AssertionError] should be thrownBy model.iterateTowards(-math.Pi, 0.0, 0.0, false)
  }
  
  it should "correctly compute arctangent for positive y/x" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = 1.0
    val (_, _, angle) = model.iterateTowards(0.0, x, y, true)
    
    angle should be (math.atan2(y, x) +- precision)
  }
  
  it should "correctly compute arctangent for negative y/x" in {
    val model = new trigScalaModel(testCycles)
    val x = 1.0
    val y = -1.0
    val (_, _, angle) = model.iterateTowards(0.0, x, y, true)
    
    angle should be (math.atan2(y, x) +- precision)
  }
  
  it should "throw assertion error for negative x in arctan mode" in {
    val model = new trigScalaModel(testCycles)
    an [AssertionError] should be thrownBy model.iterateTowards(0.0, -1.0, 1.0, true)
  }
  
  it should "show increasing accuracy with more cycles" in {
    val angles = List(0.1, 0.5, 1.0)
    
    for (angle <- angles) {
      val modelLow = new trigScalaModel(8)
      val modelHigh = new trigScalaModel(16)
      
      val (sinLow, cosLow, _) = modelLow.iterateTowards(angle, 0.0, 0.0, false)
      val (sinHigh, cosHigh, _) = modelHigh.iterateTowards(angle, 0.0, 0.0, false)
      
      val trueSin = math.sin(angle) * modelLow.k
      val trueCos = math.cos(angle) * modelLow.k
      
      val errorLow = math.abs(sinLow - trueSin) + math.abs(cosLow - trueCos)
      val errorHigh = math.abs(sinHigh - trueSin) + math.abs(cosHigh - trueCos)
      
      assert(errorHigh < errorLow, s"For angle $angle, higher cycles should have lower error")
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 