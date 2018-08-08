package cromwell.backend.standard

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

object GlobLinkMethod extends Enumeration {
  type GlobLinkMethod = Value
  val Hard, Soft = Value
  
  implicit val configReader = new ValueReader[Option[GlobLinkMethod]] {
    override def read(config: Config, path: String) = if (config.hasPath(path)) {
      Option(GlobLinkMethod.withName(config.getString(path).capitalize))
    } else None
  }
}
