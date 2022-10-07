package com.example.akka

import akka.actor
import akka.actor.ClassicActorSystemProvider
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

object ApiCalling:

  object Executor:

    sealed trait Command
    final case class Call(request: HttpRequest, replyTo: ActorRef[Try[HttpResponse]]) extends Command
    private final case class ReturnToSelf(response: Try[HttpResponse], replyTo: ActorRef[Try[HttpResponse]]) extends Command

    def apply(call: HttpRequest => Future[HttpResponse]): Behavior[Command] =
      Behaviors.receive { (context, command) =>
        command match {
          case Call(request, replyTo) =>
            context.pipeToSelf(call(request)) {
              x => ReturnToSelf(x, replyTo)
            }
          case ReturnToSelf(result, replyTo) =>
            replyTo ! result
        }
        Behaviors.same
      }


  object Orchestrator:

    sealed trait Command
    final case class Get(uri: Uri) extends Command
    private final case class Receive(response: Try[HttpResponse]) extends Command

    private object Receiver:
      def apply(replyTo: ActorRef[Orchestrator.Command]): Behavior[Try[HttpResponse]] =
        Behaviors.receiveMessage { message =>
          replyTo ! Orchestrator.Receive(message)
          Behaviors.same
        }

    private[ApiCalling] def apply(call: HttpRequest => Future[HttpResponse]): Behavior[Command] =
      Behaviors.receive { (context, message) =>
        message match {
          case Get(uri) =>
            val id = java.util.UUID.randomUUID.toString
            val executor = context.spawn(Executor(call), s"executor-$id")
            val receiver = context.spawn(Receiver(context.self), s"receiver-$id")
            executor ! Executor.Call(HttpRequest(uri = uri), receiver.ref)
          case Receive(response) =>
            response match {
              case Success(x) => context.log.info(x.toString)
              case Failure(e) => context.log.error(e.toString)
            }
        }
        Behaviors.same
      }

  object ApiCallingMain:
    sealed trait Command
    final case class Run(call: HttpRequest => Future[HttpResponse]) extends Command

    def apply(): Behavior[Command] =
      Behaviors.receive { (context, message) =>
        message match {
          case Run(call) =>
            val orchestrator = context.spawn(Orchestrator(call), "orchestrator")
            orchestrator ! Orchestrator.Get("http://localhost/alice.json")
            orchestrator ! Orchestrator.Get("http://localhost/bob.json")
        }
        Behaviors.same
      }

  def run(): Unit =
    val system = ActorSystem(ApiCallingMain(), "akka-example")
    implicit val classicSystem: actor.ClassicActorSystemProvider = system.classicSystem
    implicit val executionContext: ExecutionContextExecutor = system.executionContext
    system ! ApiCallingMain.Run(res => Http().singleRequest(res))
    system.terminate()
    io.StdIn.readLine()
    ()
