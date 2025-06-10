package CORDIC

import chisel3._
import chisel3.util._
import scala.math.{pow, sqrt, log, atan}

object CORDICCore {
  object CORDICModeAll extends ChiselEnum {
    val TrigSinCos, TrigArctanMagnitude,
        LinearMultiply, LinearDivide,
        HyperSinhCosh, HyperAtanhMagnitude,
        Exponential, NaturalLog = Value
  }

  def doubleToFixed(x: Double, fractionalBits: Int, width: Int): BigInt = {
    val scaled = BigDecimal(x) * BigDecimal(BigInt(1) << fractionalBits)
    val rounded = scaled.setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt
    val maxVal = (BigInt(1) << (width - 1)) - 1
    val minVal = -(BigInt(1) << (width - 1))
    rounded.max(minVal).min(maxVal)
  }

  def clamp(value: SInt, targetWidth: Int): SInt = {
    val maxValTarget = ((BigInt(1) << (targetWidth - 1)) - 1).S(targetWidth.W)
    val minValTarget = (-(BigInt(1) << (targetWidth - 1))).S(targetWidth.W)
    val valueW = value.getWidth
    val maxValExtended = maxValTarget.pad(valueW)
    val minValExtended = minValTarget.pad(valueW)
    Mux(value > maxValExtended, maxValTarget,
      Mux(value < minValExtended, minValTarget,
        value(targetWidth - 1, 0).asSInt))
  }

  val TRIGONOMETRIC_GAIN: Double = 0.6072529350088813
  def getAtanLUT(fractionalBits: Int, width: Int, numEntries: Int): Seq[SInt] = {
    (0 until numEntries).map { i =>
      val angleRad = scala.math.atan(scala.math.pow(2.0, -i))
      doubleToFixed(angleRad, fractionalBits, width).S(width.W)
    }
  }

  val LINEAR_RANGE_LIMIT: Double = 1.99

  def getHyperbolicShiftExponents(cycleCount: Int): Seq[Int] = {
    var exponents = scala.collection.mutable.ArrayBuffer[Int]()
    var i = 0
    var k = 1
    var nextRepeat = 4
    
    // For hyperbolic CORDIC, some iterations must be repeated to ensure convergence.
    // The sequence of repeated iteration indices 'k' follows the rule k_new = 3*k_old + 1,
    // starting with k = 4, 13, 40, ...
    while (i < cycleCount) {
      exponents += k
      i += 1
      if (k == nextRepeat && i < cycleCount) {
        exponents += k
        i += 1
        // As k is repeated, calculate the next iteration index to be repeated
        nextRepeat = nextRepeat * 3 + 1
      }
      k += 1
    }
    exponents.toSeq
  }

  def calculateHyperbolicGainFactor(shiftValues: Seq[Int]): Double = {
    shiftValues.map { shiftValue =>
      sqrt(1.0 - pow(2.0, -2.0 * shiftValue))
    }.product
  }

  def getAtanHyperLUT(fractionalBits: Int, width: Int, hyperShiftExponents: Seq[Int]): Seq[SInt] = {
    hyperShiftExponents.map { exponent =>
      val xAtanh = pow(2.0, -exponent)
      val angleRad = 0.5 * log((1.0 + xAtanh) / (1.0 - xAtanh))
      doubleToFixed(angleRad, fractionalBits, width).S(width.W)
    }
  }
}

class CORDICCore(
    val width: Int,
    val cycleCount: Int,
    val integerBits: Int = 3,
    val gainCorrection: Boolean = true,
    val includeLinear: Boolean,
    val includeHyperbolic: Boolean
) extends Module {
  import CORDICCore._

  require(width > 0, "Width must be positive")
  require(cycleCount > 0, "Cycle count must be positive")
  require(integerBits >= 1, "Integer bits must be at least 1 for sign bit and potential integer part")
  val fractionalBits: Int = width - 1 - integerBits
  require(fractionalBits > 0, s"Fractional bits must be positive. Check width ($width) vs integerBits ($integerBits).")

  val io = IO(new Bundle {
    val mode = Flipped(Decoupled(CORDICModeAll()))
    val inputX = Input(SInt(width.W))
    val inputY = Input(SInt(width.W))
    val inputTheta = Input(SInt(width.W))
    val output1 = Decoupled(SInt(width.W))
    val output2 = Decoupled(SInt(width.W))
  })

  object State extends ChiselEnum {
    val sIdle, sBusy, sDone = Value
  }
  val state = RegInit(State.sIdle)

  val xReg = Reg(SInt(width.W))
  val yReg = Reg(SInt(width.W))
  val zReg = Reg(SInt(width.W))
  val iterCount = Reg(UInt(log2Ceil(cycleCount + 1).W))

  val currentOpModeReg = Reg(CORDICModeAll())
  val inputXBuffer = Reg(SInt(width.W))
  val inputYBuffer = Reg(SInt(width.W))
  val inputThetaBuffer = Reg(SInt(width.W))

  val oneFixed = doubleToFixed(1.0, fractionalBits, width).S(width.W)
  val trigGainFixed = doubleToFixed(TRIGONOMETRIC_GAIN, fractionalBits, width).S(width.W)
  val invTrigGainFixed = doubleToFixed(1.0 / TRIGONOMETRIC_GAIN, fractionalBits, width).S(width.W)
  val xInitSinCosFixed = if (gainCorrection) trigGainFixed else oneFixed
  val yInitSinCosFixed = 0.S(width.W)
  val atanTrigLut = VecInit(getAtanLUT(fractionalBits, width, cycleCount))

  val linearRangeLimitFixed = if (includeLinear) Some(doubleToFixed(LINEAR_RANGE_LIMIT, fractionalBits, width).S(width.W)) else None
  val linearScalingFactorKReg = if (includeLinear) Some(Reg(UInt(log2Ceil(width + 1).W))) else None
  val isDivisorNegativeReg = if (includeLinear) Some(Reg(Bool())) else None
  val linearTermXReg = if (includeLinear) Some(Reg(SInt(width.W))) else None
  val linearTermOneReg = if (includeLinear) Some(Reg(SInt(width.W))) else None

  val hyperbolicShiftExponents = if (includeHyperbolic) Some(getHyperbolicShiftExponents(cycleCount)) else None
  val hyperbolicGainDbl = if (includeHyperbolic && hyperbolicShiftExponents.get.nonEmpty) Some(calculateHyperbolicGainFactor(hyperbolicShiftExponents.get)) else None
  val invHyperbolicGainFixed = if (includeHyperbolic && hyperbolicGainDbl.isDefined && hyperbolicGainDbl.get != 0) Some(doubleToFixed(1.0 / hyperbolicGainDbl.get, fractionalBits, width).S(width.W)) else if (includeHyperbolic) Some(oneFixed) else None
  val xInitHyperbolicFixed = if (includeHyperbolic) Some(if (gainCorrection) invHyperbolicGainFixed.get else oneFixed) else None
  val yInitHyperbolicFixed = 0.S(width.W)
  val atanHyperbolicLut = if (includeHyperbolic) Some(VecInit(getAtanHyperLUT(fractionalBits, width, hyperbolicShiftExponents.get))) else None
  val hyperbolicShiftExponentsVec = if (includeHyperbolic) Some(VecInit(hyperbolicShiftExponents.get.map(_.U(log2Ceil(math.max(1,width)).W)))) else None

  val output1Reg = Reg(SInt(width.W))
  val output2Reg = Reg(SInt(width.W))
  io.output1.bits := output1Reg
  io.output2.bits := output2Reg

  io.mode.ready := false.B
  io.output1.valid := false.B
  io.output2.valid := false.B

  switch(state) {
    is(State.sIdle) {
      io.mode.ready := true.B 

      when(io.mode.fire) {
        currentOpModeReg := io.mode.bits
        inputXBuffer := io.inputX
        inputYBuffer := io.inputY
        inputThetaBuffer := io.inputTheta
        
        iterCount := 0.U
        switch(io.mode.bits) {
          is(CORDICModeAll.TrigSinCos) {
            xReg := xInitSinCosFixed
            yReg := yInitSinCosFixed
            zReg := io.inputTheta
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            xReg := io.inputX
            yReg := io.inputY
            zReg := 0.S(width.W)
          }
          is(CORDICModeAll.LinearMultiply) {
            if (includeLinear) {
              val multiplicand = io.inputX
              val multiplier = io.inputY
              val multiplierAbsUint = multiplier.abs.asUInt
              
              val conditionsMult = VecInit(Seq.tabulate(width + 1) { kIdx =>
                (multiplierAbsUint >> kIdx) <= linearRangeLimitFixed.get.asUInt
              })
              val scalingFactorK = PriorityEncoder(conditionsMult)
              linearScalingFactorKReg.get := scalingFactorK

              xReg := multiplicand
              yReg := 0.S 
              zReg := multiplier >> scalingFactorK
              isDivisorNegativeReg.get := false.B
              linearTermXReg.get := multiplicand
              linearTermOneReg.get := oneFixed
            }
          }
          is(CORDICModeAll.LinearDivide) {
            if (includeLinear) {
              val dividend = io.inputX
              val divisor = io.inputY
              isDivisorNegativeReg.get := divisor < 0.S
              val absDivisorUint = divisor.abs.asUInt
              val dividendAbsUint = dividend.abs.asUInt

              val limitProdIntermediate = absDivisorUint.zext * linearRangeLimitFixed.get.asUInt.zext
              val limitForDividendScaledAbsUint = (limitProdIntermediate >> fractionalBits).asUInt

              val conditionsDiv = VecInit(Seq.tabulate(width + 1) { kIdx =>
                Mux(limitForDividendScaledAbsUint === 0.U && dividendAbsUint =/= 0.U,
                  (dividendAbsUint >> kIdx) === 0.U,
                  (dividendAbsUint >> kIdx) <= limitForDividendScaledAbsUint)
              })
              val scalingFactorK = PriorityEncoder(conditionsDiv)
              linearScalingFactorKReg.get := scalingFactorK

              xReg := absDivisorUint.asSInt 
              yReg := dividend >> scalingFactorK
              zReg := 0.S
              linearTermXReg.get := absDivisorUint.asSInt
              linearTermOneReg.get := oneFixed
            }
          }
          is(CORDICModeAll.HyperSinhCosh, CORDICModeAll.Exponential) {    
            if (includeHyperbolic) {
              xReg := xInitHyperbolicFixed.get
              yReg := yInitHyperbolicFixed
              zReg := io.inputTheta
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude) {
            if (includeHyperbolic) {
              xReg := io.inputX
              yReg := io.inputY
              zReg := 0.S(width.W)
            }
          }
          is(CORDICModeAll.NaturalLog) {
            if (includeHyperbolic) {
              val one = oneFixed
              val xPlusOne = io.inputX + one
              val xMinusOne = io.inputX - one
              xReg := xPlusOne
              yReg := xMinusOne
              zReg := 0.S(width.W)
            }
          }
        }
        state := State.sBusy
      }
    } 

    is(State.sBusy) {
      when(iterCount < cycleCount.U) {
        val currentIterIdx = iterCount
        val yShifted = WireDefault(0.S(width.W))
        val xShifted = WireDefault(0.S(width.W))
        val deltaTheta = WireDefault(0.S(width.W))
        val direction = WireDefault(0.S(2.W))

        switch(currentOpModeReg) {
          is(CORDICModeAll.TrigSinCos) {
            yShifted := (yReg >> currentIterIdx).asSInt
            xShifted := (xReg >> currentIterIdx).asSInt
            deltaTheta := atanTrigLut(currentIterIdx)
            direction := Mux(zReg >= 0.S, 1.S(2.W), -1.S(2.W))
            xReg := xReg - (direction * yShifted)
            yReg := yReg + (direction * xShifted)
            zReg := zReg - (direction * deltaTheta)
          }
          is(CORDICModeAll.TrigArctanMagnitude) {
            yShifted := (yReg >> currentIterIdx).asSInt
            xShifted := (xReg >> currentIterIdx).asSInt
            deltaTheta := atanTrigLut(currentIterIdx)
            direction := Mux(yReg >= 0.S, 1.S(2.W), -1.S(2.W))
            xReg := xReg + (direction * yShifted)
            yReg := yReg - (direction * xShifted)
            zReg := zReg + (direction * deltaTheta)
          }
          is(CORDICModeAll.LinearMultiply) {
            if (includeLinear) {
              val currentTermX = linearTermXReg.get
              val currentTermOne = linearTermOneReg.get
              val zSign = Mux(zReg > 0.S, 1.S(2.W), Mux(zReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(zSign === 0.S, 1.S(2.W), zSign)
              yReg := yReg + (direction * currentTermX)
              zReg := zReg - (direction * currentTermOne)
              linearTermXReg.get := (currentTermX >> 1).asSInt
              linearTermOneReg.get := (currentTermOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.LinearDivide) {
            if (includeLinear) {
              val currentTermX = linearTermXReg.get
              val currentTermOne = linearTermOneReg.get
              val ySign = Mux(yReg > 0.S, 1.S(2.W), Mux(yReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(ySign === 0.S, -1.S(2.W), -ySign)
              yReg := yReg + (direction * currentTermX)
              zReg := zReg - (direction * currentTermOne)
              linearTermXReg.get := (currentTermX >> 1).asSInt
              linearTermOneReg.get := (currentTermOne >> 1).asSInt
            }
          }
          is(CORDICModeAll.HyperSinhCosh, CORDICModeAll.Exponential) { 
            if (includeHyperbolic) {
              val currentShiftExponent = hyperbolicShiftExponentsVec.get(currentIterIdx)
              yShifted := (yReg >> currentShiftExponent).asSInt
              xShifted := (xReg >> currentShiftExponent).asSInt
              deltaTheta := atanHyperbolicLut.get(currentIterIdx)
              val zSign = Mux(zReg > 0.S, 1.S(2.W), Mux(zReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(zSign === 0.S, -1.S(2.W), zSign)
              xReg := xReg + (direction * yShifted)
              yReg := yReg + (direction * xShifted)
              zReg := zReg - (direction * deltaTheta)
            }
          }
          is(CORDICModeAll.HyperAtanhMagnitude, CORDICModeAll.NaturalLog) { 
            if (includeHyperbolic) {
              val currentShiftExponent = hyperbolicShiftExponentsVec.get(currentIterIdx)
              yShifted := (yReg >> currentShiftExponent).asSInt
              xShifted := (xReg >> currentShiftExponent).asSInt
              deltaTheta := atanHyperbolicLut.get(currentIterIdx)
              val ySign = Mux(yReg > 0.S, 1.S(2.W), Mux(yReg < 0.S, -1.S(2.W), 0.S(2.W)))
              direction := Mux(ySign === 0.S, -1.S(2.W), -ySign)
              xReg := xReg + (direction * yShifted)
              yReg := yReg + (direction * xShifted)
              zReg := zReg - (direction * deltaTheta)
            }
          }
        }
        iterCount := iterCount + 1.U
      }.otherwise {
        state := State.sDone
      }
    } 

    is(State.sDone) {
      output1Reg := 0.S(width.W) 
      output2Reg := 0.S(width.W)

      switch(currentOpModeReg) {
        is(CORDICModeAll.TrigSinCos) {
          output1Reg := xReg
          output2Reg := yReg
        }
        is(CORDICModeAll.TrigArctanMagnitude) {
          output1Reg := zReg
          if (gainCorrection) {
            val magnitudeFullProd = xReg * trigGainFixed
            output2Reg := (magnitudeFullProd >> fractionalBits.U).asSInt
          } else {
            output2Reg := xReg
          }
        }
        is(CORDICModeAll.LinearMultiply) {
          if (includeLinear) {
            val scalingFactorK = linearScalingFactorKReg.get
            val shiftedProd = yReg << scalingFactorK 
            output1Reg := clamp(shiftedProd, width) 
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.LinearDivide) {
          if (includeLinear) {
            val scalingFactorK = linearScalingFactorKReg.get
            val shiftedQuotBase = zReg << scalingFactorK 
            val finalQuotientValue = Mux(isDivisorNegativeReg.get, -shiftedQuotBase, shiftedQuotBase)
            output1Reg := clamp(finalQuotientValue, width) 
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.HyperSinhCosh) {
          if (includeHyperbolic) {
            output1Reg := xReg
            output2Reg := yReg
          }
        }
        is(CORDICModeAll.HyperAtanhMagnitude) {
          if (includeHyperbolic) {
            output1Reg := zReg
            if (gainCorrection && invHyperbolicGainFixed.isDefined) { 
              val magnitudeFullProd = xReg * invHyperbolicGainFixed.get
              output2Reg := (magnitudeFullProd >> fractionalBits.U).asSInt
            } else {
              output2Reg := xReg 
            }
          }
        }
        is(CORDICModeAll.NaturalLog) {
          if (includeHyperbolic) {
            output1Reg := (zReg << 1).asSInt
            output2Reg := 0.S
          }
        }
        is(CORDICModeAll.Exponential) {
          if (includeHyperbolic) {
            output1Reg := xReg + yReg
            output2Reg := xReg - yReg
          }
        }
      }
      
      io.output1.valid := true.B
      io.output2.valid := true.B

      when(io.output1.ready && io.output2.ready) {
        state := State.sIdle
      }
    } 
  } 
}
