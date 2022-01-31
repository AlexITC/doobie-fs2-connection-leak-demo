package sample

import cats.effect._
import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForEach
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import fs2.Stream
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.DurationInt

class SampleSpec extends AnyWordSpec with TestContainerForEach {
  private val postgresImage = DockerImageName.parse("postgres:13")
  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(dockerImageName = postgresImage)

  private val logger = LoggerFactory.getLogger(this.getClass)

  "sample" should {
    "work" in withContainers { postgres =>
      val transactor: Resource[IO, HikariTransactor[IO]] =
        for {
          ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
          xa <- HikariTransactor
            .newHikariTransactor[IO](
              "org.postgresql.Driver", // driver classname
              postgres.jdbcUrl, // connect URL
              postgres.username, // username
              postgres.password, // password
              ce // await connection here
            )
            .evalTap(tx => IO(tx.kernel.setAutoCommit(false)))
            .evalTap(tx => IO(tx.kernel.setMaxLifetime(30000)))
            .evalTap(tx => IO(tx.kernel.setLeakDetectionThreshold(2000)))
            .evalTap(tx => IO(tx.kernel.setConnectionTimeout(2000)))
            .evalTap(tx => IO(tx.kernel.setMaximumPoolSize(1)))
        } yield xa

      def increasingStream(start: Int): fs2.Stream[IO, Int] = {
        Stream.emit(start) ++ increasingStream(start + 1)
      }

      transactor
        .use { xa =>
          val dbStream =
            sql"select table_catalog from information_schema.tables where 1 = 0"
              .query[Int]
              .stream
              .transact(xa)
              .onFinalize(IO(logger.info("Db connection returned to the pool")))

          logger.info(
            "dbStream picks a connection from the pool which is returned when dbStream is consumed (as expected)"
          )
          (dbStream ++ increasingStream(1))
            .evalMap { x =>
              IO(logger.info(s"Step: $x")) *> IO.sleep(30.seconds)
            }
            .take(2)
            .compile
            .drain
        }
        .unsafeRunSync()

      transactor
        .use { xa =>
          val dbStream =
            sql"select table_catalog from information_schema.tables where 1 = 0".query[Int].stream.transact(xa)

          logger.warn(
            "dbStream picks a connection from the pool which is returned to the pool until the 2nd stream finishes"
          )
          (dbStream ++ increasingStream(1))
            .evalMap { x =>
              IO(logger.info(s"Step: $x")) *> IO.sleep(30.seconds)
            }
            .take(2)
            .compile
            .drain
        }
        .unsafeRunSync()
    }
  }
}
