package com.github.robtimus.filesystems.ftp

import org.apache.commons.net.ftp.{FTPClient, FTPFile, FTPFileFilter}

class TestFTPEnvironment extends FTPEnvironment {
  override def createClient(hostname: String, port: Int) = new FTPClient() {
    override def printWorkingDirectory = "hello"
    override def listFiles(pathname: String, filter: FTPFileFilter) = Array.empty[FTPFile]
  }
}
