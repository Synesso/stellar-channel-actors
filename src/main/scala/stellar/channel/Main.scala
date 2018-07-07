package stellar.channel

import akka.actor.{ActorSystem, Props}
import com.typesafe.scalalogging.LazyLogging
import stellar.channel.Channel.{Close, Pay}
import stellar.sdk.op.PaymentOperation
import stellar.sdk.resp.TransactionPostResp
import stellar.sdk.{Amount, KeyPair, TestNetwork, Transaction}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object PayWithChannels extends LazyLogging {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("stellar-channels")
    import system.dispatcher

    val primaryAccount = KeyPair.random
    TestNetwork.fund(primaryAccount).foreach { _ =>

      logger.debug(s"funded $primaryAccount")

      val recipient = KeyPair.fromAccountId("GAQUWIRXODT4OE3YE6L4NF3AYSR5ACEHPINM5S3J2F4XKH7FRZD4NDW2")

      val channel = system.actorOf(Props(classOf[Channel], primaryAccount, TestNetwork, 24))

      (0 to 255) foreach { _ =>
        channel ! Pay(recipient, Amount.lumens(1))
      }
      channel ! Close
    }

  }

}


object PayDirectly extends LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val network = TestNetwork

  def main(args: Array[String]): Unit = {

    val primaryAccount = KeyPair.random
    val recipient = KeyPair.fromAccountId("GAQUWIRXODT4OE3YE6L4NF3AYSR5ACEHPINM5S3J2F4XKH7FRZD4NDW2")

    val results: Future[Seq[TransactionPostResp]] = for {
      _ <- network.fund(primaryAccount)
      source <- network.account(primaryAccount).map(_.toAccount)
      results <- Future.sequence {
        (0 to 31) map { i =>
          val sourceWithSeqNo = source.copy(sequenceNumber = source.sequenceNumber + i)
          Future.fromTry(Transaction(sourceWithSeqNo)
            .add(PaymentOperation(recipient, Amount.lumens(1)))
            .sign(primaryAccount)
          ).flatMap(network.submit) recoverWith {
            case failure =>
              logger.debug(s"Failed to transact: $failure")
              throw failure
          }
        }
      }
    } yield results

    Await.result(results, 5.minutes)
  }
}
