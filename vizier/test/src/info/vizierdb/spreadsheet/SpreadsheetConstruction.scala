package info.vizierdb.spreadsheet


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

import scalikejdbc.DB
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types._
import info.vizierdb.spark.vizual._
import info.vizierdb.commands._
import scala.concurrent.duration._


import play.api.libs.functional.syntax._

import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor.dagFormat
import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor.dagWrites
import info.vizierdb.spark.spreadsheet.SpreadsheetConstructor
import info.vizierdb.spark.spreadsheet.SpreadsheetOnSpark
import scala.collection.mutable
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.IntegerType
import info.vizierdb.Vizier
import scala.collection.compat.immutable.LazyList
import info.vizierdb.catalog.Artifact

import info.vizierdb.commands.ExecutionContext
//import info.vizierdb.types._


class SpreadsheetConstruction
    extends Specification
    with BeforeAll
{
    def beforeAll = SharedTestResources.init
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val A = ColumnRef(1, "A")
    val B = ColumnRef(2, "B")
    val C = ColumnRef(3, "C")
    val D = ColumnRef(4, "D")
    
    "Test everything" >> {
     //"Insert a single column" >> 
     {
        lazy val project = MutableProject("Spreadsheet serialization test")
        project.load("test_data/r.csv", "A")
        val preSerialization = Spreadsheet(project.dataframe("A"))
        preSerialization.insertColumn("D", None)
        val spreadsheetConstructor = SpreadsheetConstructor(Some(project.projectId), preSerialization.overlay.dag, preSerialization.overlay.frame, preSerialization.schema)
        val jsonConstructor = Json.toJson(spreadsheetConstructor)
        val readableConstructor = Json.prettyPrint(jsonConstructor)
        //println(readableConstructor)
        val constructorFromJson: JsResult[SpreadsheetConstructor] = jsonConstructor.validate[SpreadsheetConstructor]
        var cDeserialized: SpreadsheetConstructor = null
        var postSerialization: Spreadsheet = null
        constructorFromJson match {
            case JsSuccess(s, _) => cDeserialized = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        println("After inserting column D:")
        SpreadsheetOnSpark(project.dataframe("A"), cDeserialized.dag, cDeserialized.frame, cDeserialized.schema).show()
        ok
     }

     //"Delete a single row"
     {
        lazy val project = MutableProject("Spreadsheet serialization test")
        project.load("test_data/r.csv", "B")
        val preSerialization = Spreadsheet(project.dataframe("B"))
        preSerialization.deleteRows(2, 1)
        val spreadsheetConstructor = SpreadsheetConstructor(Some(project.projectId), preSerialization.overlay.dag, preSerialization.overlay.frame, preSerialization.schema)
        val jsonConstructor = Json.toJson(spreadsheetConstructor)
        val readableConstructor = Json.prettyPrint(jsonConstructor)
        //println(readableConstructor)
        val constructorFromJson: JsResult[SpreadsheetConstructor] = jsonConstructor.validate[SpreadsheetConstructor]
        var cDeserialized: SpreadsheetConstructor = null
        var postSerialization: Spreadsheet = null
        constructorFromJson match {
            case JsSuccess(s, _) => cDeserialized = s
            case e: JsError         => println(s"Errors: ${JsError.toJson(e)}")
        }
        SpreadsheetOnSpark(project.dataframe("B"), cDeserialized.dag, cDeserialized.frame, cDeserialized.schema).show()
        ok
     }





    }
}