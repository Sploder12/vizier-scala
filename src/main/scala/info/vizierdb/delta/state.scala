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
package info.vizierdb.delta

import scalikejdbc.DBSession
import info.vizierdb.types._
import info.vizierdb.catalog.serialized.ModuleDescription

case class CellState(
  moduleId: String,
  resultId: Option[String],
  state: ExecutionState.T, 
  messageCount: Int
)
object CellState
{
  def apply(description: ModuleDescription): CellState =
    CellState(
      description.moduleId.toString,
      description.resultId,
      description.statev2,
      description.outputs.stdout.size + description.outputs.stderr.size
    )
}

case class WorkflowState(
  branchId: Identifier,
  workflowId: Identifier,
  cells: Seq[CellState],
)
{
  def applyDelta(delta: WorkflowDelta): WorkflowState = 
    delta match {
      case InsertCell(cell, position) => copy(cells = 
        cells.patch(position, Seq(CellState(cell)), 0)
      )
      case UpdateCell(cell, position) => copy(cells = 
        cells.patch(position, Seq(CellState(cell)), 1)
      )
      case DeleteCell(position) => copy(cells = 
        cells.patch(position, Seq(), 1)
      )
      case UpdateCellState(position, newState) => copy(cells =
        cells.patch(position, Seq(
          cells(position).copy( state = newState )
        ), 1)
      )
      case AppendCellMessage(position, _, _) => copy(cells =
        cells.patch(position, Seq(
          cells(position).copy( messageCount = cells(position).messageCount + 1 )
        ), 1)
      )
      case _:AppendCellArtifact => this
      case _:DeleteCellArtifact => this
      case AdvanceResultId(position, resultId) => 
        val oldCell = cells(position)
        copy(cells = 
          cells.patch(position, Seq(
            oldCell.copy( 
              resultId = Some(resultId), 
              messageCount = 0
            )
          ), 1)
        )
    }

  def withWorkflowId(newWorkflowId: Identifier): WorkflowState = 
    copy(workflowId = newWorkflowId)
}