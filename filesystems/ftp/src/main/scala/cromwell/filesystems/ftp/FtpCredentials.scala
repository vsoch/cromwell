package cromwell.filesystems.ftp

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
