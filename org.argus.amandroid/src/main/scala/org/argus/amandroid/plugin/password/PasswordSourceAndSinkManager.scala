/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.amandroid.plugin.password

import org.argus.amandroid.alir.pta.reachingFactsAnalysis.IntentHelper
import org.argus.amandroid.alir.pta.reachingFactsAnalysis.model.InterComponentCommunicationModel
import org.argus.amandroid.alir.taintAnalysis.AndroidSourceAndSinkManager
import org.argus.amandroid.core.{AndroidConstants, ApkGlobal}
import org.argus.jawa.alir.controlFlowGraph.{ICFGInvokeNode, ICFGNode}
import org.argus.jawa.alir.pta.{PTAResult, VarSlot}
import org.argus.jawa.alir.util.ExplicitValueFinder
import org.argus.jawa.compiler.parser.{CallStatement, Location}
import org.argus.jawa.core._

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
class PasswordSourceAndSinkManager(sasFilePath: String) extends AndroidSourceAndSinkManager(sasFilePath){
  private final val TITLE = "PasswordSourceAndSinkManager"
  
  override def isUISource(apk: ApkGlobal, calleeSig: Signature, callerSig: Signature, callerLoc: Location): Boolean = {
    if(calleeSig.signature == AndroidConstants.ACTIVITY_FINDVIEWBYID || calleeSig.signature == AndroidConstants.VIEW_FINDVIEWBYID){
      val callerMethod = apk.getMethod(callerSig).get
      val cs = callerLoc.statement.asInstanceOf[CallStatement]
      val nums = ExplicitValueFinder.findExplicitLiteralForArgs(callerMethod, callerLoc, cs.arg(0))
      nums.filter(_.isInt).foreach{
        num =>
          apk.model.getLayoutControls.get(num.getInt) match{
            case Some(control) =>
              return control.isSensitive
            case None =>
              apk.reporter.error(TITLE, "Layout control with ID " + num + " not found.")
          }
      }
    }
    false
  }

  def isIccSink(apk: ApkGlobal, invNode: ICFGInvokeNode, ptaResult: PTAResult): Boolean = {
    var sinkFlag = false
    val calleeSet = invNode.getCalleeSet
    calleeSet.foreach{
      callee =>
        if(InterComponentCommunicationModel.isIccOperation(callee.callee)){
          sinkFlag = true
          val args = invNode.argNames
          val intentSlot = VarSlot(args(1), isBase = false, isArg = true)
          val intentValues = ptaResult.pointsToSet(intentSlot, invNode.getContext)
          val intentContents = IntentHelper.getIntentContents(ptaResult, intentValues, invNode.getContext)
          val compType = AndroidConstants.getIccCallType(callee.callee.getSubSignature)
          val comMap = IntentHelper.mappingIntents(apk, intentContents, compType)
          comMap.foreach{
            case (_, coms) =>
              if(coms.isEmpty) sinkFlag = true
              coms.foreach{
                case (com, typ) =>
                  typ match {
                    case IntentHelper.IntentType.EXPLICIT => 
                      val clazz = apk.getClassOrResolve(com)
                      if(clazz.isUnknown) sinkFlag = true
                    case IntentHelper.IntentType.IMPLICIT => sinkFlag = true
                  }
              }
          }
        }
    }
    sinkFlag
  }

  def isIccSource(apk: ApkGlobal, entNode: ICFGNode): Boolean = {
    false
  }

}
