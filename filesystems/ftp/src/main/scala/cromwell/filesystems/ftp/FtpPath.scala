package cromwell.filesystems.ftp

import java.net.URI
import java.nio.file.FileAlreadyExistsException

import com.github.robtimus.filesystems.ftp.FTPFileSystemException
import cromwell.core.path.BetterFileMethods.Attributes
import cromwell.core.path.{NioPath, Path}

case class FtpPath(ftpPath: java.nio.file.Path) extends Path {
  override protected def nioPath = ftpPath
  override protected def newPath(nioPath: NioPath) = FtpPath(nioPath)
  override def pathAsString = {
    val uri = ftpPath.toUri
    new URI(uri.getScheme, null, uri.getHost, uri.getPort, uri.getPath, uri.getQuery, uri.getFragment).toString
  }

  override def pathWithoutScheme = nioPath.toUri.getPath

  override def name = ftpPath.getFileName.toUri.getPath.stripPrefix("/")

  override def createDirectories()(implicit attributes: Attributes = Attributes.default): this.type = try {
    super.createDirectories()
  } catch {
    case ftp: FTPFileSystemException if ftp.getReplyString.contains("File exists") =>
      this
  }

  override def createPermissionedDirectories(): this.type = {
    if (!exists) {
      try {
        createDirectories()
      }
      catch {
        // Race condition that's particularly likely with scatters.  Ignore.
        case _: FileAlreadyExistsException =>
        // The GCS filesystem does not support setting permissions and will throw an `UnsupportedOperationException`.
        // Evaluating expressions like `write_lines` in a command block will cause the above permission-manipulating
        // code to run against a GCS Path. Fortunately creating directories in GCS is also unnecessary, so this
        // exception type is just ignored.
        case _: UnsupportedOperationException =>
      }
    }
    this
  }
}
