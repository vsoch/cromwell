package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileSystem

import com.github.robtimus.filesystems.ftp.FTPEnvironment
import com.google.common.base.Ticker
import com.typesafe.config.ConfigFactory
import cromwell.filesystems.ftp.FtpPathBuilderFactory.FileSystemKey
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.specs2.mock.Mockito

import scala.concurrent.duration._

class FtpPathBuilderFactorySpec extends FlatSpec with Matchers with Mockito with MockFactory {
  behavior of "FtpPathBuilderFactory"

  val config = ConfigFactory.empty()
  val cacheTTL = 5.seconds
  // A ticker that allows to manually expire values
  val testTicker = new TestTicker

  it should "cache filesystems until they're unreachable" in {
    var fileSystems = List.empty[TestFileSystem]

    val factory = new FtpPathBuilderFactory(config, config) {
      override private[ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = {
        val fs = new TestFileSystem()
        fileSystems = fileSystems :+ fs
        fs
      }
      override private [ftp] def initPeriodicCleanup() = ()
    }
    val cache = factory.ftpCacheBuilder
      .ticker(testTicker)
      .build(factory.cacheLoader)

    // The unique
    val anonymousKey = FileSystemKey("ftp.myserver.com", FtpConfiguration(FtpAnonymousCredentials, cacheTTL))
    val credentialedKey = FileSystemKey("ftp.myserver.com", FtpConfiguration(FtpAuthenticatedCredentials("c'est moi", "mot de passe"), cacheTTL))
    val otherHost = FileSystemKey("ftp.myotherserver.com", FtpConfiguration(FtpAnonymousCredentials, cacheTTL))

    // Retrieve from the cache multiple times. This indirectly test that they are indeed cached because an FTPFilesystemProvider
    // does not allow creation of an already existing filesystem
    (0 to 5) foreach { _ =>
      cache.get(anonymousKey)
      cache.get(credentialedKey)
      cache.get(otherHost)
    }

    // Force a cache cleanup
    cache.cleanUp()

    // They should still be there
    cache.getIfPresent(anonymousKey) should not be null
    cache.getIfPresent(credentialedKey) should not be null
    cache.getIfPresent(otherHost) should not be null

    testTicker.setExpired(true)

    cache.cleanUp()

    // They should have been evicted now
    cache.getIfPresent(anonymousKey) shouldBe null
    cache.getIfPresent(credentialedKey) shouldBe null
    cache.getIfPresent(otherHost) shouldBe null

    // And filesystems should be closed
    fileSystems.foreach(_.isOpen shouldBe false)
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
