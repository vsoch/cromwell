package cromwell.backend.google.pipelines.v1alpha2

import java.net.URL

import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.services.genomics.Genomics
import com.google.api.services.genomics.model._
import cromwell.backend.google.pipelines.common.api.PipelinesApiRequestFactory.CreatePipelineParameters
import cromwell.backend.google.pipelines.common.api.{PipelinesApiFactoryInterface, PipelinesApiRequestFactory}
import cromwell.backend.google.pipelines.v1alpha2.PipelinesConversions._
import cromwell.backend.google.pipelines.v1alpha2.api.{NonPreemptibleJesPipelineInfoBuilder, PreemptibleJesPipelineInfoBuilder}
import cromwell.backend.standard.StandardAsyncJob
import cromwell.cloudsupport.gcp.auth.GoogleAuthMode

import scala.collection.JavaConverters._

case class GenomicsFactory(applicationName: String, authMode: GoogleAuthMode, endpointUrl: URL) extends PipelinesApiFactoryInterface {
  def build(initializer: HttpRequestInitializer): PipelinesApiRequestFactory = {
    new PipelinesApiRequestFactory {
      private val genomics = new Genomics.Builder(
        GoogleAuthMode.httpTransport,
        GoogleAuthMode.jsonFactory,
        initializer)
        .setApplicationName(applicationName)
        .setRootUrl(endpointUrl.toString)
        .build

      override def runRequest(createPipelineParameters: CreatePipelineParameters) = {
        lazy val workflow = createPipelineParameters.jobDescriptor.workflowDescriptor
        val pipelineInfoBuilder = if (createPipelineParameters.preemptible) PreemptibleJesPipelineInfoBuilder else NonPreemptibleJesPipelineInfoBuilder
        val pipelineInfo = pipelineInfoBuilder.build(createPipelineParameters.commandLine, createPipelineParameters.runtimeAttributes, createPipelineParameters.dockerImage)

        val inputParameters = createPipelineParameters.inputParameters.map({ i => i.name -> i }).toMap
        val outputParameters = createPipelineParameters.outputParameters.map({ o => o.name -> o }).toMap

        val pipeline = new Pipeline()
          .setProjectId(createPipelineParameters.projectId)
          .setDocker(pipelineInfo.docker)
          .setResources(pipelineInfo.resources)
          .setName(workflow.callable.name)
          .setInputParameters(inputParameters.values.map(_.toGooglePipelineParameter).toVector.asJava)
          .setOutputParameters(outputParameters.values.map(_.toGooglePipelineParameter).toVector.asJava)

        // disks cannot have mount points at runtime, so set them null
        val runtimePipelineResources = {
          val resources = pipelineInfoBuilder.build(createPipelineParameters.commandLine, createPipelineParameters.runtimeAttributes, createPipelineParameters.dockerImage).resources
          val disksWithoutMountPoint = resources.getDisks.asScala map {
            _.setMountPoint(null)
          }
          resources.setDisks(disksWithoutMountPoint.asJava)
        }

        val svcAccount = new ServiceAccount().setEmail(createPipelineParameters.computeServiceAccount).setScopes(PipelinesApiFactoryInterface.GenomicsScopes)
        val rpargs = new RunPipelineArgs().setProjectId(createPipelineParameters.projectId).setServiceAccount(svcAccount).setResources(runtimePipelineResources)

        rpargs.setInputs(inputParameters.mapValues(_.toGoogleRunParameter).asJava)
        rpargs.setOutputs(outputParameters.mapValues(_.toGoogleRunParameter).asJava)

        rpargs.setLabels(createPipelineParameters.labels.asJavaMap)

        val rpr = new RunPipelineRequest().setEphemeralPipeline(pipeline).setPipelineArgs(rpargs)

        val logging = new LoggingOptions()
        logging.setGcsPath(createPipelineParameters.logGcsPath)
        rpargs.setLogging(logging)

        genomics.pipelines().run(rpr).buildHttpRequest()
      }

      override def getRequest(job: StandardAsyncJob) = genomics.operations().get(job.jobId).buildHttpRequest()

      override def cancelRequest(job: StandardAsyncJob): HttpRequest = {
        val cancellationRequest: CancelOperationRequest = new CancelOperationRequest()
        genomics.operations().cancel(job.jobId, cancellationRequest).buildHttpRequest()
      }
    }
  }
}