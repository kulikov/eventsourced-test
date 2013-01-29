package com.libitec.sce.core

import java.io.File

import scala.concurrent.duration._
import scala.language.implicitConversions

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import org.eligosource.eventsourced.core._
import org.eligosource.eventsourced.journal._
import scala.concurrent.ExecutionContext


object Main extends App {

  println("Start...")

  implicit val system = ActorSystem("example")

  val doerJournal = Journal(JournalioJournalProps(new File("target/journal/doers")))
  val doerExtension = EventsourcingExtension(system, doerJournal)

  val doerStore = doerExtension.processorOf(ProcessorProps(new DoerStore with Receiver with Eventsourced { val id = 1 } ))
  val doer = system.actorOf(Props(new Doer(doerStore)), name = "doer-1")

  doerExtension.recover()




  transaction { sync ⇒
    doer ! sync(LoadSku("5 books"))
    node ! sync(UnloadSku("5 books"))
  }





}


//
//
//case class LoadSku(sku: String)
//
//
//class Doer(doerStore: ActorRef) extends Actor {
//
//  lazy val payload = Await.ready(doerStore ? GetPaylod, 10 seconds).asInstanceOf[Payload]
//
//  doerStore.underlaingActor.payload
//
//  def receive = {
//    case sync @ LoadSku(sku) ⇒
//      implicit val timeout = Timeout(1 second)
//
//      if (!payload.hasSku(sku)) {
//
//        store(LoadSku(sku), sync) { p =>
//          payload = p
//        }
//      } else
//        sync.fail("Sku already has!")
//  }
//
//  private def store[T](sync: SympleSync, msg: Any)(callback: ⇒ T): T = {
//    if (sync.ok()) {
//      Await.ready(doerStore ? Message(msg)) onComplete {
//        case Right(Success(msg)) ⇒
//          try callback()
//          catch { case e: Exception ⇒
//            doerStore ! Revert(msg)
//          }
//        case error ⇒
//          throw new Exception
//      }
//    } else {
//      log.error("Transaction failed!")
//    }
//  }
//}
//
//
//class DoerStore extends Actor {
//  var payload = new Payload
//
//  def receive = {
//    case LoadSku(sku) ⇒
//      payload.addSku(sku)
//      println("[Doer] event = %s (%s)" format (msg, payload))
//      sender ! "ok"
//  }
//}
