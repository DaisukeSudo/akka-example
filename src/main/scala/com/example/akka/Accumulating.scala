package com.example.akka

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object Accumulating:

  object Greeter:

    final case class Greet(name: String, replyTo: ActorRef[AccumulatingMain.Greeted])

    def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
      val msg = s"Hello ${message.name}!"
      context.log.info(msg)
      message.replyTo ! AccumulatingMain.Greeted(msg)
      Behaviors.same
    }

  object AccumulatingMain:

    sealed trait Command
    final case class Add(name: String) extends Command
    case object Clear extends Command
    final case class Greeted(msg: String) extends Command

    sealed trait Event
    final case class Started() extends Event
    final case class Finished(msg: String) extends Event
    case object Cleared extends Event

    final case class State(history: List[String] = Nil, processing: Boolean = false)

    def apply(id: String): Behavior[Command] =
      Behaviors.setup { context =>
        context.log.info("accumulating id: {}", id)
        val greeter = context.spawn(Greeter(), id)

        EventSourcedBehavior[Command, Event, State](
          persistenceId = PersistenceId.ofUniqueId(id),
          emptyState = State(),
          commandHandler = { (state: State, command: Command) =>
            command match {
              case Add(name) =>
                context.log.info("Add: {}, {}", name, state)
                if (state.processing)
                  Effect.stash()
                else
                  greeter ! Greeter.Greet(name, context.self)
                  Effect.persist(Started())
                    .thenRun(state => context.log.info("Started  > state: {}", state))
              case Greeted(msg) =>
                context.log.info("Greeted: {}, {}", msg, state)
                if (!state.processing)
                  Effect.none
                else
                  Effect.persist(Finished(msg))
                    // .thenRun(state => context.log.info("Finished > state: {}", state))
                    .thenUnstashAll()
              case Clear =>
                context.log.info("Clear, {}", state)
                if (state.processing)
                  Effect.stash()
                else
                  Effect.persist(Cleared)
                    .thenRun(state => context.log.info("Cleared  > state: {}", state))
            }
          },
          eventHandler = { (state, event) =>
            event match {
              case Started() => state.copy(processing = true)
              case Finished(msg) => State(msg :: state.history.take(4))
              case Cleared => State()
            }
          },
        )
      }

  def run(): Unit =
    val system = ActorSystem(Behaviors.empty, "akka-example")
    val sharding = ClusterSharding(system)
    val typeKey = EntityTypeKey[AccumulatingMain.Command]("Accumulating")
    val aRef = sharding.init(Entity(typeKey = typeKey) { context => AccumulatingMain(context.entityId) }) // ActorRef[ShardingEnvelope[Command]]
    val eRef = sharding.entityRefFor(typeKey, "abc") // EntityRef[Command]

    eRef ! AccumulatingMain.Add("Alice")
    eRef ! AccumulatingMain.Add("Bob")
    eRef ! AccumulatingMain.Clear
    eRef ! AccumulatingMain.Add("Eve")
    eRef ! AccumulatingMain.Add("Franky")
    eRef ! AccumulatingMain.Clear

    io.StdIn.readLine()
    system.terminate()
