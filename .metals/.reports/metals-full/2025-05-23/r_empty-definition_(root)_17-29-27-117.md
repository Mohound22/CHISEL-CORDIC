error id: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelOneCycle.scala:
file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelOneCycle.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 32
uri: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelOneCycle.scala
text:
```scala
//import chisel3._
//import chis@@el3.util.Decoupled

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

      if (inputX == 0.0 && inputY == 0.0) {
        return (0.0, 0.0, 0.0)
      }

      var xPrime = inputX
      var yPrime = inputY
      var totalTheta: Double = 0
      for (i <- 0 until cycleCount) {
        val deltaTheta = math.atan(math.pow(2, -i))

        var direction = 0
        if (yPrime < 0) {
          direction = 1
        } else {
          direction = -1
        }

        val xOld = xPrime
        val yOld = yPrime

        // SWAP TO >> WHEN FIXED DECIMAL
        xPrime = xOld - direction * yOld * math.pow(2, -i)
        yPrime = yOld + direction * xOld * math.pow(2, -i)
        totalTheta -= direction * deltaTheta

        println(
          f"$i%9d | ${xPrime}%12.8f | ${yPrime}%12.8f | ${totalTheta}%12.8f"
        )

      }

      (0.0, 0.0, totalTheta) // Return total angle / Arctan

    } else { // Sin Cos, take in targetTheta and spit out sin cos
      assert((math.Pi / 2) >= targetTheta)
      assert(targetTheta >= -(math.Pi / 2))

      if (targetTheta == 0.0) {
        return (1.0/k, 0.0, 0.0) // (cos, sin, angle)
      }

      var xPrime: Double = 1.0 // Start with (1,0) vector
      var yPrime: Double = 0.0
      var totalTheta: Double = 0.0 // Accumulated rotation

      for (i <- 0 until cycleCount) {
        val deltaTheta = math.atan(math.pow(2, -i))

        var direction = 0
        if (totalTheta < targetTheta) { 
          direction = -1
        } else { 
          direction = 1
        }


        val xOld = xPrime
        val yOld = yPrime


        xPrime = xOld + direction * yOld * math.pow(2, -i)
        yPrime = yOld - direction * xOld * math.pow(2, -i) 
        
        totalTheta -= direction * deltaTheta 

        println(
          f"$i%9d | ${xPrime}%12.8f | ${yPrime}%12.8f | ${totalTheta}%12.8f"
        )
      }

      // Return (cos_component, sin_component, final_accumulated_angle)
      (xPrime, yPrime, totalTheta) 
    }
  }

  def calcSinCos(
      targetTheta: Double,
      gainCorrection: Boolean
  ): (Double, Double) = { // return (sin, cos)
  
    val (cos, sin, _) = iterateTowards(targetTheta, 0.0, 0.0, false)

    if (gainCorrection)
      (sin * k, cos * k)
    else
      (sin, cos)
  }

  def calcArcTan(
      inputX: Double,
      inputY: Double,
  ): Double = { // return arcTan
    val (_, _, arcTan) = iterateTowards(0.0, inputX, inputY, true)

    arcTan

  }
}

// override def toString: String =
// f"trigScalaModel(x=$x%.2f, y=$y%.2f, θ=$theta%.2f, sinθ=${getSin}%.2f, cosθ=${getCos}%.2f, cycles=$cycleCount)"

```


#### Short summary: 

empty definition using pc, found symbol in pc: 