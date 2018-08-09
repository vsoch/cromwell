//package cromwell.filesystems.ftp
//
//import com.github.robtimus.filesystems.ftp.TestFTPEnvironment
//import com.typesafe.config.ConfigFactory
//import cromwell.core.WorkflowOptions
//import cromwell.core.path.{BadPath, GoodPath, PathBuilderSpecUtils}
//import org.scalatest.prop.Tables.Table
//import org.scalatest.{FlatSpec, Matchers}
//
//import scala.concurrent.Await
//import scala.concurrent.duration._
//
//class FtpPathSpec extends FlatSpec with Matchers with PathBuilderSpecUtils {
//
//  behavior of "FtpPathSpec"
//  
//  val pathBuilderFactory = new FtpPathBuilderFactory(ConfigFactory.empty(), ConfigFactory.empty()) {
//    override private [ftp] lazy val configFtpConfiguration = new FtpConfiguration(FtpAnonymousCredentials, 1.hour, new TestFTPEnvironment())
//  }
//  
//  val pathBuilder =
//    Await.result(pathBuilderFactory.withOptions(WorkflowOptions.empty)(null, null), 1.second)
//
//  it should behave like truncateCommonRoots(pathBuilder, pathsToTruncate)
//
//  goodPaths foreach { goodPath =>
//    it should behave like buildGoodPath(pathBuilder, goodPath)
//  }
//
//  badPaths foreach { badPath =>
//    it should behave like buildBadPath(pathBuilder, badPath)
//  }
//
//  private def pathsToTruncate = Table(
//    ("context", "file", "relative"),
//    ("ftp://ftp-server.com/root", "ftp://ftp-server.com/root/path/to/file", "path/to/file"),
//    ("ftp://ftp-server.com/root/path/to/my/dir", "ftp://ftp-server.com/root/path/to/my/dir/file", "file"),
//    ("ftp://ftp-server.com/root/path/to/my/dir", "ftp://ftp-server.com/root/path/to/my/dir//file", "file"),
//    // NOTE: Next two are different from the DefaultPathBuilder. "//" doesn't build to "/" in the GcsPathBuilder
//    ("ftp://ftp-server.com/root/path/to/my//dir", "ftp://ftp-server.com/root/path/to/my/dir/file", "dir/file"),
//    ("ftp://ftp-server.com/root/path/to/my//dir", "ftp://ftp-server.com/root/path/to/my/dir//file", "dir//file"),
//    ("ftp://ftp-server.com/root/path/to/my/dir", "ftp://ftp-server.com/root/path/./to/my/dir/file", "./to/my/dir/file"),
//    ("ftp://ftp-server.com/root/path/to/my/dir/with/file", "ftp://ftp-server.com/root/path/to/other/dir/with/file", "other/dir/with/file")
//  )
//
//  private def goodPaths = Seq(
//    GoodPath(
//      description = "a path with spaces",
//      path = s"ftp://ftp-server.com/root/hello/world/with spaces",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/world/with spaces",
//      pathWithoutScheme = s"root/hello/world/with spaces",
//      parent = s"ftp://ftp-server.com/root/hello/world/",
//      getParent = s"ftp://ftp-server.com/root/hello/world/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "with spaces",
//      getFileName = s"ftp://ftp-server.com/root/with spaces",
//      getNameCount = 3,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a path with non-ascii",
//      path = s"ftp://ftp-server.com/root/hello/world/with non ascii £€",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/world/with non ascii £€",
//      pathWithoutScheme = s"root/hello/world/with non ascii £€",
//      parent = s"ftp://ftp-server.com/root/hello/world/",
//      getParent = s"ftp://ftp-server.com/root/hello/world/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "with non ascii £€",
//      getFileName = s"ftp://ftp-server.com/root/with non ascii £€",
//      getNameCount = 3,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a gs uri path with encoded characters",
//      path = s"ftp://ftp-server.com/root/hello/world/encoded%20spaces",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/world/encoded%20spaces",
//      pathWithoutScheme = s"root/hello/world/encoded%20spaces",
//      parent = s"ftp://ftp-server.com/root/hello/world/",
//      getParent = s"ftp://ftp-server.com/root/hello/world/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "encoded%20spaces",
//      getFileName = s"ftp://ftp-server.com/root/encoded%20spaces",
//      getNameCount = 3,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket only path",
//      path = s"ftp://ftp-server.com/root",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/",
//      pathWithoutScheme = s"root/",
//      parent = null,
//      getParent = null,
//      root = s"ftp://ftp-server.com/root/",
//      name = "",
//      getFileName = s"ftp://ftp-server.com/root/",
//      getNameCount = 1,
//      isAbsolute = false),
//
//    GoodPath(
//      description = "a bucket only path ending in a /",
//      path = s"ftp://ftp-server.com/root/",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/",
//      pathWithoutScheme = s"root/",
//      parent = null,
//      getParent = null,
//      root = s"ftp://ftp-server.com/root/",
//      name = "",
//      getFileName = null,
//      getNameCount = 0,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a file at the top of the bucket",
//      path = s"ftp://ftp-server.com/root/hello",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello",
//      pathWithoutScheme = s"root/hello",
//      parent = s"ftp://ftp-server.com/root/",
//      getParent = s"ftp://ftp-server.com/root/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "hello",
//      getFileName = s"ftp://ftp-server.com/root/hello",
//      getNameCount = 1,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a path ending in /",
//      path = s"ftp://ftp-server.com/root/hello/world/",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/world/",
//      pathWithoutScheme = s"root/hello/world/",
//      parent = s"ftp://ftp-server.com/root/hello/",
//      getParent = s"ftp://ftp-server.com/root/hello/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "world",
//      getFileName = s"ftp://ftp-server.com/root/world",
//      getNameCount = 2,
//      isAbsolute = true),
//
//    // Special paths
//
//    GoodPath(
//      description = "a bucket with a path .",
//      path = s"ftp://ftp-server.com/root/.",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/.",
//      pathWithoutScheme = s"root/.",
//      parent = null,
//      getParent = s"ftp://ftp-server.com/root/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "",
//      getFileName = s"ftp://ftp-server.com/root/.",
//      getNameCount = 1,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket with a path ..",
//      path = s"ftp://ftp-server.com/root/..",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/..",
//      pathWithoutScheme = s"root/..",
//      parent = null,
//      getParent = s"ftp://ftp-server.com/root/",
//      root = null,
//      name = "",
//      getFileName = s"ftp://ftp-server.com/root/..",
//      getNameCount = 1,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket including . in the path",
//      path = s"ftp://ftp-server.com/root/hello/./world",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/./world",
//      pathWithoutScheme = s"root/hello/./world",
//      parent = s"ftp://ftp-server.com/root/hello/",
//      getParent = s"ftp://ftp-server.com/root/hello/./",
//      root = s"ftp://ftp-server.com/root/",
//      name = "world",
//      getFileName = s"ftp://ftp-server.com/root/world",
//      getNameCount = 3,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket including .. in the path",
//      path = s"ftp://ftp-server.com/root/hello/../world",
//      normalize = false,
//      pathAsString = s"ftp://ftp-server.com/root/hello/../world",
//      pathWithoutScheme = s"root/hello/../world",
//      parent = s"ftp://ftp-server.com/root/",
//      getParent = s"ftp://ftp-server.com/root/hello/../",
//      root = s"ftp://ftp-server.com/root/",
//      name = "world",
//      getFileName = s"ftp://ftp-server.com/root/world",
//      getNameCount = 3,
//      isAbsolute = true),
//
//    // Normalized
//
//    GoodPath(
//      description = "a bucket with a normalized path .",
//      path = s"ftp://ftp-server.com/root/.",
//      normalize = true,
//      pathAsString = s"ftp://ftp-server.com/root/",
//      pathWithoutScheme = s"root/",
//      parent = null,
//      getParent = null,
//      root = s"ftp://ftp-server.com/root/",
//      name = "",
//      getFileName = null,
//      getNameCount = 0,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket with a normalized path ..",
//      path = s"ftp://ftp-server.com/root/..",
//      normalize = true,
//      pathAsString = s"ftp://ftp-server.com/root/",
//      pathWithoutScheme = s"root/",
//      parent = null,
//      getParent = null,
//      root = s"ftp://ftp-server.com/root/",
//      name = "",
//      getFileName = s"ftp://ftp-server.com/root/",
//      getNameCount = 1,
//      isAbsolute = false),
//
//    GoodPath(
//      description = "a bucket including . in the normalized path",
//      path = s"ftp://ftp-server.com/root/hello/./world",
//      normalize = true,
//      pathAsString = s"ftp://ftp-server.com/root/hello/world",
//      pathWithoutScheme = s"root/hello/world",
//      parent = s"ftp://ftp-server.com/root/hello/",
//      getParent = s"ftp://ftp-server.com/root/hello/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "world",
//      getFileName = s"ftp://ftp-server.com/root/world",
//      getNameCount = 2,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket including .. in the normalized path",
//      path = s"ftp://ftp-server.com/root/hello/../world",
//      normalize = true,
//      pathAsString = s"ftp://ftp-server.com/root/world",
//      pathWithoutScheme = s"root/world",
//      parent = s"ftp://ftp-server.com/root/",
//      getParent = s"ftp://ftp-server.com/root/",
//      root = s"ftp://ftp-server.com/root/",
//      name = "world",
//      getFileName = s"ftp://ftp-server.com/root/world",
//      getNameCount = 1,
//      isAbsolute = true),
//
//    GoodPath(
//      description = "a bucket with an underscore",
//      path = s"gs://hello_underscore/world",
//      normalize = true,
//      pathAsString = s"gs://hello_underscore/world",
//      pathWithoutScheme = s"hello_underscore/world",
//      parent = s"gs://hello_underscore/",
//      getParent = s"gs://hello_underscore/",
//      root = s"gs://hello_underscore/",
//      name = "world",
//      getFileName = s"gs://hello_underscore/world",
//      getNameCount = 1,
//      isAbsolute = true)
//  )
//
//  private def badPaths = Seq(
//    BadPath("an empty path", "", " does not have a gcs scheme"),
//    BadPath("an bucketless path", "gs://", "The specified GCS path 'gs://' does not parse as a URI.\nExpected authority at index 5: gs://"),
//    BadPath("a bucket named .", "gs://./hello/world", "The path 'gs://./hello/world' does not seem to be a valid GCS path. Please check that it starts with gs:// and that the bucket and object follow GCS naming guidelines at https://cloud.google.com/storage/docs/naming."),
//    BadPath("a non ascii bucket name", "gs://nonasciibucket£€/hello/world",
//      "The path 'gs://nonasciibucket£€/hello/world' does not seem to be a valid GCS path. Please check that it starts with gs:// and that the bucket and object follow GCS naming guidelines at https://cloud.google.com/storage/docs/naming."),
//    BadPath("a https path", "https://hello/world", "Cloud Storage URIs must have 'gs' scheme: https://hello/world"),
//    BadPath("a file uri path", "file:///hello/world", "Cloud Storage URIs must have 'gs' scheme: file:///hello/world"),
//    BadPath("a relative file path", "hello/world", "hello/world does not have a gcs scheme"),
//    BadPath("an absolute file path", "/hello/world", "/hello/world does not have a gcs scheme")
//  )
//}
