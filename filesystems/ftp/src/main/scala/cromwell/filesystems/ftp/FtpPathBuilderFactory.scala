package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileSystem
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.github.robtimus.filesystems.ftp.{ConnectionMode, FTPEnvironment, FTPFileSystemProvider}
import com.google.common.cache._
import com.typesafe.config.Config
import cromwell.core.WorkflowOptions
import cromwell.core.path.PathBuilderFactory
import cromwell.filesystems.ftp.FtpPathBuilderFactory.FileSystemKey

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FtpPathBuilderFactory {
  case class FileSystemKey(host: String, configuration: FtpConfiguration)
}

class FtpPathBuilderFactory(globalConfig: Config, instanceConfig: Config) extends PathBuilderFactory {
  private val configFtpConfiguration = FtpConfiguration(instanceConfig)
  private val ftpFileSystemProvider = new FTPFileSystemProvider()
  private val defaultEnvironment = new FTPEnvironment()
    .withConnectionMode(ConnectionMode.PASSIVE)
    .withClientConnectionCount(3)

  private val cacheCleanUpInterval = 1.hour
  private val scheduler = Executors.newScheduledThreadPool(1)
  private val cleanUpCache = new Runnable {
    override def run() = {
      Try(ftpCache.cleanUp())
      ()
    }
  }
  private [ftp] val cacheLoader = new CacheLoader[FileSystemKey, FileSystem] {
    override def load(key: FileSystemKey) = {
      val environment = makeEnvironment(key.configuration)
      val uri = new URI(ftpFileSystemProvider.getScheme, null, key.host, -1, null, null, null)
      makeFilesystem(uri, environment)
    }
  }

  private [ftp] val ftpCacheBuilder: CacheBuilder[FileSystemKey, FileSystem] = CacheBuilder.newBuilder()
    .expireAfterAccess(configFtpConfiguration.cacheTTL.length, configFtpConfiguration.cacheTTL.unit)
    .removalListener((notification: RemovalNotification[FileSystemKey, FileSystem]) => {
      // Do this synchronously, expecting it to be quick, but we might want to asynchronify it since it involves
      // closing opened TCP connections
      notification.getValue.close()
    })

  private val ftpCache: LoadingCache[FileSystemKey, FileSystem] = ftpCacheBuilder.build[FileSystemKey, FileSystem](cacheLoader)
  initPeriodicCleanup()

  private def makeEnvironment(configuration: FtpConfiguration) = configuration.ftpCredentials match {
    case FtpAnonymousCredentials => defaultEnvironment
    case FtpAuthenticatedCredentials(username, password) => defaultEnvironment.withCredentials(username, password.toCharArray)
  }

  private [ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = ftpFileSystemProvider.newFileSystem(uri, environment)

  override def withOptions(options: WorkflowOptions)(implicit as: ActorSystem, ec: ExecutionContext) = Future {
    val ftpConfig = FtpConfiguration(options).getOrElse(configFtpConfiguration)
    FtpPathBuilder(ftpFileSystemProvider, ftpCache, ftpConfig)
  }
  
  private [ftp] def initPeriodicCleanup() = {
    scheduler.scheduleAtFixedRate(cleanUpCache, cacheCleanUpInterval.length, cacheCleanUpInterval.length, cacheCleanUpInterval.unit)
    ()
  }
}
