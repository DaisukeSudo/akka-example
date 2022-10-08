# akka-example

## Contents
+ Akka でいろいろ検証してみた

### [Greeting](./src/main/scala/com/example/akka/Greeting.scala)
+ 一般的な Actor
+ GreetingMain -> Greeter -> Echo (-> Greeter -> Echo) * 3
+ Greeter の replyTo に Echo（別の子アクター）の ActorRef を設定する
+ Greeter は Echo に返事する
+ Echo は max 回数まで Greeter に投げ返す
+ 参考
    - https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html

### [Accumulating](./src/main/scala/com/example/akka/Accumulating.scala)
+ 永続的な Actor (Akka Persistence + Cluster Sharding)
+ AccumulatingMain -> Greeter
+ Add は 状態 (history) にメッセージを追加する
    - いったん Greeter の呼び出しを挟む
    - Greeted で戻ってきたメッセージを永続化する
+ Clear は 状態 (history) をクリアする 
+ 処理中は 状態 (processing) が true になる
    - Add で Greeter を呼んで，戻ってくるまで処理中になる
    - 処理中の Add / Clear コマンドは stash される
    - Greeted コマンドで 状態 (processing) が false になり，UnstashAll で保留中のコマンドが実行される
+ **`thenRun` のあとに `thenUnstashAll` が呼べないのはバグじゃないかと思われる**
+ 参考
     - https://doc.akka.io/docs/akka/current/typed/persistence.html

### [ApiCalling](./src/main/scala/com/example/akka/ApiCalling.scala)
+ akka-http をつかって API 呼び出しをしようと思ったけど無理だった（動かない）
    - Scala 3 に対応してない
    - **互換モードで 2.13 版のを使おうとしたが，他の akka 系が 3 対応版だとダメらしい**
+ ApiCallingMain -> Orchestrator -> Executor -> Receiver -> Orchestrator
+ Executor の replyTo のコマンド型と Orchestrator のコマンド型を合わせられなかったため  
  いったん Receiver を挟んでいる
+ Http() が implicit で `ClassicActorSystemProvider` を要求しているため ActorSystem の初期化に生成できない
    - spawn するときに `call: HttpRequest => Future[HttpResponse]` を渡すようにして回避
+ 本当は Executor をジェネリックにして HttpRequest と HttpResponse を抽象化したかった
    - object だとジェネリックにできないし，class だと Call コマンドの apply でエラーになってしまってダメだった
+ 参考
    - https://doc.akka.io/docs/akka-http/current/introduction.html#http-client-api

## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).
