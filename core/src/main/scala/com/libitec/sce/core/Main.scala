package com.libitec.sce.core

import java.io.File

import scala.concurrent.duration._

import scala.concurrent.Await

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

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

    implicit val timeout = Timeout(1 second)

    val sync = SimpleSync()
    doer1 ! sync(LoadSku("Sku for doer1 ", Map(3 → "-33", 6 → "-66")))
    doer2 ! sync(LoadSku("Sku for doer2 ", Map(12 → "-33", 211 → "-66")))

    if (sync.ok()) {
      println("\n | OK! \n |\n")
    }
  }
}


trait ActorSystemComponent {
  implicit val actorSystem = ActorSystem("eventsourced-example")
}


case class LoadSku(sku: String, list: Map[Int, String])
case class AnySimpleMessage(text: String)
case class Success(msg: Any)
case object Success


trait DoerComponent {
  this: ActorSystemComponent ⇒


  def createDoer(id: Long) =
    actorSystem.actorOf(Props(new Doer(id)), name = s"doer-$id")

  val doerJournal = Journal(JournalioJournalProps(new File("target/journal/doers")))
  val doerExtension = EventsourcingExtension(actorSystem, doerJournal)


  /**
   * Actor
   */
  class Doer(doerId: Long) extends Actor with ActorLogging with EventsourcedSupport {

    var payload = Set.empty[String]
    var cnt = 0

    val actorStore = doerExtension.processorOf(Props(new DoerStore with Receiver with Eventsourced { val id = doerId.toInt } ))(context)
    doerExtension.recover(id ⇒ if (id == doerId) Some(0) else None, 1 minute)


    class DoerStore extends Actor { this: Receiver ⇒
      def receive = {
        case LoadSku(sku, list) ⇒
          cnt += 1
          payload += s"$sku - $cnt"
          println(s"[Doer$doerId] event = $sku $cnt")
          sender ! Success(message) // TODO: should be autoreplied with AutoConfirmed trait
      }
    }


    def receive =  {
      // save message without transaction
      case AnySimpleMessage(text) ⇒
        save(('MyCustomEvent, text)) // throw exception if can not write


      // save with distributed transaction
      case sync @ SimpleSync(LoadSku(sku, ls)) ⇒
        if (hasReservedSku(sku)) {

//          node.actor ! sync(UnloadSku(sku)) // include other members to this transaction

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

    private def hasReservedSku(sku: String) = true
  }


  /**
   * Helper functions
   */
  trait EventsourcedSupport {

    def actorStore: ActorRef

    protected def save(event: Any) {
      implicit val timeout = Timeout(5 seconds)

      Await.result(actorStore ? Message(event), timeout.duration) match {
        case Success(_) ⇒ // ok, skip
        case error ⇒ throw new Exception(s"Write error! $error")
      }
    }

    protected def save(event: Any, sync: SimpleSync) {
      implicit val timeout = Timeout(5 seconds)

      Await.result(actorStore ? Message(event), timeout.duration) match {
        case Success(msg) ⇒
          if (!sync.ok()) {
            actorStore ! Delete(msg) // rollback written message
            throw new Exception("Sync error!")
          }

        case error ⇒
          sync.fail("Transaction failed — write error!")
          throw new Exception(s"Write error! $error")
      }
    }

  }
}
