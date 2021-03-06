/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.jawa.alir.taintAnalysis

import org.argus.jawa.alir.Context
import org.argus.jawa.alir.pta.{Instance, PTASlot}
import org.argus.jawa.alir.reachingDefinitionAnalysis.Slot

object TaintSlotPosition extends Enumeration {
  val LHS, RHS, ARG = Value
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */
trait TaintSlot extends Slot {
  def context: Context
  def pos: TaintSlotPosition.Value
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */
final case class InstanceTaintSlot(s: PTASlot, pos: TaintSlotPosition.Value, context: Context, ins: Instance) extends TaintSlot

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */
final case class PrimitiveTaintSlot(s: PTASlot, pos: TaintSlotPosition.Value, context: Context) extends TaintSlot

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */ 
final case class TaintFact(s: TaintSlot, tag: String){
  def getContext: Context = s.context
  override def toString: String = {
    "TaintFact" + "(" + s + ":" + tag + ")"
  }
}
