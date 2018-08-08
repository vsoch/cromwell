package cromwell.filesystems.ftp

import common.validation.ErrorOr.ErrorOr
import common.validation.Validation._
import cromwell.core.WorkflowOptions
import cats.syntax.validated._

object FtpCredentials {
  def apply(options: WorkflowOptions): Option[FtpAuthenticatedCredentials] = {
    val credentials: ErrorOr[Option[FtpAuthenticatedCredentials]] = {
      (options.get("ftp-username").toOption, options.get("ftp-password").toOption) match {
        case (Some(username), Some(password)) => Option(FtpAuthenticatedCredentials(username, password)).validNel
        case (None, None) => None.validNel
        case _ => "Supply both ftp username and password, or none of them to default back to Cromwell's FTP configuration".invalidNel
      }
    }

    credentials.unsafe("FTP credentials configuration is not valid")
  }
}

sealed trait FtpCredentials {
  def user: Option[String]
}
// Yes, FTP uses plain text username / password
case class FtpAuthenticatedCredentials(username: String, password: String) extends FtpCredentials {
  override def user = Option(username)
}
case object FtpAnonymousCredentials extends FtpCredentials {
  override def user = None
}
