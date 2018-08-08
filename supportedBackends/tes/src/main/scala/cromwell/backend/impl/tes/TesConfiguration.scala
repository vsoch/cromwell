package cromwell.backend.impl.tes

import cromwell.backend.BackendConfigurationDescriptor
import cromwell.backend.standard.GlobLinkMethod._
import net.ceedubs.ficus.Ficus._

class TesConfiguration(val configurationDescriptor: BackendConfigurationDescriptor) {
  val endpointURL = configurationDescriptor.backendConfig.getString("endpoint")
  val runtimeConfig = configurationDescriptor.backendRuntimeConfig
  val globLinkMethod = configurationDescriptor.backendConfig.getAs[GlobLinkMethod]("glob-link-method")
}
