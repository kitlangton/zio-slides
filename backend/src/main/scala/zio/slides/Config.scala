package zio.slides

import zio._
import zio.config._
import zio.config.magnolia._

case class Config(adminPassword: String)

object Config {
  val descriptor: _root_.zio.config.ConfigDescriptor[Config] = Descriptor[Config].desc

  val live: ZLayer[Any, Nothing, Config] =
    ZConfig.fromPropertiesFile("../application.conf", descriptor).orDie
}
