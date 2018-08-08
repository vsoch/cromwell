package cromwell.filesystems.ftp

import java.net.InetAddress
import java.nio.charset.Charset
import java.util

import cats.syntax.apply._
import cats.syntax.validated._
import com.github.robtimus.filesystems.ftp.{ConnectionMode, FTPEnvironment}
import com.typesafe.config.Config
import common.validation.ErrorOr.ErrorOr
import common.validation.Validation._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.duration._

case class FtpConfiguration(ftpCredentials: FtpCredentials,
                            cacheTTL: FiniteDuration,
                            fTPEnvironment: FTPEnvironment = new FTPEnvironment()) {
  def authenticated(newCredentials: FtpAuthenticatedCredentials) = {
    this.copy(
      ftpCredentials = newCredentials,
      fTPEnvironment = fTPEnvironment.clone().withCredentials(newCredentials.username, newCredentials.password.toCharArray)
    )
  }
}

object FtpConfiguration {
  // Copied from com.github.robtimus.filesystems.ftp.FTPEnvironment
  // Some have been omitted on purpose because not easily serializable to HOCON
  private val LOCAL_ADDR = "localAddr"
  private val LOCAL_PORT = "localPort"
  private val SO_TIMEOUT = "soTimeout"
  private val SEND_BUFFER_SIZE = "sendBufferSize"
  private val RECEIVE_BUFFER_SIZE = "receiveBufferSize"
  private val TCP_NO_DELAY = "tcpNoDelay"
  private val KEEP_ALIVE = "keepAlive"
  private val SO_LINGER_ON = "soLinger.on"
  private val SO_LINGER_VALUE = "soLinger.val"
  private val CONNECT_TIMEOUT = "connectTimeout"
  private val CHARSET = "charset"
  private val CONTROL_ENCODING = "controlEncoding"
  private val STRICT_MULTILINE_PARSING = "strictMultilineParsing"
  private val DATA_TIMEOUT = "dataTimeout"
  private val REMOTE_VERIFICATION_ENABLED = "remoteVerificationEnabled"
  private val CONNECTION_MODE = "connectionMode"
  private val ACTIVE_PORT_RANGE_MIN = "activePortRange.min"
  private val ACTIVE_PORT_RANGE_MAX = "activePortRange.max"
  private val ACTIVE_EXTERNAL_IP_ADDRESS = "activeExternalIPAddress"
  private val PASSIVE_LOCAL_IP_ADDRESS = "passiveLocalIPAddress"
  private val REPORT_ACTIVE_EXTERNAL_IP_ADDRESS = "reportActiveExternalIPAddress"
  private val BUFFER_SIZE = "bufferSize"
  private val SEND_DATA_SOCKET_BUFFER_SIZE = "sendDataSocketBufferSize"
  private val RECEIVE_DATA_SOCKET_BUFFER_SIZE = "receiveDataSocketBufferSize"
  private val USE_EPSV_WITH_IPV4 = "useEPSVwithIPv4"
  private val CONTROL_KEEP_ALIVE_TIMEOUT = "controlKeepAliveTimeout"
  private val CONTROL_KEEP_ALIVE_REPLY_TIMEOUT = "controlKeepAliveReplyTimeout"
  private val PASSIVE_NAT_WORKAROUND = "passiveNatWorkaround"
  private val AUTODETECT_ENCODING = "autodetectEncoding"
  private val CLIENT_CONNECTION_COUNT = "clientConnectionCount"
  private val SUPPORT_ABSOLUTE_FILE_PATHS = "supportAbsoluteFilePaths"
  private val CALCULATE_ACTUAL_TOTAL_SPACE = "calculateActualTotalSpace"
  
  implicit val iNetAddressConfigReader = new ValueReader[InetAddress] {
    override def read(config: Config, path: String) = {
      InetAddress.getByName(config.getString(path))
    }
  }

  implicit val charsetConfigReader = new ValueReader[Charset] {
    override def read(config: Config, path: String) = {
      Charset.forName(config.getString(path))
    }
  }

  implicit val connectionModeConfigReader = new ValueReader[Option[ConnectionMode]] {
    override def read(config: Config, path: String) = if (config.hasPath(path)) {
      Option(ConnectionMode.valueOf(config.getString(path).toUpperCase))
    } else None
  }
  
  // TTL needs to be sufficiently large such that no job can be still running when the filesystem is closed and evicted from the cache
  lazy val Default = FtpConfiguration(FtpAnonymousCredentials, 24.hours, new FTPEnvironment())
  
  def apply(conf: Config): FtpConfiguration = {
    val credentials: ErrorOr[FtpCredentials] = conf.getAs[Config]("auth") map { authConfig =>
      val username = validate { authConfig.as[String]("username") }
      val password = validate { authConfig.as[String]("password") }
      (username, password) mapN FtpAuthenticatedCredentials.apply
    } getOrElse Default.ftpCredentials.validNel

    val cacheTTL: ErrorOr[FiniteDuration] = validate { conf.getAs[FiniteDuration]("cache-ttl").getOrElse(Default.cacheTTL) }
    
    val ftpEnvironment: ErrorOr[FTPEnvironment] = {
      credentials.map({ c =>
        val configReader = new FtpEnvironmentValueReader(c)
        conf.getAs[FTPEnvironment]("client")(configReader).getOrElse(Default.fTPEnvironment)
      })
    }

    val validatedConfiguration = (credentials, cacheTTL, ftpEnvironment) mapN FtpConfiguration.apply

    validatedConfiguration.unsafe("FTP configuration is not valid")
  }
  
  private class FtpEnvironmentValueReader(ftpCredentials: FtpCredentials) extends ValueReader[Option[FTPEnvironment]] {
    override def read(config: Config, path: String) = {
      val envMap = new util.HashMap[String, Object]()

      if (config.hasPath(path)) {
        val clientConfig = config.getConfig(path)
        List(
          LOCAL_ADDR -> clientConfig.getAs[InetAddress](LOCAL_ADDR),
          LOCAL_PORT -> clientConfig.getAs[Int](LOCAL_PORT),
          SO_TIMEOUT -> clientConfig.getAs[FiniteDuration](SO_TIMEOUT).map(_.toMillis.toInt),
          SEND_BUFFER_SIZE -> clientConfig.getAs[Int](SEND_BUFFER_SIZE),
          RECEIVE_BUFFER_SIZE -> clientConfig.getAs[Int](RECEIVE_BUFFER_SIZE),
          TCP_NO_DELAY -> clientConfig.getAs[Boolean](TCP_NO_DELAY),
          KEEP_ALIVE -> clientConfig.getAs[Boolean](KEEP_ALIVE),
          SO_LINGER_ON -> clientConfig.getAs[Boolean](SO_LINGER_ON),
          SO_LINGER_VALUE -> clientConfig.getAs[Int](SO_LINGER_VALUE),
          CONNECT_TIMEOUT -> clientConfig.getAs[FiniteDuration](CONNECT_TIMEOUT).map(_.toMillis.toInt),
          CHARSET -> clientConfig.getAs[Charset](CHARSET),
          CONTROL_ENCODING -> clientConfig.getAs[String](CONTROL_ENCODING),
          STRICT_MULTILINE_PARSING -> clientConfig.getAs[Boolean](STRICT_MULTILINE_PARSING),
          DATA_TIMEOUT -> clientConfig.getAs[FiniteDuration](DATA_TIMEOUT).map(_.toMillis.toInt),
          REMOTE_VERIFICATION_ENABLED -> clientConfig.getAs[Boolean](REMOTE_VERIFICATION_ENABLED),
          CONNECTION_MODE -> clientConfig.getAs[ConnectionMode](CONNECTION_MODE),
          ACTIVE_PORT_RANGE_MIN -> clientConfig.getAs[Int](ACTIVE_PORT_RANGE_MIN),
          ACTIVE_PORT_RANGE_MAX -> clientConfig.getAs[Int](ACTIVE_PORT_RANGE_MAX),
          ACTIVE_EXTERNAL_IP_ADDRESS -> clientConfig.getAs[String](ACTIVE_EXTERNAL_IP_ADDRESS),
          PASSIVE_LOCAL_IP_ADDRESS -> clientConfig.getAs[String](PASSIVE_LOCAL_IP_ADDRESS),
          REPORT_ACTIVE_EXTERNAL_IP_ADDRESS -> clientConfig.getAs[String](REPORT_ACTIVE_EXTERNAL_IP_ADDRESS),
          BUFFER_SIZE -> clientConfig.getAs[Int](BUFFER_SIZE),
          SEND_DATA_SOCKET_BUFFER_SIZE -> clientConfig.getAs[Int](SEND_DATA_SOCKET_BUFFER_SIZE),
          RECEIVE_DATA_SOCKET_BUFFER_SIZE -> clientConfig.getAs[Int](RECEIVE_DATA_SOCKET_BUFFER_SIZE),
          USE_EPSV_WITH_IPV4 -> clientConfig.getAs[Boolean](USE_EPSV_WITH_IPV4),
          CONTROL_KEEP_ALIVE_TIMEOUT -> clientConfig.getAs[FiniteDuration](CONTROL_KEEP_ALIVE_TIMEOUT).map(_.toMillis.toInt),
          CONTROL_KEEP_ALIVE_REPLY_TIMEOUT -> clientConfig.getAs[FiniteDuration](CONTROL_KEEP_ALIVE_REPLY_TIMEOUT).map(_.toMillis.toInt),
          PASSIVE_NAT_WORKAROUND -> clientConfig.getAs[Boolean](PASSIVE_NAT_WORKAROUND),
          AUTODETECT_ENCODING -> clientConfig.getAs[Boolean](AUTODETECT_ENCODING),
          CLIENT_CONNECTION_COUNT -> clientConfig.getAs[Int](CLIENT_CONNECTION_COUNT),
          SUPPORT_ABSOLUTE_FILE_PATHS -> clientConfig.getAs[Boolean](SUPPORT_ABSOLUTE_FILE_PATHS),
          CALCULATE_ACTUAL_TOTAL_SPACE -> clientConfig.getAs[Boolean](CALCULATE_ACTUAL_TOTAL_SPACE)
        ) foreach {
          case (key, Some(value)) => envMap.put(key, value.asInstanceOf[AnyRef])
          case _ =>
        }
      }

      ftpCredentials match {
        case FtpAuthenticatedCredentials(username, password) => 
          envMap.put("username", username)
          envMap.put("password", password.toCharArray)
        case _ =>
      }

      Option(new FTPEnvironment(envMap))
    }
  }
}
