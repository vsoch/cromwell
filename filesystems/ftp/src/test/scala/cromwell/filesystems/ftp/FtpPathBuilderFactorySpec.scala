package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileSystem

import com.github.robtimus.filesystems.ftp.FTPEnvironment
import com.google.common.base.Ticker
import com.typesafe.config.{Config, ConfigFactory}
import cromwell.core.WorkflowOptions
import cromwell.filesystems.ftp.FtpPathBuilderFactory.FileSystemKey
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.specs2.mock.Mockito

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class FtpPathBuilderFactorySpec extends FlatSpec with Matchers with Mockito with MockFactory {
  behavior of "FtpPathBuilderFactory"

  val emptyConfig = ConfigFactory.empty()
  val cacheTTL = 5.seconds
  // A ticker that allows to manually expire values
  val testTicker = new TestTicker
  
  def makeTestFactory(config: Config) = new FtpPathBuilderFactory(config, config) {
    override private[ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = new TestFileSystem()
    override private [ftp] def initPeriodicCleanup() = null
  }

  it should "cache filesystems until they're unreachable" in {
    var fileSystems = List.empty[TestFileSystem]

    val factory = new FtpPathBuilderFactory(emptyConfig, emptyConfig) {
      override private[ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = {
        val fs = new TestFileSystem()
        fileSystems = fileSystems :+ fs
        fs
      }
      override private [ftp] def initPeriodicCleanup() = null
      // Introduce a custom ticker to be able to manually expire entries from the cache
      override private [ftp] lazy val ftpCacheBuilder = makeCacheBuilder.ticker(testTicker)
    }
    val cache = factory.ftpCache

    val anonymousKey = FileSystemKey("ftp.myserver.com", FtpConfiguration(FtpAnonymousCredentials, cacheTTL))
    val credentialedKey = FileSystemKey("ftp.myserver.com", FtpConfiguration(FtpAuthenticatedCredentials("c'est moi", "mot de passe"), cacheTTL))
    val otherHost = FileSystemKey("ftp.myotherserver.com", FtpConfiguration(FtpAnonymousCredentials, cacheTTL))

    // Retrieve from the cache multiple times. This indirectly test that they are indeed cached because an FTPFilesystemProvider
    // does not allow creation of an already existing filesystem and would throw if that happend
    (0 to 5) foreach { _ =>
      cache.get(anonymousKey)
      cache.get(credentialedKey)
      cache.get(otherHost)
    }

    // Force a cache cleanup
    factory.cleanUpCache.run()

    // They should still be there
    cache.getIfPresent(anonymousKey) should not be null
    cache.getIfPresent(credentialedKey) should not be null
    cache.getIfPresent(otherHost) should not be null

    testTicker.setExpired(true)

    factory.cleanUpCache.run()

    // They should have been evicted now
    cache.getIfPresent(anonymousKey) shouldBe null
    cache.getIfPresent(credentialedKey) shouldBe null
    cache.getIfPresent(otherHost) shouldBe null

    // And filesystems should be closed
    fileSystems.foreach(_.isOpen shouldBe false)
  }
  
  it should "schedule a periodic cache cleanup" in {
    var cleanedUp = false

    val factory = new FtpPathBuilderFactory(emptyConfig, emptyConfig) {
      override private [ftp] lazy val cacheCleanUpInterval = 5.seconds
      override private [ftp] lazy val cleanUpCache = new Runnable {
        override def run() = cleanedUp = true
      }
      override private[ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = new TestFileSystem()
    }
    
    Thread.sleep(6.seconds.toMillis)
    cleanedUp shouldBe true
    Try {
      factory.cleanUpCancellable.cancel(true)
      factory.scheduler.shutdown()
    }
  }
  
  it should "not change default credentials if there is no workflow option override" in {
    val anonymousFactory = makeTestFactory(emptyConfig)
    val anonymousPathBuilder = Await.result(anonymousFactory.withOptions(WorkflowOptions.empty)(null, null), 1.second)
    anonymousPathBuilder.ftpConfiguration.ftpCredentials shouldBe FtpAnonymousCredentials

    val authenticatedFactory = makeTestFactory(ConfigFactory.parseString(
      """
        |auth {
        |  username: "user"
        |  password: "password"
        |}
      """.stripMargin))

    val authenticatedPathBuilder = Await.result(authenticatedFactory.withOptions(WorkflowOptions.empty)(null, null), 1.second)
    authenticatedPathBuilder.ftpConfiguration.ftpCredentials shouldBe FtpAuthenticatedCredentials("user", "password")
  }

  it should "change default credentials if there is a workflow option override" in {
    val anonymousFactory = makeTestFactory(emptyConfig)
    val workflowOptions = WorkflowOptions.fromMap(Map("ftp-username" -> "user", "ftp-password" -> "password")).get
    val anonymousPathBuilder = Await.result(anonymousFactory.withOptions(workflowOptions)(null, null), 1.second)
    
    anonymousPathBuilder.ftpConfiguration.ftpCredentials shouldBe FtpAuthenticatedCredentials("user", "password")

    // The default factory config should NOT have changed
    anonymousFactory.configFtpConfiguration.fTPEnvironment.get("username") shouldBe null
    anonymousFactory.configFtpConfiguration.fTPEnvironment.get("password") shouldBe null

    // But the path builder one should have the updated credentials
    anonymousPathBuilder.ftpConfiguration.fTPEnvironment.get("username") shouldBe "user"
    anonymousPathBuilder.ftpConfiguration.fTPEnvironment.get("password") shouldBe "password".toCharArray

    val authenticatedFactory = makeTestFactory(ConfigFactory.parseString(
      """
        |auth {
        |  username: "user"
        |  password: "password"
        |}
      """.stripMargin))

    val otherWorkflowOptions = WorkflowOptions.fromMap(Map("ftp-username" -> "other user", "ftp-password" -> "other password")).get
    val authenticatedPathBuilder = Await.result(authenticatedFactory.withOptions(otherWorkflowOptions)(null, null), 1.second)
    
    authenticatedPathBuilder.ftpConfiguration.ftpCredentials shouldBe FtpAuthenticatedCredentials("other user", "other password")
    
    // The default factory config should NOT have changed
    authenticatedFactory.configFtpConfiguration.fTPEnvironment.get("username") shouldBe "user"
    authenticatedFactory.configFtpConfiguration.fTPEnvironment.get("password") shouldBe "password".toCharArray
    
    // But the path builder one should have the updated credentials
    authenticatedPathBuilder.ftpConfiguration.fTPEnvironment.get("username") shouldBe "other user"
    authenticatedPathBuilder.ftpConfiguration.fTPEnvironment.get("password") shouldBe "other password".toCharArray
  }

  private class TestFileSystem() extends FileSystem {
    private var open: Boolean = true
    override def provider() = ???
    override def close() = open = false
    override def isOpen = open
    override def isReadOnly = ???
    override def getSeparator = ???
    override def getRootDirectories = ???
    override def getFileStores = ???
    override def supportedFileAttributeViews() = ???
    override def getPath(first: String, more: String*) = ???
    override def getPathMatcher(syntaxAndPattern: String) = ???
    override def getUserPrincipalLookupService = ???
    override def newWatchService() = ???
  }

  class TestTicker extends Ticker {
    private var _isExpired: Boolean = false
    override def read() = if (_isExpired) Long.MaxValue else cacheTTL.toNanos
    def setExpired(boolean: Boolean) = _isExpired = boolean
  }
}
