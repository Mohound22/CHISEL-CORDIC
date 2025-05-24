error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala:_empty_/trigScalaModel#iterateTowards().(trueXYfalseTheta)
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -trueXYfalseTheta.
	 -trueXYfalseTheta#
	 -trueXYfalseTheta().
	 -scala/Predef.trueXYfalseTheta.
	 -scala/Predef.trueXYfalseTheta#
	 -scala/Predef.trueXYfalseTheta().
offset: 476
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.Decoupled

abstract class baseScalaModel {
  val k: Double = 1.0
}

/*  This class takes in an angle in radians and outputs and the sin and cosine of the angle*/
class trigScalaModel(val cycleCount: Int) extends baseScalaModel {

  def iterateTowards(
      targetTheta: Double,
      inputX: Double,
      inputY: Double,
      inputTheta: Double,
      trueArcTanfalseSinCos: Boolean
  ): (Double, Double, Double) = {
    if (trueXYf@@alseTheta) {
      // math.atan()
      // return (sin,cos,arctan)
      var xPrime = inputX
      var yPrime = inputY
      var totalTheta: Double = 0
      for (i <- 0 until cycleCount) {
        val deltaTheta = math.atan(math.pow(2, -i))
        xPrime = xPrime - math.tan(deltaTheta) * yPrime
        yPrime = math.tan(deltaTheta) * xPrime + yPrime
      }
    } else {
      var xPrime = inputX


    }
    ???
  }

  // Calculate sine of the current angle (theta)
  def getMathSin(targetTheta: Double): Double = math.sin(targetTheta)

  // Calculate cosine of the current angle (theta)
  def getMathCos(targetTheta: Double): Double = math.cos(targetTheta)

  // Additional method to get (sin, cos) as a tuple
  // def getSinCos: (Double, Double) = (getSin, getCos)

  // override def toString: String =
  // f"trigScalaModel(x=$x%.2f, y=$y%.2f, θ=$theta%.2f, sinθ=${getSin}%.2f, cosθ=${getCos}%.2f, cycles=$cycleCount)"
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 