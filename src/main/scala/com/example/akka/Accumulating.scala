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

  sealed trait Command
  final case class Add(data: String) extends Command
  case object Clear extends Command

  sealed trait Event
  final case class Added(data: String) extends Event
  case object Cleared extends Event

  final case class State(history: List[String] = Nil)

  def apply(id: String): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("id: {}", id)
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(),
        commandHandler = { (_: State, command: Command) =>
          command match {
            case Add(data) => Effect.persist(Added(data))
              .thenRun(state => context.log.info("Added   > state: {}", state))
            case Clear => Effect.persist(Cleared)
              .thenRun(state => context.log.info("Cleared > state: {}", state))
          }
        },
        eventHandler = { (state, event) =>
          event match {
            case Added(data) => State(data :: state.history.take(4))
            case Cleared => State(Nil)
          }
        },
      )
    }

  def run(): Unit =
    val system = ActorSystem(Behaviors.empty, "akka-example")
    val sharding = ClusterSharding(system)
    val typeKey = EntityTypeKey[Command]("Accumulating")
    val aRef = sharding.init(Entity(typeKey = typeKey) { context => Accumulating(context.entityId) }) // ActorRef[ShardingEnvelope[Command]]
    val eRef = sharding.entityRefFor(typeKey, "abc") // EntityRef[Command]

    eRef ! Add("Alice")
    eRef ! Add("Bob")
    eRef ! Clear
    eRef ! Add("Eve")

    io.StdIn.readLine()
    system.terminate()
