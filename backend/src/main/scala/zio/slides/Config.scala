package zio.slides

import zio._
import zio.config._
import zio.config.magnolia._

case class Config(adminPassword: String)

object Config {
  val descriptor: ConfigDescriptor[Config] =
    DeriveConfigDescriptor.descriptor[Config]

  val live: ZLayer[system.System, Nothing, Has[Config]] =
    (ZConfig.fromPropertiesFile("../application.conf", descriptor) orElse ZConfig.fromSystemEnv(descriptor)).orDie
}
