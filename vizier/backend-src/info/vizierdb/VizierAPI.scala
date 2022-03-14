/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb

import java.io.File
import play.api.libs.json._
import info.vizierdb.types._
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.{ Resource, PathResource }
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.server.{ Request => JettyRequest }
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response._
import info.vizierdb.api.handler._
import info.vizierdb.api.servlet.{ VizierAPIServlet, VizierUIServlet }

import info.vizierdb.api.{ Request, Response }
import java.net.{ URL, URI }
import java.sql.Time
import java.time.ZonedDateTime
import info.vizierdb.api._
import info.vizierdb.api.websocket.BranchWatcherSocket
import java.net.URLConnection
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import info.vizierdb.util.Streams
import scala.io.Source
import java.net.InetSocketAddress
import info.vizierdb.util.ExperimentalOptions
import info.vizierdb.spark.caveats.QueryWithCaveats
import info.vizierdb.api.spreadsheet.SpreadsheetSocket


object VizierAPI
{
  var server: Server = null

  val DEFAULT_PORT = 5000
  val NAME = "vizier"
  val BACKEND = "SCALA"
  val SERVICE_NAME = s"Vizier ($BACKEND)"
  val MAX_UPLOAD_SIZE = 1024*1024*100 // 100MB
  val MAX_FILE_MEMORY = 1024*1024*10  // 10MB
  val MAX_DOWNLOAD_ROW_LIMIT = QueryWithCaveats.RESULT_THRESHOLD
  val VERSION="1.0.0"
  val DEFAULT_DISPLAY_ROWS = 20


  var urls: VizierURLs = null
  var started: ZonedDateTime = null

  lazy val WEB_UI_URL = getClass().getClassLoader().getResource("ui")

  /**
   * 
   */
  def init(
    publicURL: String = null, 
    port: Int = DEFAULT_PORT, 
    path: File = Vizier.config.basePath(),
    bindToLocalhost: Boolean = 
      !(Vizier.config.devel() || Vizier.config.connectFromAnyHost())
  )
  {
    if(server != null){ 
      throw new RuntimeException("Can't have two Vizier servers running in one JVM")
    }
    server = 
      if(bindToLocalhost){
        new Server(InetSocketAddress.createUnresolved(
          "localhost",
          port
        ))
      } else {
        new Server(port)
      }

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)
    context.setBaseResource(Resource.newResource(new File(".")))

    /////////////// Websocket API ///////////////
    {
      val websocket = new ServletHolder(BranchWatcherSocket.Servlet)
      context.addServlet(websocket, "/vizier-db/api/v1/websocket")
    }
    /////////////// Spreadsheet Websocket API ///////////////
    {
      val websocket = new ServletHolder(SpreadsheetSocket.Servlet)
      context.addServlet(websocket, "/vizier-db/api/v1/spreadsheet")
    }

    /////////////// Transactional API ///////////////
    {
      val api = new ServletHolder(VizierAPIServlet)
      api.getRegistration()
         .setMultipartConfig(new MultipartConfigElement(
           /* location          = */ (new File(path, "temp")).toString,
           /* maxFileSize       = */ MAX_UPLOAD_SIZE.toLong,
           /* maxRequestSize    = */ MAX_UPLOAD_SIZE.toLong,
           /* fileSizeThreshold = */ MAX_FILE_MEMORY
         ))
      context.addServlet(api, "/vizier-db/api/v1/*")
    }

    /////////////// Static UI Pages ///////////////
    {
      val webUI = new ServletHolder("default", VizierUIServlet)
      context.addServlet(webUI, "/*")
    }


    val actualPublicURL =
      Option(publicURL)
        .orElse { Vizier.config.publicURL.get }
        .getOrElse { s"http://localhost:$port/" }

    urls = new VizierURLs(
      ui = new URL(actualPublicURL),
      base = new URL(s"${actualPublicURL}vizier-db/api/v1/"),
      api = None
    )
    server.start()
    // server.dump(System.err)
    started = ZonedDateTime.now()
  }
}

