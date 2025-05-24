error id: B2CBBE602C8D9A11CB935745D1CD3DA0
file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
### java.lang.NullPointerException: Cannot invoke "scala.reflect.internal.Symbols$Symbol.isModule()" because the return value of "scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.methodSym()" is null

occurred in the presentation compiler.



action parameters:
offset: 419
uri: file://<WORKSPACE>/src/main/scala/CORDIC/CORDICscalaModel.scala
text:
```scala
//import chisel3._
//import chisel3.util.Decoupled

abstract class baseScalaModel {
  val k: Double = 1.0
}

/*  This class takes in an angle in radians and outputs and the sin and cosine of the angle*/
class trigScalaModel(val cycleCount: Int) extends baseScalaModel {

  def iterateTowards(targetTheta: Double): (Double, Double, Double) = {
    //math.atan()
    xPrime = 1 * x - math.tan(theta) *y
    yPrime = tan(t@@)
  }

  // Calculate sine of the current angle (theta)
  def getMathSin(targetTheta: Double): Double = math.sin(targetTheta)

  // Calculate cosine of the current angle (theta)
  def getMathCos(targetTheta: Double): Double = math.cos(targetTheta)


  // Additional method to get (sin, cos) as a tuple
  //def getSinCos: (Double, Double) = (getSin, getCos)


  //override def toString: String =
    //f"trigScalaModel(x=$x%.2f, y=$y%.2f, θ=$theta%.2f, sinθ=${getSin}%.2f, cosθ=${getCos}%.2f, cycles=$cycleCount)"
}

```


presentation compiler configuration:
Scala version: 2.13.14
Classpath:
<WORKSPACE>/.bloop/root/bloop-bsp-clients-classes/classes-Metals-7lRBFyUvSD6jxHMIc8Sljw== [exists ], <HOME>/.cache/bloop/semanticdb/com.sourcegraph.semanticdb-javac.0.10.4/semanticdb-javac-0.10.4.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/edu/berkeley/cs/chisel3_2.13/3.6.1/chisel3_2.13-3.6.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.14/scala-reflect-2.13.14.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/edu/berkeley/cs/firrtl_2.13/1.6.0/firrtl_2.13-1.6.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/2.0.0/upickle_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.8.1/os-lib_2.13-0.8.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.9.3/antlr4-runtime-4.9.3.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/google/protobuf/protobuf-java/3.18.3/protobuf-java-3.18.3.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/3.7.1/scopt_2.13-3.7.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/net/jcazevedo/moultingyaml_2.13/0.4.2/moultingyaml_2.13-0.4.2.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.6/json4s-native_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.5/data-class_2.13-0.2.5.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/1.0.4/scala-parallel-collections_2.13-1.0.4.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/2.0.0/ujson_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/2.0.0/upack_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/2.0.0/upickle-implicits_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/0.7.1/geny_2.13-0.7.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/nscala-time/nscala-time_2.13/2.22.0/nscala-time_2.13-2.22.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/yaml/snakeyaml/1.26/snakeyaml-1.26.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.6/json4s-core_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.6/json4s-native-core_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/2.0.0/upickle-core_2.13-2.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/joda-time/joda-time/2.10.1/joda-time-2.10.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/joda/joda-convert/2.2.0/joda-convert-2.2.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.6/json4s-ast_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.6/json4s-scalap_2.13-4.0.6.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar [exists ]
Options:
-language:reflectiveCalls -deprecation -feature -Xcheckinit -Yrangepos -Xplugin-require:semanticdb




#### Error stacktrace:

```
scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.methodsParams$lzycompute(ArgCompletions.scala:34)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.methodsParams(ArgCompletions.scala:33)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.allParams$lzycompute(ArgCompletions.scala:85)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.allParams(ArgCompletions.scala:85)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.params$lzycompute(ArgCompletions.scala:87)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.params(ArgCompletions.scala:86)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.isParamName$lzycompute(ArgCompletions.scala:94)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.isParamName(ArgCompletions.scala:94)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.isName(ArgCompletions.scala:100)
	scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.compare(ArgCompletions.scala:103)
	scala.meta.internal.pc.completions.Completions$$anon$1.compare(Completions.scala:255)
	scala.meta.internal.pc.completions.Completions$$anon$1.compare(Completions.scala:211)
	java.base/java.util.TimSort.countRunAndMakeAscending(TimSort.java:355)
	java.base/java.util.TimSort.sort(TimSort.java:220)
	java.base/java.util.Arrays.sort(Arrays.java:1234)
	scala.collection.SeqOps.sorted(Seq.scala:728)
	scala.collection.SeqOps.sorted$(Seq.scala:719)
	scala.collection.immutable.List.scala$collection$immutable$StrictOptimizedSeqOps$$super$sorted(List.scala:79)
	scala.collection.immutable.StrictOptimizedSeqOps.sorted(StrictOptimizedSeqOps.scala:75)
	scala.collection.immutable.StrictOptimizedSeqOps.sorted$(StrictOptimizedSeqOps.scala:75)
	scala.collection.immutable.List.sorted(List.scala:79)
	scala.meta.internal.pc.CompletionProvider.completions(CompletionProvider.scala:79)
	scala.meta.internal.pc.ScalaPresentationCompiler.$anonfun$complete$1(ScalaPresentationCompiler.scala:225)
```
#### Short summary: 

java.lang.NullPointerException: Cannot invoke "scala.reflect.internal.Symbols$Symbol.isModule()" because the return value of "scala.meta.internal.pc.completions.ArgCompletions$ArgCompletion.methodSym()" is null