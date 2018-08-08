package cromwell.filesystems.ftp

import java.util.Collections

import cats.syntax.apply._
import cats.syntax.validated._
import com.typesafe.config.Config
import common.validation.ErrorOr.ErrorOr
import common.validation.Validation._

import scala.util.Try
import cromwell.core.WorkflowOptions
import net.ceedubs.ficus.Ficus._
import scala.collection.JavaConverters._
import scala.concurrent.duration._

case class FtpConfiguration(ftpCredentials: FtpCredentials, cacheTTL: FiniteDuration, clientConfig: java.util.Map[String, Object] = Collections.emptyMap())

object FtpConfiguration {
  // Needs to be sufficiently large such that no job can be still running when the filesystem is closed and evicted from the cache
  val defaultCacheTTL = 24.hours

  def apply(conf: Config): FtpConfiguration = {
    val credentials = conf.getAs[Config]("auth") match {
      case Some(authConfig) =>
        val username = validate { authConfig.as[String]("username") }
        val password = validate { authConfig.as[String]("password") }
        (username, password) mapN FtpAuthenticatedCredentials.apply
      case None => FtpAnonymousCredentials.validNel
    }
    
    val cacheTTL = conf.getAs[FiniteDuration]("cacheTTL").getOrElse(defaultCacheTTL)
    
    val customConfig: Try[java.util.Map[String, Object]] = Try{conf.getAs[Config]("client") match {
      case Some(config) => config.entrySet().asScala
        .map({ element => element.getKey -> element.getValue.unwrapped() })
        .toMap.asJava
      case None => Collections.emptyMap[String, Object]()
    }}

    FtpConfiguration(credentials.unsafe("FTP configuration is not valid"), cacheTTL, customConfig.get)
  }

  def apply(options: WorkflowOptions): Option[FtpConfiguration] = {
    val credentials: ErrorOr[Option[FtpCredentials]] = {
      (options.get("ftp-username").toOption, options.get("ftp-password").toOption) match {
        case (Some(username), Some(password)) => Option(FtpAuthenticatedCredentials(username, password)).validNel
        case (None, None) => None.validNel
        case _ => "Supply both ftp username and password, or none of them to default back to Cromwell's FTP configuration".invalidNel
      }
    }

    credentials.unsafe("FTP configuration is not valid").map(FtpConfiguration(_, defaultCacheTTL))
  }
}
