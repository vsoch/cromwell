package cromwell.filesystems.ftp

import java.net.InetAddress
import java.nio.charset.Charset

import com.github.robtimus.filesystems.ftp.ConnectionMode
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class FtpConfigurationSpec extends FlatSpec with Matchers {

  behavior of "FtpConfigurationSpec"

  it should "accept valid client configuration" in {

    val confMap = Map(
      "localAddr" -> "127.0.17.5",
      "localPort" -> 6000,
      "soTimeout" -> "1 second",
      "sendBufferSize" -> 10,
      "receiveBufferSize" -> 20,
      "tcpNoDelay" -> true,
      "keepAlive" -> true,
      "soLinger.on" -> true,
      "soLinger.val" -> 30,
      "connectTimeout" -> "2 seconds",
      "charset" -> "UTF-8",
      "controlEncoding" -> "UTF-8",
      "strictMultilineParsing" -> true,
      "dataTimeout" -> "3 seconds",
      "remoteVerificationEnabled" -> false,
      "connectionMode" -> "PASSIVE",
      "activePortRange.min" -> 7000,
      "activePortRange.max" -> 8000,
      "activeExternalIPAddress" -> "250.60.0.15",
      "passiveLocalIPAddress" -> "127.0.12.6",
      "reportActiveExternalIPAddress" -> "127.0.12.30",
      "bufferSize" -> 40,
      "sendDataSocketBufferSize" -> 50,
      "receiveDataSocketBufferSize" -> 60,
      "useEPSVwithIPv4" -> true,
      "controlKeepAliveTimeout" -> "4 seconds",
      "controlKeepAliveReplyTimeout" -> "5 seconds",
      "passiveNatWorkaround" -> false,
      "autodetectEncoding" -> true,
      "clientConnectionCount" -> 10,
      "supportAbsoluteFilePaths" -> false,
      "calculateActualTotalSpace" -> true
    )
    
    val clientMap = Map(
      "client" -> confMap.asJava
    )

    val ftpConfiguration = FtpConfiguration(ConfigFactory.parseMap(clientMap.asJava))
    
    val expectationsOverride = Map(
      "localAddr" -> InetAddress.getByName("127.0.17.5"),
      "soTimeout" -> 1000,
      "connectTimeout" -> 2000,
      "dataTimeout" -> 3000,
      "charset" -> Charset.forName("UTF-8"),
      "connectionMode" -> ConnectionMode.PASSIVE,
      "controlKeepAliveTimeout" -> 4000,
      "controlKeepAliveReplyTimeout" -> 5000
    )

    confMap foreach {
      case (k, v) => assert(ftpConfiguration.fTPEnvironment.get(k) == expectationsOverride.getOrElse(k, v), s"for key $k")
    }
  }
  
  it should "parse anonymous credentials" in {
    FtpConfiguration(ConfigFactory.empty()).ftpCredentials shouldBe FtpAnonymousCredentials
  }

  it should "parse authenticated credentials" in {
    FtpConfiguration(ConfigFactory.parseString(
      """
        |auth {
        |  username = "me"
        |  password = "mot de passe"
        |}
      """.stripMargin)).ftpCredentials shouldBe FtpAuthenticatedCredentials("me", "mot de passe")
  }

}
