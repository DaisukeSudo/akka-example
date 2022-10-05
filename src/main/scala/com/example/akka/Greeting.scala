package com.example.akka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }

object Greeting:

  object Greeter:

    final case class Greet(whom: String, replyTo: ActorRef[Greeted])
    final case class Greeted(whom: String, from: ActorRef[Greet])

    def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
      context.log.info("Hello {}!", message.whom)
      message.replyTo ! Greeted(message.whom, context.self)
      Behaviors.same
    }

  object Echo:

    def apply(max: Int): Behavior[Greeter.Greeted] =
      bot(0, max)

    private def bot(counter: Int, max: Int): Behavior[Greeter.Greeted] =
      Behaviors.receive { (context, message) =>
        val n = counter + 1
        println(s"Greeting $n for ${message.whom}")
        if (n == max) {
          Behaviors.stopped
        } else {
          message.from ! Greeter.Greet(message.whom, context.self)
          bot(n, max)
        }
      }

  object GreetingMain:

    final case class SayHello(name: String)

    private[Greeting] def apply(): Behavior[SayHello] =
      Behaviors.setup { context =>
        val greeter = context.spawn(Greeter(), "greeter")

        Behaviors.receiveMessage { message =>
          val replyTo = context.spawn(Echo(max = 3), s"echo--${message.name}")
          greeter ! Greeter.Greet(message.name, replyTo)
          Behaviors.same
        }
      }

  def run(): Unit =
    def system = ActorSystem(GreetingMain(), "akka-example")
    system ! GreetingMain.SayHello("Alice")
    system ! GreetingMain.SayHello("Bob")
    system.terminate()
