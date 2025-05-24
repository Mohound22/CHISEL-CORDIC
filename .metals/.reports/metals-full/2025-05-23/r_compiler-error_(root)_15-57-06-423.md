error id: 640DDDFA489919D703437E5BD8874A4D
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
### scala.reflect.internal.FatalError: no context found for source-file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala,line-2,offset=42

occurred in the presentation compiler.



action parameters:
offset: 42
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.D@@ecoupled

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


presentation compiler configuration:
Scala version: 2.13.14
Classpath:
<WORKSPACE>/.bloop/root/bloop-bsp-clients-classes/classes-Metals-ibh9fzEySUyqPNQLUJdd3A== [exists ], <HOME>/.cache/bloop/semanticdb/com.sourcegraph.semanticdb-javac.0.10.4/semanticdb-javac-0.10.4.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/edu/berkeley/cs/chisel3_2.13/3.6.1/chisel3_2.13-3.6.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.14/scala-reflect-2.13.14.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/edu/berkeley/cs/firrtl_2.13/1.6.0/firrtl_2.13-1.6.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/2.0.0/upickle_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.8.1/os-lib_2.13-0.8.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.9.3/antlr4-runtime-4.9.3.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/google/protobuf/protobuf-java/3.18.3/protobuf-java-3.18.3.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/3.7.1/scopt_2.13-3.7.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/net/jcazevedo/moultingyaml_2.13/0.4.2/moultingyaml_2.13-0.4.2.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.6/json4s-native_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.5/data-class_2.13-0.2.5.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/1.0.4/scala-parallel-collections_2.13-1.0.4.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/2.0.0/ujson_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/2.0.0/upack_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/2.0.0/upickle-implicits_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/0.7.1/geny_2.13-0.7.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/nscala-time/nscala-time_2.13/2.22.0/nscala-time_2.13-2.22.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/yaml/snakeyaml/1.26/snakeyaml-1.26.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.6/json4s-core_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.6/json4s-native-core_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/2.0.0/upickle-core_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/joda-time/joda-time/2.10.1/joda-time-2.10.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/joda/joda-convert/2.2.0/joda-convert-2.2.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.6/json4s-ast_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.6/json4s-scalap_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar [exists ]
Options:
-language:reflectiveCalls -deprecation -feature -Xcheckinit -Yrangepos -Xplugin-require:semanticdb




#### Error stacktrace:

```
scala.tools.nsc.interactive.CompilerControl.$anonfun$doLocateContext$1(CompilerControl.scala:100)
	scala.tools.nsc.interactive.CompilerControl.doLocateContext(CompilerControl.scala:100)
	scala.tools.nsc.interactive.CompilerControl.doLocateContext$(CompilerControl.scala:99)
	scala.tools.nsc.interactive.Global.doLocateContext(Global.scala:114)
	scala.meta.internal.pc.PcDefinitionProvider.definitionTypedTreeAt(PcDefinitionProvider.scala:181)
	scala.meta.internal.pc.PcDefinitionProvider.definition(PcDefinitionProvider.scala:69)
	scala.meta.internal.pc.PcDefinitionProvider.definition(PcDefinitionProvider.scala:17)
	scala.meta.internal.pc.ScalaPresentationCompiler.$anonfun$definition$1(ScalaPresentationCompiler.scala:479)
```
#### Short summary: 

scala.reflect.internal.FatalError: no context found for source-file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala,line-2,offset=42