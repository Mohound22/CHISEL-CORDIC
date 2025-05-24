error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala:CORDIC/
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 78
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.Decoupled

package CORDIC


abstract @@class baseScalaModel {
  val k: Double = 0.607253
}

/*  This class takes in an angle in radians and outputs and the sin and cosine of the angle*/
class trigScalaModel(val cycleCount: Int) extends baseScalaModel {

  def iterateTowards(
      targetTheta: Double,
      inputX: Double,
      inputY: Double,
      trueArcTanfalseSinCos: Boolean
  ): (Double, Double, Double) = { // return (sin,cos,arctan)
    if (trueArcTanfalseSinCos) { // Arctan, take in X and Y and spit out angle
      assert(inputX >= 0)

      // math.atan()
      
      var xPrime = inputX
      var yPrime = inputY
      var totalTheta: Double = 0
      for (i <- 0 until cycleCount) {
        val deltaTheta = math.atan(math.pow(2, -i))
        totalTheta += deltaTheta

        if (yPrime < 0) { // Positive angle added
          xPrime = xPrime - yPrime / math.pow(2, i) // RETURN AND USE SHIFTS INSTEAD (>> i)
          yPrime = yPrime + xPrime / math.pow(2, i)
        } else { // Negative angle added
          xPrime = xPrime + yPrime / math.pow(2, i)
          yPrime = yPrime - xPrime / math.pow(2, i)
        }

      }

      (0.0, 0.0, totalTheta) // Return total angle / Arctan

    } else { // Sin Cos, take in targetTheta and spit out sin cos
      assert((2 / math.Pi) >= targetTheta)
      assert(targetTheta >= -(2 / math.Pi))

      var xPrime: Double = 1
      var yPrime: Double = 0
      var totalTheta: Double = 0
      for (i <- 0 until cycleCount) {
        if (targetTheta > totalTheta) { // Positive angle added
          xPrime = xPrime - yPrime / math.pow(2, i)
          yPrime = yPrime + xPrime / math.pow(2, i)
        } else { // Negative angle added
          xPrime = xPrime + yPrime / math.pow(2, i)
          yPrime = yPrime - xPrime / math.pow(2, i)
        }
      }

      (xPrime, yPrime, totalTheta)
    }
  }

}
  // Calculate sine of the current angle (theta)
  //def getMathSin(targetTheta: Double): Double = math.sin(targetTheta)

  // Calculate cosine of the current angle (theta)
  //def getMathCos(targetTheta: Double): Double = math.cos(targetTheta)


  // override def toString: String =
  // f"trigScalaModel(x=$x%.2f, y=$y%.2f, θ=$theta%.2f, sinθ=${getSin}%.2f, cosθ=${getCos}%.2f, cycles=$cycleCount)"


```


#### Short summary: 

empty definition using pc, found symbol in pc: 