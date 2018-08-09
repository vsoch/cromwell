package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileSystem
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.github.robtimus.filesystems.ftp.{FTPEnvironment, FTPFileSystemProvider}
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
  private [ftp] lazy val configFtpConfiguration = FtpConfiguration(instanceConfig)
  private lazy val ftpFileSystemProvider = new FTPFileSystemProvider()

  private [ftp] lazy val cacheCleanUpInterval = 1.hour
  private [ftp] lazy val scheduler = Executors.newScheduledThreadPool(1)
  private [ftp] lazy val cleanUpCache = new Runnable {
    override def run() = {
      Try(ftpCache.cleanUp())
      ()
    }
  }
  private [ftp] lazy val cacheLoader = new CacheLoader[FileSystemKey, FileSystem] {
    override def load(key: FileSystemKey) = {
      val uri = new URI(ftpFileSystemProvider.getScheme, null, key.host, -1, null, null, null)
      makeFilesystem(uri, key.configuration.fTPEnvironment)
    }
  }

  private [ftp] def makeCacheBuilder: CacheBuilder[FileSystemKey, FileSystem] = CacheBuilder.newBuilder()
    .expireAfterAccess(configFtpConfiguration.cacheTTL.length, configFtpConfiguration.cacheTTL.unit)
    .removalListener((notification: RemovalNotification[FileSystemKey, FileSystem]) => {
      // Do this synchronously, expecting it to be quick, but we might want to asynchronify it since it involves
      // closing opened TCP connections
      notification.getValue.close()
    })

  private [ftp] lazy val ftpCacheBuilder: CacheBuilder[FileSystemKey, FileSystem] = makeCacheBuilder

  private [ftp] lazy val ftpCache: LoadingCache[FileSystemKey, FileSystem] = ftpCacheBuilder.build[FileSystemKey, FileSystem](cacheLoader)

  private [ftp] var cleanUpCancellable = initPeriodicCleanup()

  private [ftp] def makeFilesystem(uri: URI, environment: FTPEnvironment) = ftpFileSystemProvider.newFileSystem(uri, environment)

  override def withOptions(options: WorkflowOptions)(implicit as: ActorSystem, ec: ExecutionContext) = Future.successful {
    val ftpConfig = FtpCredentials(options).map(configFtpConfiguration.authenticated).getOrElse(configFtpConfiguration)
    FtpPathBuilder(ftpFileSystemProvider, ftpCache, ftpConfig)
  }

  private [ftp] def initPeriodicCleanup() = {
    scheduler.scheduleAtFixedRate(cleanUpCache, cacheCleanUpInterval.length, cacheCleanUpInterval.length, cacheCleanUpInterval.unit)
  }
}
