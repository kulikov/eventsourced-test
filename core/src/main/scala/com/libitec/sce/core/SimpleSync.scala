package com.libitec.sce.core

import akka.transactor.{CoordinatedTransactionException, Coordinated}
import akka.util.Timeout


/**
 * Very simple synchronization API for two or more actors
 *
 * Implementation uses coordinated transaction from actor.transactor and agent
 *
 * Usage pattern:
 *
 * Initiator:
 * {{{
 * val sync = SimpleSync()
 * secondActor ! sync(SomeMessage)
 *
 * if (sync.syncOk()) { // blocks!
 *   // good scenario code
 * }
 * else {
 *   // bad scenario code
 * }
 * }}}
 *
 * Second actor:
 * {{{
 * def receive = {
 *   case sync @ SimpleSync(message) ⇒ {
 *     if (readyToSync()) { // some business logic
 *       if (sync.syncOk()) { // blocks!
 *         // good scenario code
 *       }
 *       else {
 *         // bad scenario code
 *       }
 *     }
 *     else {
 *       sync.syncFail()
 *     }
 *   }
 * }
 * }}}
 *
 * @since  16.03.12 14:59
 * @author davojan
 */
class SimpleSync(val message: Any, coordinated: Coordinated) {

  private var _lastError: Any = _

  /**
   * Return last error message from syncFail call
   * @since 18.07.12 17:14
   * @author kulikov
   */
  def lastError = _lastError

  /**
   * Creates additional synchronization member
   * @since  16.03.12 15:11
   * @author davojan
   */
  def apply(message: Any = null) = {
    new SimpleSync(message, coordinated(message))
  }

  /**
   * Increases the commit counter and waits for other members to complete
   * @since  16.03.12 15:20
   * @author davojan
   * @return true if all members is successfully synchronized, false - otherwise
   */
  def ok(): Boolean = {
    try coordinated atomic { implicit txn ⇒
      // nothing to do, just synchronization
    } catch {
      case e: Exception ⇒
        println("\n | " + e + "\n |\n")
        return false
    }
    true
  }

  /**
   * Cancels synchronization transaction causing the whole sync to fail
   * @since  16.03.12 15:38
   * @author davojan
   */
  def fail(info: Any) {
    try coordinated atomic { implicit txn ⇒
      _lastError = info
      throw new Exception("Transaction failed! Reason: %s".format(info))
    } catch {
      case e: Exception ⇒ {
        println("\n !!! SympleSync fail: " + e.getMessage + "\n")
      }
    }
  }

  /**
   * @since 05.10.12 19:37
   * @author kulikov
   */
  override def toString = "SimpleSync("+ message +")"
}

object SimpleSync {

  /**
   * Creates initial synchronization member
   * @since  16.03.12 15:04
   * @author davojan
   */
  def apply(message: Any = null)(implicit timeout: Timeout) =
    new SimpleSync(message, Coordinated(message)(timeout))

  /**
   * Behaviour like case-class
   * @since  16.03.12 16:11
   * @author davojan
   */
  def unapply(s: SimpleSync): Option[Any] = Some(s.message)
}
