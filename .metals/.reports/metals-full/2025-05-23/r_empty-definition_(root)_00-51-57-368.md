error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala:local1
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol local1
empty definition using fallback
non-local guesses:

offset: 942
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.Decoupled

package CORDIC


abstract class baseScalaModel {
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
        

        if (yPrime < 0) { // Positive angle added
          val xOld = xPrime
          val yOld = yPrime

          xPrime = x_old - y_old@@ >> i // or x_old + y_old * term if y_old is negative and d is handled explicitly
          yPrime = y_old + x_old * term
          totalTheta += deltaTheta
        } else { // Negative angle added
          xPrime = xPrime + yPrime / math.pow(2, i)
          yPrime = yPrime - xPrime / math.pow(2, i)
          totalTheta -= deltaTheta
        }

        println(f"$i%9d | ${xPrime}%12.8f | ${yPrime}%12.8f | ${totalTheta}%12.8f")

      }

      (0.0, 0.0, totalTheta) // Return total angle / Arctan

    } else { // Sin Cos, take in targetTheta and spit out sin cos
      assert((math.Pi / 2) >= targetTheta)
      assert(targetTheta >= -(math.Pi / 2))
      
      var xPrime: Double = 1
      var yPrime: Double = 0
      var totalTheta: Double = 0
      for (i <- 0 until cycleCount) {
        val deltaTheta = math.atan(math.pow(2, -i))

        if (targetTheta > totalTheta) { // Positive angle added
          xPrime = xPrime - yPrime / math.pow(2, i)
          yPrime = yPrime + xPrime / math.pow(2, i)
          totalTheta += deltaTheta
        } else { // Negative angle added
          xPrime = xPrime + yPrime / math.pow(2, i)
          yPrime = yPrime - xPrime / math.pow(2, i)
          totalTheta -= deltaTheta
        }

        println(f"$i%9d | ${xPrime}%12.8f | ${yPrime}%12.8f | ${totalTheta}%12.8f")
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