error id: file://<WORKSPACE>/src/main/scala/CORDIC/binaryfixedpoint.scala:
file://<WORKSPACE>/src/main/scala/CORDIC/binaryfixedpoint.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 3903
uri: file://<WORKSPACE>/src/main/scala/CORDIC/binaryfixedpoint.scala
text:
```scala
package CORDIC

import java.math.BigInteger
import scala.annotation.tailrec

class BinaryFixedPoint(
  private val unscaledValue: BigInt,
  val integerBits: Int,
  val fractionalBits: Int
) {
  require(integerBits > 0, "Must have at least 1 integer bit")
  require(fractionalBits >= 0, "Fractional bits can't be negative")
  
  // Total width in bits (for overflow checking)
  private val totalBits = integerBits + fractionalBits
  
  // Range checking values
  private val maxValue = (BigInt(1) << (totalBits - 1)) - 1
  private val minValue = -(BigInt(1) << (totalBits - 1))
  
  // Normalize the value on creation
  private val normalizedValue = {
    val masked = if (totalBits < 64) {
      unscaledValue & ((BigInt(1) << totalBits) - 1)
    } else {
      unscaledValue
    }
    
    // Sign-extend if necessary
    if (masked.testBit(totalBits - 1)) {
      masked | ((BigInt(-1) << totalBits))
    } else {
      masked
    }
  }
  
  // Conversion to double for inspection
  def toDouble: Double = {
    normalizedValue.doubleValue / (1L << fractionalBits).doubleValue
  }
  
  // Arithmetic operations
  def +(that: BinaryFixedPoint): BinaryFixedPoint = {
    requireCompatible(that)
    new BinaryFixedPoint(
      this.normalizedValue + that.normalizedValue,
      this.integerBits,
      this.fractionalBits
    )
  }
  
  def -(that: BinaryFixedPoint): BinaryFixedPoint = {
    requireCompatible(that)
    new BinaryFixedPoint(
      this.normalizedValue - that.normalizedValue,
      this.integerBits,
      this.fractionalBits
    )
  }
  
  def *(that: BinaryFixedPoint): BinaryFixedPoint = {
    requireCompatible(that)
    val product = this.normalizedValue * that.normalizedValue
    new BinaryFixedPoint(
      product >> this.fractionalBits,
      this.integerBits,
      this.fractionalBits
    )
  }
  
  // Bit manipulation operations
  def <<(shift: Int): BinaryFixedPoint = {
    new BinaryFixedPoint(
      normalizedValue << shift,
      integerBits,
      fractionalBits
    )
  }
  
  def >>(shift: Int): BinaryFixedPoint = {
    new BinaryFixedPoint(
      normalizedValue >> shift,
      integerBits,
      fractionalBits
    )
  }
  
  def &(that: BinaryFixedPoint): BinaryFixedPoint = {
    requireCompatible(that)
    new BinaryFixedPoint(
      this.normalizedValue & that.normalizedValue,
      this.integerBits,
      this.fractionalBits
    )
  }
  
  // Formatting
  override def toString: String = {
    val binaryStr = toBinaryString
    s"BinaryFixedPoint(${toDouble}, ${binaryStr.take(integerBits)}.${binaryStr.drop(integerBits)})"
  }
  
  def toBinaryString: String = {
    val bits = new StringBuilder
    @tailrec
    def buildString(value: BigInt, remaining: Int): Unit = {
      if (remaining > 0) {
        bits.append(if (value.testBit(remaining - 1)) "1" else "0")
        buildString(value, remaining - 1)
      }
    }
    
    buildString(normalizedValue, totalBits)
    bits.toString
  }
  
  private def requireCompatible(that: BinaryFixedPoint): Unit = {
    require(this.integerBits == that.integerBits, "Integer bits must match")
    require(this.fractionalBits == that.fractionalBits, "Fractional bits must match")
  }
}

object BinaryFixedPoint {
  // Factory methods
  def fromDouble(value: Double, integerBits: Int, fractionalBits: Int): BinaryFixedPoint = {
    val scaled = (value * (1L << fractionalBits)).round
    new BinaryFixedPoint(BigInt(scaled), integerBits, fractionalBits)
  }
  
  def fromBigInt(value: BigInt, integerBits: Int, fractionalBits: Int): BinaryFixedPoint = {
    new BinaryFixedPoint(value << fractionalBits, integerBits, fractionalBits)
  }
}


/* 

// Create 5.7 fixed-point numbers (5 integer bits, 7 fractional bits)
val num1 = BinaryFixedPoint.fromDouble(3.5, 5, 7)
val num2 = BinaryFixedPoint.fromDouble(1.25, 5, 7)

println(num1)       // BinaryFixedPoint(3.5, 00011.1000000)
println(num2)       // Bina@@ryFixedPoint(1.25, 00001.0100000)

val sum = num1 + num2
println(sum)        // BinaryFixedPoint(4.75, 00100.1100000)

val product = num1 * num2
println(product)    // BinaryFixedPoint(4.375, 00100.0110000)

// Bit manipulation
val shifted = num1 << 2
println(shifted)    // BinaryFixedPoint(14.0, 01110.0000000)

 */
```


#### Short summary: 

empty definition using pc, found symbol in pc: 