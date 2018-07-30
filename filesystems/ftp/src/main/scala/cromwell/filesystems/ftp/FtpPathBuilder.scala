package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.spi.FileSystemProvider

import com.google.common.cache.LoadingCache
import cromwell.core.path.PathBuilder
import cromwell.filesystems.ftp.FtpPathBuilderFactory.FileSystemKey
import org.slf4j.LoggerFactory

import scala.util.{Failure, Try}

object FtpPathBuilder {
  val logger = LoggerFactory.getLogger("FTPLogger")
}

case class FtpPathBuilder(fileSystemProvider: FileSystemProvider, fileSystemCache: LoadingCache[FileSystemKey, FileSystem], ftpConfiguration: FtpConfiguration) extends PathBuilder {
  override def name = "FTP"

  override def build(pathAsString: String) = {
    val uri = URI.create(pathAsString)
    if (Option(uri.getScheme).exists(_.equalsIgnoreCase(fileSystemProvider.getScheme)))
      Try {
        FtpPath(getFileSystem(uri).getPath(uri.getPath))
      }
    else
      Failure(new IllegalArgumentException(s"Invalid ftp path: $pathAsString"))
  }

  private def getFileSystem(uri: URI): FileSystem = fileSystemCache.get(FileSystemKey(uri.getHost, ftpConfiguration))
}
