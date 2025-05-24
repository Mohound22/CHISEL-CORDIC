error id: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala:
file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1023
uri: file://<WORKSPACE>/src/test/scala/CORDIC/CORDICTest.scala
text:
```scala
package CORDIC

object CORDICTest {
  def main(args: Array[String]): Unit = {
    // Create an instance with 10 iterations (cycleCount)
    val cordic = new trigScalaModel(10)
    
    println("=== Testing Sin/Cos Mode ===")
    testSinCos(cordic, math.Pi/4)  // 45 degrees
    testSinCos(cordic, math.Pi/6)  // 30 degrees
    testSinCos(cordic, -math.Pi/3) // -60 degrees
    
    println("\n=== Testing Arctan Mode ===")
    testArctan(cordic, 1.0, 1.0)   // 45 degrees (y/x = 1)
    testArctan(cordic, math.sqrt(3), 1.0) // 30 degrees
    testArctan(cordic, 1.0, math.sqrt(3)) // 60 degrees
  }
  
  def testSinCos(cordic: trigScalaModel, angle: Double): Unit = {
    println(s"\nCalculating sin/cos for angle: $angle radians")
    println("Iteration | x (cos)      | y (sin)      | Current Angle")
    println("--------------------------------------------------------")
    
    val (x, y, _) = cordic.iterateTowards(angle, 0, 0, false)
    println("\nFinal Results:")
    println(f"Calculated cos: ${x}%12.8f (Actual: @@${math.cos(angle)}%12.8f)")
    println(f"Calculated sin: ${y}%12.8f (Actual: ${math.sin(angle)}%12.8f)")
    println(f"Error cos: ${math.abs(x - math.cos(angle))}%12.8f")
    println(f"Error sin: ${math.abs(y - math.sin(angle))}%12.8f")
  }
  
  def testArctan(cordic: trigScalaModel, x: Double, y: Double): Unit = {
    println(s"\nCalculating arctan for y/x = $y/$x (${y/x})")
    println("Iteration | x'           | y'           | Current Angle")
    println("--------------------------------------------------------")
    
    // Modified version that prints intermediate values
    var xPrime = x
    var yPrime = y
    var totalTheta: Double = 0
    
    for (i <- 0 until cordic.cycleCount) {
      val deltaTheta = math.atan(math.pow(2, -i))
      
      if (yPrime < 0) {
        val newX = xPrime - yPrime / math.pow(2, i)
        val newY = yPrime + xPrime / math.pow(2, i)
        xPrime = newX
        yPrime = newY
        totalTheta += deltaTheta
      } else {
        val newX = xPrime + yPrime / math.pow(2, i)
        val newY = yPrime - xPrime / math.pow(2, i)
        xPrime = newX
        yPrime = newY
        totalTheta -= deltaTheta
      }
      
      println(f"$i%9d | ${xPrime}%12.8f | ${yPrime}%12.8f | ${totalTheta}%12.8f")
    }
    
    val (_, _, angle) = cordic.iterateTowards(0, x, y, true)
    val actualAngle = math.atan2(y, x)
    println("\nFinal Results:")
    println(f"Calculated angle: ${angle}%12.8f radians")
    println(f"Actual angle:     ${actualAngle}%12.8f radians")
    println(f"Error:            ${math.abs(angle - actualAngle)}%12.8f radians")
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 