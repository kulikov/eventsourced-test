package com.libitec.sce.core

import java.io.File

import scala.concurrent.duration._

import akka.actor._

import org.eligosource.eventsourced.core._
import org.eligosource.eventsourced.journal._


object Main extends App {
  println("Start...")

  new Production().init()
}


class Production
  extends ActorSystemComponent
  with DoerComponent {

  def init() {

    val doer1 = createDoer(1)
    val doer2 = createDoer(2)

    for (n ← 1 to 10000) {
      doer1 ! LoadSku("Sku for doer1 " + n, Map(3 → "-33", 6 → "-66"))
      doer2 ! LoadSku("Sku for doer2 " + n, Map(12 → "-33", 211 → "-66"))
    }
  }
}


trait ActorSystemComponent {
  implicit val actorSystem = ActorSystem("eventsourced-example")
}


case class LoadSku(sku: String, list: Map[Int, String])
case class AnySimpleMessage(text: String)


trait DoerComponent {
  this: ActorSystemComponent ⇒


  def createDoer(id: Long) =
    actorSystem.actorOf(Props(new Doer(id)), name = s"doer-$id")

  val doerJournal = Journal(JournalioJournalProps(new File("target/journal/doers")))
  val doerExtension = EventsourcingExtension(actorSystem, doerJournal)


  /**
   * Actor
   */
  class Doer(doerId: Long) extends Actor with EventsourcedSupport {

    var payload = Set.empty[String]
    var cnt = 0

    val doerStore = doerExtension.processorOf(Props(new DoerStore with Receiver with Eventsourced { val id = doerId.toInt } ))(context)
    doerExtension.recover(id ⇒ if (id == doerId) Some(0) else None, 1 minute)


    class DoerStore extends Actor {
      def receive = {
        case LoadSku(sku, list) ⇒
          cnt += 1
          payload += s"$sku - $cnt"
          println(s"[Doer$doerId] event = $sku $cnt")
          sender ! Success // TODO: should be autoreplied with AutoConfirmed trait
      }
    }


    def receive =  {
      // save message without transaction
      case AnySimpleMessage(text) ⇒
        save(('MyCustomEvent, text)) // throw exception if can not write


      // save with distributed transaction
      case sync @ Sync(LoadSku(sku, ls)) ⇒
        if (hasReservedSku(sku)) {

          node.actor ! sync(UnloadSku(sku)) // include other members to this transaction

          try {
            save(LoadSku(sku, ls), sync)

            // do some staff this
            log.info("Sku successfull loaded!")
          } catch {
            case e: Exception ⇒ log.error(s"Error load sku on Doer($doerId). Reason: $e")
          }
        } else {
          sync.fail("No reserved sku here!")
        }
    }
  }


  /**
   * Helper functions
   */
  trait EventsourcedSupport {

    protected def save(event: Any) {
      Await.ready(doerStore ? Message(event)) match {
        case Success(_) ⇒ // ok, skip
        case error ⇒ throw new Exception(s"Write error! $error")
      }
    }

    protected def save(event: Any, sync: Coordinator) {
      Await.ready(doerStore ? Message(event)) match {
        case Success(msg) ⇒
          if (!sync.ok()) {
            doerStore ! Delete(msg) // rollback written message
            throw new Exception("Sync error!")
          }

        case other ⇒
          sync.fail("Transaction failed — write error!")
          throw new Exception(s"Write error! $error")
      }
    }

  }
}
