error id: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable.scala:
file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 211
uri: file://<WORKSPACE>/src/main/scala/CORDIC/scalaModelIterable.scala
text:
```scala
package CORDIC

abstract class baseScalaModel {
  val k: Double = 0.607253
}

class trigScalaModelMultiCycle extends baseScalaModel {
  var xPrime: Double = _
  var yPrime: Double = _
  var totalTheta: Double = @@_
  
  var currentIteration: Int = 0
  var trueArcTanfalseSinCos: Boolean = _
  var internalTargetTheta: Double = _ 

  var isSpecialCalculationDone: Boolean = false 
  var initialized: Boolean = false

  def setupCalculation(
      targetTheta: Double,
      inputX: Double,
      inputY: Double,
      trueArcTanfalseSinCos: Boolean
  ): Unit = {
    this.trueArcTanfalseSinCos = trueArcTanfalseSinCos
    this.currentIteration = 0
    this.isSpecialCalculationDone = false
    this.initialized = true

    if (this.trueArcTanfalseSinCos) { 
      assert(inputX >= 0)
      if (inputX == 0.0 && inputY == 0.0) {
        this.xPrime = 0.0
        this.yPrime = 0.0
        this.totalTheta = 0.0
        this.isSpecialCalculationDone = true
      } else {
        this.xPrime = inputX
        this.yPrime = inputY
        this.totalTheta = 0.0
      }
    } else { 
      assert((math.Pi / 2) >= targetTheta && targetTheta >= -(math.Pi / 2))
      this.internalTargetTheta = targetTheta
      if (targetTheta == 0.0) {
        this.xPrime = 1.0 / k 
        this.yPrime = 0.0     
        this.totalTheta = 0.0
        this.isSpecialCalculationDone = true
      } else {
        this.xPrime = 1.0 
        this.yPrime = 0.0
        this.totalTheta = 0.0 
      }
    }
  }

  def iterate(): (Double, Double, Double) = {
    if (!initialized) {
        throw new IllegalStateException("Calculation not set up. Call setupCalculation first.")
    }

    if (isSpecialCalculationDone) {
        println(
          f"${this.currentIteration}%9d | ${this.xPrime}%12.8f | ${this.yPrime}%12.8f | ${this.totalTheta}%12.8f"
        )
        currentIteration += 1
        return (this.xPrime, this.yPrime, this.totalTheta)
    }

    val i = currentIteration
    val deltaTheta = math.atan(math.pow(2, -i))
    var direction = 0
    val xOld = xPrime
    val yOld = yPrime

    if (this.trueArcTanfalseSinCos) { 
      if (yPrime < 0) {
        direction = 1
      } else {
        direction = -1
      }
      xPrime = xOld - direction * yOld * math.pow(2, -i)
      yPrime = yOld + direction * xOld * math.pow(2, -i)
      totalTheta -= direction * deltaTheta
    } else { 
      if (totalTheta < this.internalTargetTheta) {
        direction = -1
      } else {
        direction = 1
      }
      xPrime = xOld + direction * yOld * math.pow(2, -i)
      yPrime = yOld - direction * xOld * math.pow(2, -i)
      totalTheta -= direction * deltaTheta
    }

    println(
      f"${this.currentIteration}%9d | ${this.xPrime}%12.8f | ${this.yPrime}%12.8f | ${this.totalTheta}%12.8f"
    )
    
    currentIteration += 1
    (this.xPrime, this.yPrime, this.totalTheta)
  }

  def calcSinCos(gainCorrection: Boolean): (Double, Double) = {
    if (!initialized || this.trueArcTanfalseSinCos) {
        throw new IllegalStateException("Not set up for Sin/Cos or in wrong mode.")
    }
    val finalSin = if (gainCorrection) yPrime * k else yPrime
    val finalCos = if (gainCorrection) xPrime * k else xPrime
    (finalSin, finalCos)
  }

  def calcArcTan(): Double = {
    if (!initialized || !this.trueArcTanfalseSinCos) {
        throw new IllegalStateException("Not set up for ArcTan or in wrong mode.")
    }
    totalTheta
  }
  
  def getCurrentIteration(): Int = this.currentIteration
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 