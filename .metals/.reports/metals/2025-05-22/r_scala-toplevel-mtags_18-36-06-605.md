error id: file://<WORKSPACE>/src/main/scala/gcd/CORDICscalaModel.scala:[348..349) in Input.VirtualFile("file://<WORKSPACE>/src/main/scala/gcd/CORDICscalaModel.scala", "//import chisel3._
//import chisel3.util.Decoupled

abstract class baseScalaModel {
  var x: Double = 0.0
  var y: Double = 0.0
  var theta: Double = 0.0

  val k: Double = 1.0
}

/*  This class takes in an angle in radians and outputs and the sin and cosine of the angle*/
class trigScalaModel(val cycleCount: Int) extends baseScalaModel {

  def () {

  }
}
")
file://<WORKSPACE>/file:<WORKSPACE>/src/main/scala/gcd/CORDICscalaModel.scala
file://<WORKSPACE>/src/main/scala/gcd/CORDICscalaModel.scala:15: error: expected identifier; obtained lparen
  def () {
      ^
#### Short summary: 

expected identifier; obtained lparen