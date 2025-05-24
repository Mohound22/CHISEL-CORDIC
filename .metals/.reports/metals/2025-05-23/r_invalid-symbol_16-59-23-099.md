error id: 2319A26FC87DCC6D6E69A1307375A4E2
### scala.meta.internal.mtags.IndexingExceptions$InvalidSymbolException: 1000 idle cycles/ You can extend the timeout by calling #

Symbol: 1000 idle cycles/ You can extend the timeout by calling #

#### Error stacktrace:

```
scala.meta.internal.mtags.OnDemandSymbolIndex.definitions(OnDemandSymbolIndex.scala:61)
	scala.meta.internal.metals.DestinationProvider.definition(DefinitionProvider.scala:456)
	scala.meta.internal.metals.DestinationProvider.fromSymbol(DefinitionProvider.scala:494)
	scala.meta.internal.metals.DestinationProvider.fromSymbol(DefinitionProvider.scala:537)
	scala.meta.internal.metals.DefinitionProvider.fromSymbol(DefinitionProvider.scala:181)
	scala.meta.internal.metals.StacktraceAnalyzer.findLocationForSymbol$1(StacktraceAnalyzer.scala:74)
	scala.meta.internal.metals.StacktraceAnalyzer.$anonfun$fileLocationFromLine$2(StacktraceAnalyzer.scala:79)
	scala.PartialFunction$Unlifted.applyOrElse(PartialFunction.scala:347)
	scala.collection.IterableOnceOps.collectFirst(IterableOnce.scala:1256)
	scala.collection.IterableOnceOps.collectFirst$(IterableOnce.scala:1248)
	scala.collection.AbstractIterable.collectFirst(Iterable.scala:935)
	scala.meta.internal.metals.StacktraceAnalyzer.$anonfun$fileLocationFromLine$1(StacktraceAnalyzer.scala:79)
	scala.Option.flatMap(Option.scala:283)
	scala.meta.internal.metals.StacktraceAnalyzer.fileLocationFromLine(StacktraceAnalyzer.scala:77)
	scala.meta.internal.metals.StacktraceAnalyzer.workspaceFileLocationFromLine(StacktraceAnalyzer.scala:66)
	scala.meta.internal.metals.debug.DebugProxy.$anonfun$modifyLocationInTests$3(DebugProxy.scala:316)
	scala.collection.Iterator$$anon$10.nextCur(Iterator.scala:594)
	scala.collection.Iterator$$anon$10.hasNext(Iterator.scala:608)
	scala.collection.Iterator$$anon$10.hasNext(Iterator.scala:600)
	scala.meta.internal.mtags.MtagsEnrichments$XtensionIteratorCollection.headOption(MtagsEnrichments.scala:32)
	scala.meta.internal.metals.debug.DebugProxy.$anonfun$modifyLocationInTests$1(DebugProxy.scala:320)
	scala.collection.StrictOptimizedIterableOps.map(StrictOptimizedIterableOps.scala:100)
	scala.collection.StrictOptimizedIterableOps.map$(StrictOptimizedIterableOps.scala:87)
	scala.collection.convert.JavaCollectionWrappers$JListWrapper.map(JavaCollectionWrappers.scala:138)
	scala.meta.internal.metals.debug.DebugProxy.modifyLocationInTests(DebugProxy.scala:308)
	scala.meta.internal.metals.debug.DebugProxy.$anonfun$handleServerMessage$1(DebugProxy.scala:284)
	scala.meta.internal.metals.debug.DebugProxy.$anonfun$handleServerMessage$1$adapted(DebugProxy.scala:241)
	scala.meta.internal.metals.debug.ServerAdapter.$anonfun$onReceived$1(ServerAdapter.scala:25)
	scala.meta.internal.metals.debug.MessageIdAdapter.$anonfun$listen$1(MessageIdAdapter.scala:57)
	org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer.handleMessage(StreamMessageProducer.java:185)
	org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer.listen(StreamMessageProducer.java:97)
	scala.meta.internal.metals.debug.SocketEndpoint.listen(SocketEndpoint.scala:38)
	scala.meta.internal.metals.debug.MessageIdAdapter.listen(MessageIdAdapter.scala:47)
	scala.meta.internal.metals.debug.ServerAdapter.onReceived(ServerAdapter.scala:18)
	scala.meta.internal.metals.debug.DebugProxy.$anonfun$listenToServer$1(DebugProxy.scala:92)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:687)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:467)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1583)
```
#### Short summary: 

scala.meta.internal.mtags.IndexingExceptions$InvalidSymbolException: 1000 idle cycles/ You can extend the timeout by calling #