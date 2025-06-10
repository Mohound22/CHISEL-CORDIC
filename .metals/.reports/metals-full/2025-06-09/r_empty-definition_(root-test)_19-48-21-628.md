error id: file://<WORKSPACE>/src/test/scala/CORDIC/scalaHyperTestIterable.scala:CORDIC/HyperCordicModelTest#runTest().
file://<WORKSPACE>/src/test/scala/CORDIC/scalaHyperTestIterable.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol CORDIC/HyperCordicModelTest#runTest().
empty definition using fallback
non-local guesses:

offset: 12617
uri: file://<WORKSPACE>/src/test/scala/CORDIC/scalaHyperTestIterable.scala
text:
```scala
package CORDIC

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import CordicModelConstants._
import CordicModelConstants.Mode._
import CordicModelConstants.ModeHyper._
import scala.math.{sinh, cosh, sqrt, abs, tanh, log}

class HyperCordicModelTest extends AnyFlatSpec with Matchers {
  // Test parameters
  val width = 16
  val fractionalBits = 12
  val integerBits = 3
  val testCycles = 16
  val precision = 0.01 // Adjusted precision for fixed-point arithmetic
  
  // Instantiate HyperCordicModel
  val model = new HyperCordicModel(
    width = width,
    cycleCount = testCycles,
    integerBits = integerBits,
    magnitudeCorrection = true // Default to true, test both cases
  )


  // Helper conversions
  def doubleToFixed(x: Double): BigInt = 
    CordicModelConstants.doubleToFixed(x, fractionalBits, width)
    
  def fixedToDouble(x: BigInt): Double = {
    // Handle the case where very small values get rounded to -1 instead of 0
    if (x == -1 && fractionalBits > 0) 0.0
    else x.toDouble / (1 << fractionalBits).toDouble
  }

  // Helper function to run test case for Hyperbolic CORDIC
  def runTest(
    theta: Double = 0.0, // For SinhCosh
    xIn: Double = 0.0,  // For AtanhMagnitudeHyper
    yIn: Double = 0.0,  // For AtanhMagnitudeHyper
    mode: ModeHyper = SinhCosh, // Use ModeHyper
    currentModel: HyperCordicModel = model // Allow passing different model instances
  ): Unit = {
    currentModel.reset()
    currentModel.setInputs(
      start = true,
      modeIn = mode,
      // Pass BigInt representations of inputs
      theta = if (mode == SinhCosh) doubleToFixed(theta) else BigInt(0),
      xIn = if (mode == AtanhMagnitudeHyper) doubleToFixed(xIn) else BigInt(0),
      yIn = if (mode == AtanhMagnitudeHyper) doubleToFixed(yIn) else BigInt(0)
    )
    while(!currentModel.done) currentModel.step()
  }

  // Basic functionality tests for AtanhMagnitudeHyper
  it should "calculate atanh for positive y/x ratio" in {
    // Test atanh(y/x). Ensure x > abs(y) for valid atanh.
    // Let y/x = 0.5. Choose x=1.0, y=0.5. atanh(0.5)
    val testX = 1.0
    val testY = 0.5

    runTest(xIn = testX, yIn = testY, mode = AtanhMagnitudeHyper, currentModel = model)
    
    val modelAtanhFixed = model.atanh
    val modelAtanhDouble = fixedToDouble(modelAtanhFixed)
    val expectedAtanhDouble = atanh(testY/testX) // Using the test's helper

    val modelMagFixed = model.magnitudeHyper
    val modelMagDouble = fixedToDouble(modelMagFixed)
    val expectedMagDouble = sqrt(testX*testX - testY*testY)

    fixedToDouble(model.atanh) should be (expectedAtanhDouble +- precision) // Keep adjusted precision for atanh
    fixedToDouble(model.magnitudeHyper) should be (expectedMagDouble +- precision) // Original assertion
  }

  it should "calculate atanh for negative y/x ratio" in {
    // Let y/x = -0.5. Choose x=1.0, y=-0.5. atanh(-0.5)
    val testX = 1.0
    val testY = -0.5
    runTest(xIn = testX, yIn = testY, mode = AtanhMagnitudeHyper)
    fixedToDouble(model.atanh) should be (atanh(testY/testX) +- precision)
    fixedToDouble(model.magnitudeHyper) should be (sqrt(testX*testX - testY*testY) +- precision)
  }
  
  // Basic functionality tests for SinhCosh
  it should "calculate sinh/cosh for positive theta" in {
    val testTheta = 0.5 // Example value for theta
    runTest(theta = testTheta, mode = SinhCosh)
    fixedToDouble(model.cosh) should be (cosh(testTheta) +- precision)
    fixedToDouble(model.sinh) should be (sinh(testTheta) +- precision)
  }

  it should "calculate sinh/cosh for negative theta" in {
    val testTheta = -0.5
    runTest(theta = testTheta, mode = SinhCosh)
    fixedToDouble(model.cosh) should be (cosh(testTheta) +- precision) // cosh(-x) = cosh(x)
    fixedToDouble(model.sinh) should be (sinh(testTheta) +- precision) // sinh(-x) = -sinh(x)
  }

  // Edge cases for AtanhMagnitudeHyper mode
  it should "handle zero Y input in AtanhMagnitudeHyper mode" in {
    // atanh(0/x) = atanh(0) = 0
    val testX = 1.0
    runTest(xIn = testX, yIn = 0.0, mode = AtanhMagnitudeHyper)
    fixedToDouble(model.atanh) should be (0.0 +- precision)
    fixedToDouble(model.magnitudeHyper) should be (testX +- precision) // sqrt(x^2 - 0) = x
  }


  // Edge cases for SinhCosh mode
  it should "calculate sinh/cosh for zero theta" in {
    runTest(theta = 0.0, mode = SinhCosh)
    fixedToDouble(model.cosh) should be (1.0 +- precision) // cosh(0) = 1
    fixedToDouble(model.sinh) should be (0.0 +- precision) // sinh(0) = 0
  }


  it should "handle inputs for SinhCosh near theta limits" in {

    val testTheta = 1.1181
    runTest(theta = testTheta, mode = SinhCosh)
    fixedToDouble(model.cosh) should be (cosh(testTheta) +- precision) 
    fixedToDouble(model.sinh) should be (sinh(testTheta) +- precision)
    // Verify cosh^2 - sinh^2 = 1
    val ch = fixedToDouble(model.cosh)
    val sh = fixedToDouble(model.sinh)
    (ch*ch - sh*sh) should be (1.0 +- precision)
  }

  it should "handle inputs for AtanhMagnitudeHyper near y/x = +/-1" in { //MAXIMUM VALUE OF Y/X = 0.8068
    // Test y/x close to 1, e.g., y=0.8, x=1.0. atanh(0.9)
    var testX = 1.0
    var testY = 0.8 // y/x = 0.8
    if (abs(testY/testX) >= 1.0) {
        // This case should ideally not be reached if inputs are chosen carefully
        //println(s"Skipping test for y/x = ${testY/testX} as it's outside atanh domain or x is not > |y|")
    } else {
        runTest(xIn = testX, yIn = testY, mode = AtanhMagnitudeHyper)
        fixedToDouble(model.atanh) should be (atanh(testY/testX) +- precision * 10) // atanh grows fast near 1
        fixedToDouble(model.magnitudeHyper) should be (sqrt(testX*testX - testY*testY) +- precision * 5)
    }

    // Test y/x close to -1, e.g., y=-0.8, x=1.0. atanh(-0.8)
    testY = -0.8 // y/x = -0.9
    if (abs(testY/testX) >= 1.0) {
        // This case should ideally not be reached
        //println(s"Skipping test for y/x = ${testY/testX} as it's outside atanh domain or x is not > |y|")
    } else {
        runTest(xIn = testX, yIn = testY, mode = AtanhMagnitudeHyper)
        fixedToDouble(model.atanh) should be (atanh(testY/testX) +- precision * 10)
        fixedToDouble(model.magnitudeHyper) should be (sqrt(testX*testX - testY*testY) +- precision * 5)
    }
  }
  
  // Multiple operations test
  it should "handle consecutive operations correctly" in {
    // First operation - SinhCosh
    val theta1 = 0.5
    runTest(theta = theta1, mode = SinhCosh)
    val cosh1_actual = fixedToDouble(model.cosh)
    val sinh1_actual = fixedToDouble(model.sinh)
    cosh1_actual should be (cosh(theta1) +- precision)
    sinh1_actual should be (sinh(theta1) +- precision)

    
    val x2_double = 1.5 
    val y2_double = 0.5 
    val expected_atanh2 = atanh(y2_double/x2_double)
    val expected_mag2 = sqrt(x2_double*x2_double - y2_double*y2_double)

    runTest(xIn = x2_double, yIn = y2_double, mode = AtanhMagnitudeHyper)
    fixedToDouble(model.atanh) should be (expected_atanh2 +- precision)
    fixedToDouble(model.magnitudeHyper) should be (expected_mag2 +- precision)
  }

  // Error condition tests (remain similar)
  it should "not complete when not started" in {
    model.reset() // Ensure model is in a known state
    // model.setInputs(false, SinhCosh, BigInt(0), BigInt(0), BigInt(0)) // Not strictly needed if start is false by default in reset
    model.done should be (false)
    model.step() // Step without start flag being true
    model.done should be (false)
  }

  // Magnitude correction tests for Hyperbolic CORDIC
  it should "produce correct magnitude in AtanhMagnitudeHyper with correction enabled" in {
    val modelWithCorrection = new HyperCordicModel(
      width = width, cycleCount = testCycles, integerBits = integerBits, magnitudeCorrection = true
    )
    // Ensure x_val > abs(y_val)
    val testInputs = Seq((1.2, 0.5), (2.0, 1.0), (1.5, -0.3), (2.5, 2.0)) 
    
    for ((x_val, y_val) <- testInputs) {
      // Ensure valid inputs for atanh: abs(y_val/x_val) < 1.0, and for sqrt: x_val*x_val - y_val*y_val >= 0 which means x_val >= abs(y_val)
      // The CORDIC implementation expects x_in > 0 for AtanhMagnitudeHyper if y_in is non-zero for proper direction d.
      if (x_val > 0 && x_val > abs(y_val)) { 
          runTest(xIn = x_val, yIn = y_val, mode = AtanhMagnitudeHyper, currentModel = modelWithCorrection)
          val expectedMagnitude = sqrt(x_val*x_val - y_val*y_val)
          fixedToDouble(modelWithCorrection.magnitudeHyper) should be (expectedMagnitude +- precision * 5) // Allow some tolerance
          fixedToDouble(modelWithCorrection.atanh) should be (atanh(y_val/x_val) +- precision * 5)
      } else {
          //println(s"Skipping AtanhMagnitudeHyper (correction enabled) test for invalid input: x=$x_val, y=$y_val")
      }
    }
  }

  it should "produce scaled magnitude in AtanhMagnitudeHyper with correction disabled" in {
    val modelNoCorrection = new HyperCordicModel(
      width = width, cycleCount = testCycles, integerBits = integerBits, magnitudeCorrection = false
    )
    val hyperShiftExponents = CordicModelConstants.getHyperbolicShiftExponents(testCycles)
    val K_h = CordicModelConstants.calculateHyperbolicGainFactor(hyperShiftExponents)

    val testInputs = Seq((1.2, 0.5), (2.0, 1.0), (1.5, -0.3), (2.5, 2.0))
    
    for ((x_val, y_val) <- testInputs) {
      if (x_val > 0 && x_val > abs(y_val)) {
          runTest(xIn = x_val, yIn = y_val, mode = AtanhMagnitudeHyper, currentModel = modelNoCorrection)
          // When magnitudeCorrection is false, magnitudeHyper is the raw x output from CORDIC iterations
          // This raw x output should be K_h * sqrt(inputX^2 - inputY^2)
          val expectedRawXOutput = K_h * sqrt(x_val*x_val - y_val*y_val)
          fixedToDouble(modelNoCorrection.magnitudeHyper) should be (expectedRawXOutput +- precision * K_h * 5) // K_h can be > 1, scale precision
          fixedToDouble(modelNoCorrection.atanh) should be (atanh(y_val/x_val) +- precision * 5)
      } else {
          //println(s"Skipping AtanhMagnitudeHyper (correction disabled) test for invalid input: x=$x_val, y=$y_val")
      }
    }
  }
  
  it should "satisfy cosh^2(theta) - sinh^2(theta) ~= 1 with correction enabled for SinhCosh" in {
    val modelWithCorrection = new HyperCordicModel(
      width = width, cycleCount = testCycles, integerBits = integerBits, magnitudeCorrection = true
    )
    val testThetas = Seq(0.0, 0.3, 0.8, -0.5, 1.0, 1.1) // Added more test thetas, including 0
    for (theta_val <- testThetas) {
      runTest(theta = theta_val, mode = SinhCosh, currentModel = modelWithCorrection)
      val ch_res = fixedToDouble(modelWithCorrection.cosh)
      val sh_res = fixedToDouble(modelWithCorrection.sinh)
      // Check identity: cosh^2(x) - sinh^2(x) = 1
      (ch_res * ch_res - sh_res * sh_res) should be (1.0 +- precision * 10) // Allow tolerance due to fixed point and squaring
    }
  }

  it should "satisfy (K_h*cosh(theta))^2 - (K_h*sinh(theta))^2 ~= K_h^2 with correction disabled for SinhCosh" in {
    val modelNoCorrection = new HyperCordicModel(
      width = width, cycleCount = testCycles, integerBits = integerBits, magnitudeCorrection = false
    )
    val hyperShiftExponents = CordicModelConstants.getHyperbolicShiftExponents(testCycles)
    val K_h = CordicModelConstants.calculateHyperbolicGainFactor(hyperShiftExponents)
    
    val testThetas = Seq(0.0, 0.3, 0.8, -0.5, 1.0, 1.1) 
    for (theta_val <- testThetas) {
      runTest(theta = theta_val, mode = SinhCosh, currentModel = modelNoCorrection)
      // When magnitudeCorrection = false, x_init = 1.0 (fixed point).
      // So, output coshResult is K_h * cosh(theta_val), sinhResult is K_h * sinh(theta_val)
      val ch_res_scaled = fixedToDouble(modelNoCorrection.cosh) 
      val sh_res_scaled = fixedToDouble(modelNoCorrection.sinh)

      // Check raw outputs against expected scaled values
      ch_res_scaled should be (K_h * cosh(theta_val) +- precision * K_h * 10) // Scaled precision, increased margin slightly
      sh_res_scaled should be (K_h * sinh(theta_val) +- precision * K_h * 10) // Scaled precision, increased margin slightly
      
      // Then (ch_res_scaled^2 - sh_res_scaled^2) should be (K_h * cosh)^2 - (K_h * sinh)^2 = K_h^2
      (ch_res_scaled * ch_res_scaled - sh_res_scaled * sh_res_scaled) should be (K_h * K_h +- precision * K_h * K_h * 20) // Wider margin for squared K_h 
    }
  }

  // Tests for Exponential mode
  it should "calculate e^x and e^-x correctly for positive x" in {
    val testTheta = 0.5
    @@runTest(theta_val = testTheta, mode = Exponential)
    
    // Get results
    val expResult = fixedToDouble(model.exp)
    val expNegResult = fixedToDouble(model.expNeg)
    
    // Expected values using math library
    val expectedExp = math.exp(testTheta)
    val expectedExpNeg = math.exp(-testTheta)
    
    // Verify results
    expResult should be (expectedExp +- precision)
    expNegResult should be (expectedExpNeg +- precision)
    
    // Verify that e^x * e^-x ≈ 1
    expResult * expNegResult should be (1.0 +- precision * 10)
  }

  it should "calculate e^x and e^-x correctly for negative x" in {
    val testTheta = -0.5
    runTest(theta = testTheta, mode = Exponential)
    
    // Get results
    val expResult = fixedToDouble(model.exp)
    val expNegResult = fixedToDouble(model.expNeg)
    
    // Expected values using math library
    val expectedExp = math.exp(testTheta)
    val expectedExpNeg = math.exp(-testTheta)
    
    // Verify results
    expResult should be (expectedExp +- precision)
    expNegResult should be (expectedExpNeg +- precision)
    
    // Verify that e^x * e^-x ≈ 1
    expResult * expNegResult should be (1.0 +- precision * 10)
  }

  it should "calculate e^x correctly for x = 0" in {
    val testTheta = 0.0
    runTest(theta = testTheta, mode = Exponential)
    
    // Get results
    val expResult = fixedToDouble(model.exp)
    val expNegResult = fixedToDouble(model.expNeg)
    
    // For x = 0, both e^x and e^-x should be 1
    expResult should be (1.0 +- precision)
    expNegResult should be (1.0 +- precision)
  }

  it should "verify e^x matches cosh(x) + sinh(x) identity" in {
    val testThetas = Seq(0.0, 0.5, -0.5, 1.0, -1.0)
    
    for (theta <- testThetas) {
      println(s"\n=== Testing theta = $theta ===")
      
      // First get exp results
      runTest(theta = theta, mode = Exponential)
      val expResult = fixedToDouble(model.exp)
      val expNegResult = fixedToDouble(model.expNeg)
      println(s"Direct exponential calculation:")
      println(s"e^$theta = $expResult")
      println(s"e^${-theta} = $expNegResult")
      
      // Then get sinh/cosh results
      runTest(theta = theta, mode = SinhCosh)
      val coshResult = fixedToDouble(model.cosh)
      val sinhResult = fixedToDouble(model.sinh)
      println(s"\nVerifying through sinh/cosh:")
      println(s"cosh($theta) = $coshResult")
      println(s"sinh($theta) = $sinhResult")
      println(s"cosh($theta) + sinh($theta) = ${coshResult + sinhResult}")
      println(s"cosh($theta) - sinh($theta) = ${coshResult - sinhResult}")
      
      // Verify e^x = cosh(x) + sinh(x)
      expResult should be ((coshResult + sinhResult) +- precision)
      
      // Verify e^-x = cosh(x) - sinh(x)
      expNegResult should be ((coshResult - sinhResult) +- precision)
      
      println(s"Expected e^$theta = ${math.exp(theta)}")
      println(s"Expected e^${-theta} = ${math.exp(-theta)}")
      println(s"Actual vs Expected difference for e^$theta: ${math.abs(expResult - math.exp(theta))}")
      println(s"Actual vs Expected difference for e^${-theta}: ${math.abs(expNegResult - math.exp(-theta))}")
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 