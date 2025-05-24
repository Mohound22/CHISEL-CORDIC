error id: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala:_empty_/trigScalaModel#setY().(newY)
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -newY.
	 -newY#
	 -newY().
	 -scala/Predef.newY.
	 -scala/Predef.newY#
	 -scala/Predef.newY().
offset: 682
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.Decoupled

abstract class baseScalaModel {
  var x: Double = 0.0
  var y: Double = 0.0
  var theta: Double = 0.0

  val k: Double = 1.0
}

/*  This class takes in an angle in radians and outputs and the sin and cosine of the angle*/
class trigScalaModel(val cycleCount: Int) extends baseScalaModel {

  // Calculate sine of the current angle (theta)
  def getSin: Double = math.sin(theta)

  // Calculate cosine of the current angle (theta)
  def getCos: Double = math.cos(theta)

  def setX(newX: Double): Unit = {
    x = newX
  }

  def setY(newY: Double): Unit = {
    y = newY
  }

  def setTheta(newTheta: Double): Unit = {
    y = new@@Theta
  }

  // Update theta based on current x and y coordinates
  private def updateTheta(): Unit = {
    theta = math.atan2(y, x) // calculates angle from x-axis
  }

  // Additional method to get (sin, cos) as a tuple
  def getSinCos: (Double, Double) = (getSin, getCos)

  // Method to update both x and y at once
  def setPosition(newX: Double, newY: Double): Unit = {
    x = newX
    y = newY
    // theta will be updated automatically through the setters
  }

  override def toString: String =
    f"trigScalaModel(x=$x%.2f, y=$y%.2f, θ=$theta%.2f, sinθ=${getSin}%.2f, cosθ=${getCos}%.2f, cycles=$cycleCount)"
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 