/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.jawa.alir.dataFlowAnalysis

import org.argus.jawa.alir.{AlirLoc, AlirNode}
import org.argus.jawa.alir.controlFlowGraph._
import org.argus.jawa.compiler.parser._
import org.argus.jawa.core.Signature
import org.argus.jawa.core.util.{ISet, _}

/**
  * @author <a href="mailto:robby@k-state.edu">Robby</a>
  */
trait EntrySetListener[LatticeElement] {
  def gotoJump(j : GotoStatement, s : ISet[LatticeElement]) {}
  def ifJump(j : IfStatement, s : ISet[LatticeElement]) {}
  def switchJump(j : SwitchStatement, s : ISet[LatticeElement]) {}
  def switchCase(sc : SwitchCase, s : ISet[LatticeElement]) {}
  def switchDefault(sd : SwitchDefaultCase, s : ISet[LatticeElement]) {}
  def returnJump(j : ReturnStatement, s : ISet[LatticeElement]) {}
  def callJump(j : CallStatement, s : ISet[LatticeElement]) {}
  def exitSet(s : ISet[LatticeElement]) {}
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
trait MonotoneDataFlowAnalysisResult[N <: AlirNode, LatticeElement] extends DataFlowAnalysisResult[N, LatticeElement] {
  def entrySet: N => ISet[LatticeElement]
}

trait MonotoneDataFlowAnalysisBuilder[N <: AlirNode, LatticeElement] extends MonotoneDataFlowAnalysisResult[N, LatticeElement] {
  def update(d: ISet[LatticeElement], n: N): Boolean
  def visit(currentNode: N with AlirLoc, esl: Option[EntrySetListener[LatticeElement]] = None): Boolean
  def confluence: (ISet[LatticeElement], ISet[LatticeElement]) => ISet[LatticeElement]
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */
trait MonotonicFunction[N <: AlirNode, LatticeElement] {
  def apply(s: ISet[LatticeElement], e: Statement, currentNode: N): ISet[LatticeElement]
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */
trait MethodBodyProvider {
  def getBody(sig: Signature): ResolvedBody
}

/**
  * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
  */
trait NodeProvider[N <: AlirNode, LatticeElement, LOC] {
  def newLoc(currentNode: N with AlirLoc, newl: Location): LOC
  def next(currentNode: N with AlirLoc, body: ResolvedBody): N
  def node(l: Location, loc: LOC): N
  def exitNode(currentNode: N): N
  def returnNode(currentNode: N with AlirLoc): N
  def onPreVisitNode(node: N, preds: CSet[N])
  def onPostVisitNode(node: N, succs: CSet[N])
  def process(
      startNode: N,
      mdaf: MonotoneDataFlowAnalysisBuilder[N, LatticeElement],
      callr: Option[CallResolver[N, LatticeElement]]): Unit
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */
trait CallResolver[N <: AlirNode, LatticeElement] {
  /**
   * It returns the facts for each callee entry node and caller return node
   */
  def resolveCall(s: ISet[LatticeElement], cs: CallStatement, callerNode: N): (IMap[N, ISet[LatticeElement]], ISet[LatticeElement])
  def getAndMapFactsForCaller(calleeS: ISet[LatticeElement], callerNode: N, calleeExitNode: N): ISet[LatticeElement]
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */
object MonotoneDataFlowAnalysisFramework {

  final val TITLE = "MonotoneDataFlowAnalysisFramework"

  def apply[N <: AlirNode, LatticeElement, LOC](
      cfg: ControlFlowGraph[N],
      forward: Boolean, lub: Boolean,
      mbp: MethodBodyProvider,
      np: NodeProvider[N, LatticeElement, LOC],
      gen: MonotonicFunction[N, LatticeElement],
      kill: MonotonicFunction[N, LatticeElement],
      callr: Option[CallResolver[N, LatticeElement]],
      iota: ISet[LatticeElement],
      initial: ISet[LatticeElement]): MonotoneDataFlowAnalysisResult[N, LatticeElement] = {
    val flow = if (forward) cfg else cfg.reverse
    val startNode = flow.entryNode
    build(cfg, forward, lub, mbp, np, gen, kill, callr, startNode, iota, initial)
  }

  def build[N <: AlirNode, LatticeElement, LOC](
      cfg: ControlFlowGraph[N],
      forward: Boolean, lub: Boolean,
      mbp: MethodBodyProvider,
      np: NodeProvider[N, LatticeElement, LOC],
      gen: MonotonicFunction[N, LatticeElement],
      kill: MonotonicFunction[N, LatticeElement],
      callr: Option[CallResolver[N, LatticeElement]],
      startNode: N,
      iota: ISet[LatticeElement],
      initial: ISet[LatticeElement]): MonotoneDataFlowAnalysisResult[N, LatticeElement] = {

    val entrySetMap: MMap[N, ISet[LatticeElement]] = mmapEmpty
    def getEntrySet(n: N): ISet[LatticeElement] = entrySetMap.getOrElse(n, initial)

    class Mdaf(val entrySet: N => ISet[LatticeElement],
               initial: ISet[LatticeElement])
      extends MonotoneDataFlowAnalysisBuilder[N, LatticeElement] {
      type DFF = ISet[LatticeElement]

      def confluence: (DFF, DFF) => DFF = if (lub) iunion[LatticeElement] else iintersect[LatticeElement]

      override def toString: String = {
        val sb = new StringBuilder
        for (n <- cfg.nodes.take(1000)) {
          sb.append("%s = %s\n".format(n, entrySet(n).toString))
        }
        sb.append("\n")
        sb.toString
      }

//      def exitSet: N => DFF = {
//        case en: ICFGEntryNode =>
//          getEntrySet(en)
//        case xn: ICFGExitNode =>
//          getEntrySet(xn)
//        case cn: ICFGCallNode =>
//          val r = calculateResult(cn)
//          r.values.reduce(iunion[LatticeElement])
//        case rn: ICFGReturnNode =>
//          getEntrySet(rn)
//        case nn: ICFGNormalNode =>
//          val r = calculateResult(nn)
//          r.values.reduce(iunion[LatticeElement])
//        case a => throw new RuntimeException("unexpected node type: " + a)
//      }

      protected def fS(e: Statement, in: DFF, currentNode: N): DFF =
        kill(in, e, currentNode).union(gen(in, e, currentNode))

      def update(s: DFF, n: N): Boolean = {
        val oldS = entrySet(n)
        val newS = s
        if (oldS != newS) {
          entrySetMap.update(n, newS)
          true
        } else
          false
      }

//        protected def visitBackward(
//            currentNode: ICFGLocNode,
//            esl: Option[EntrySetListener[LatticeElement]]): IMap[N, DFF] = {
//          val pSig = currentNode.getOwner
//          val pst = mbp.getPst(pSig)
//          val l = pst.location(currentNode.getLocIndex)
//          val currentContext = currentNode.getContext
//          val callerContext = currentContext.copy.removeTopContext()
//
//          val latticeMap: MMap[N, DFF] = mmapEmpty
//
//          if(l.name.isEmpty)
//            currentContext.setContext(pSig, l.index.toString)
//          else
//            currentContext.setContext(pSig, l.name.get.uri)
//          val eslb = esl.orNull
//          def jumpF(j: Jump): DFF =
//            j match {
//              case j: IfJump =>
//                var result = initial
//                val numOfIfThens = j.ifThens.size
//                for (i <- 0 until numOfIfThens) {
//                  val ifThen = j.ifThens(i)
//                  val ifThenContext = callerContext.copy
//                  ifThenContext.setContext(pSig, ifThen.target.uri)
//                  val ifThenLoc = pst.location(ifThen.target.uri)
//                  val sn = node(ifThenLoc, ifThenContext)
//                  var r = getEntrySet(sn)
//                  for (k <- tozero(i)) {
//                    val it = j.ifThens(k)
//                    r = fE(it.cond, r, currentNode)
//                  }
//                  result = confluence(result, r)
//                }
//                {
//                  val ifElse = j.ifElse
//                  val ifElseDefined = ifElse.isDefined
//                  val sn =
//                    if (ifElseDefined) {
//                      val ifElseContext = callerContext.copy
//                      ifElseContext.setContext(pSig, ifElse.get.target.uri)
//                      val ifElseLoc = pst.location(ifElse.get.target.uri)
//                      node(ifElseLoc, ifElseContext)
//                    }
//                    else next(l, pst, pSig, callerContext)
//                  var r = getEntrySet(sn)
//                  for (k <- tozero(numOfIfThens - 1)) {
//                    val it = j.ifThens(k)
//                    r = fE(it.cond, r, currentNode)
//                  }
//                  if (ifElseDefined && esl.isDefined) eslb.ifElse(ifElse.get, r)
//                  result = confluence(result, r)
//                }
//                if (esl.isDefined) eslb.ifJump(j, result)
//                result
//              case j: SwitchJump =>
//                var result = initial
//                val numOfCases = j.cases.size
//                for (i <- 0 until numOfCases) {
//                  val switchCase = j.cases(i)
//                  val switchCaseContext = callerContext.copy
//                  switchCaseContext.setContext(pSig, switchCase.target.uri)
//                  val switchCaseLoc = pst.location(switchCase.target.uri)
//                  val sn = node(switchCaseLoc, switchCaseContext)
//                  var r = getEntrySet(sn)
//                  if (switchAsOrderedMatch)
//                    for (k <- tozero(i)) {
//                      val sc = j.cases(k)
//                      r = fE(sc.cond, r, currentNode)
//                    }
//                  else
//                    r = fE(switchCase.cond, r, currentNode)
//                  if (esl.isDefined) eslb.switchCase(switchCase, r)
//                  result = confluence(result, r)
//                }
//              {
//                val switchDefault = j.defaultCase
//                val switchDefaultDefined = switchDefault.isDefined
//                val sn =
//                  if (switchDefaultDefined){
//                    val switchDefaultContext = callerContext.copy
//                    switchDefaultContext.setContext(pSig, switchDefault.get.target.uri)
//                    val switchDefaultLoc = pst.location(switchDefault.get.target.uri)
//                    node(switchDefaultLoc, switchDefaultContext)
//                  }
//                  else next(l, pst, pSig, callerContext)
//                var r = getEntrySet(sn)
//                if (switchAsOrderedMatch)
//                  for (k <- tozero(numOfCases - 1)) {
//                    val sc = j.cases(k)
//                    r = fE(sc.cond, r, currentNode)
//                  }
//                if (esl.isDefined && switchDefaultDefined)
//                  eslb.switchDefault(switchDefault.get, r)
//                result = confluence(result, r)
//              }
//                if (esl.isDefined)
//                  eslb.switchJump(j, result)
//                result
//              case j: GotoJump =>
//                val jContext = callerContext.copy
//                jContext.setContext(pSig, j.target.uri)
//                val jLoc = pst.location(j.target.uri)
//                val sn = node(jLoc, jContext)
//                val result = getEntrySet(sn)
//                if (esl.isDefined)
//                  eslb.gotoJump(j, result)
//                result
//              case j: ReturnJump =>
//                val exitContext = callerContext.copy
//                exitContext.setContext(pSig, pSig.signature)
//                val sn = icfg.getICFGExitNode(exitContext)
//                val result = fOE(j.exp, getEntrySet(sn), currentNode)
//                if (esl.isDefined)
//                  eslb.returnJump(j, result)
//                result
//              case j: CallJump =>
//                val s =
//                  if (j.jump.isEmpty)
//                    getEntrySet(next(l, pst, pSig, callerContext))
//                  else
//                    jumpF(j.jump.get)
//                val result = fA(j, s, currentNode)
//                if (esl.isDefined)
//                  eslb.callJump(j, result)
//                result
//            }
//          val ln = node(l, currentContext)
//          l match {
//            case l: ComplexLocation =>
//              val result = bigConfluence(l.transformations.map { t =>
//                var r =
//                  if (t.jump.isEmpty)
//                    getEntrySet(next(l, pst, pSig, callerContext))
//                  else
//                    jumpF(t.jump.get)
//                val numOfActions = t.actions.size
//                for (i <- untilzero(numOfActions)) {
//                  val a = t.actions(i)
//                  r = actionF(r, a, currentNode)
//                  if (esl.isDefined) eslb.action(a, r)
//                }
//                if (esl.isDefined) eslb.exitSet(None, r)
//                r
//              })
//              latticeMap += (ln -> result)
//            case l: ActionLocation =>
//              val result = actionF(getEntrySet(next(l, pst, pSig, callerContext)), l.action, currentNode)
//              if (esl.isDefined) {
//                eslb.action(l.action, result)
//                eslb.exitSet(None, result)
//              }
//              latticeMap += (ln -> result)
//            case l: JumpLocation =>
//              val result = jumpF(l.jump)
//              if (esl.isDefined) {
//                eslb.exitSet(None, result)
//              }
//              latticeMap += (ln -> result)
//            case l: EmptyLocation =>
//              val result = getEntrySet(next(l, pst, pSig, callerContext))
//              if (esl.isDefined) {
//                eslb.exitSet(None, result)
//              }
//              latticeMap += (ln -> result)
//          }
//          latticeMap.toMap
//        }


      protected def visitForward(
          currentNode: N with AlirLoc,
          esl: Option[EntrySetListener[LatticeElement]]): IMap[N, DFF] = {
        val body = mbp.getBody(currentNode.getOwner)
        val l = body.locations(currentNode.locIndex)

        val latticeMap: MMap[N, DFF] = mmapEmpty

        def jumpF(s: DFF, j: Jump): Unit =
          j match {
            case j: IfStatement =>
              if (esl.isDefined) esl.get.ifJump(j, s)
              val ifGotoLoc = body.locations(j.targetLocation.locationIndex)
              val ifGotoContext = np.newLoc(currentNode, ifGotoLoc)
              val gn = np.node(ifGotoLoc, ifGotoContext)
              latticeMap += (gn -> s)
              val sn = np.next(currentNode, body)
              if (esl.isDefined) esl.get.exitSet(s)
              latticeMap += (sn -> s)
            case j: SwitchStatement =>
              if (esl.isDefined) esl.get.switchJump(j, s)
              for (switchCase <- j.cases) {
                val switchCaseLoc = body.locations(switchCase.targetLocation.locationIndex)
                val switchCaseContext = np.newLoc(currentNode, switchCaseLoc)
                val sn = np.node(switchCaseLoc, switchCaseContext)
                if (esl.isDefined) {
                  esl.get.switchCase(switchCase, s)
                  esl.get.exitSet(s)
                }
                latticeMap += (sn -> s)
              }
              if (j.defaultCaseOpt.isEmpty) {
                val sn = np.next(currentNode, body)
                if (esl.isDefined) esl.get.exitSet(s)
                latticeMap += (sn -> s)
              } else {
                val switchDefault = j.defaultCaseOpt.get
                val switchDefaultLoc = body.locations(switchDefault.targetLocation.locationIndex)
                val switchDefaultContext = np.newLoc(currentNode, switchDefaultLoc)
                val sn = np.node(switchDefaultLoc, switchDefaultContext)
                if (esl.isDefined) {
                  esl.get.switchDefault(switchDefault, s)
                  esl.get.exitSet(s)
                }
                latticeMap += (sn -> s)
              }
            case j: GotoStatement =>
              val gotoLoc = body.locations(j.targetLocation.locationIndex)
              val gotoContext = np.newLoc(currentNode, gotoLoc)
              val sn = np.node(gotoLoc, gotoContext)
              if (esl.isDefined) {
                esl.get.gotoJump(j, s)
                esl.get.exitSet(s)
              }
              latticeMap += (sn -> s)
            case j: ReturnStatement =>
              val succs = cfg.successors(currentNode)
              if (esl.isDefined) {
                esl.get.returnJump(j, s)
                esl.get.exitSet(s)
              }
              succs.foreach(succ=>latticeMap += (succ -> s))
            case j: CallStatement =>
              if (esl.isDefined) esl.get.callJump(j, s)
              if (callr.isDefined) {
                val (calleeFactsMap, retFacts) = callr.get.resolveCall(s, j, currentNode)
                calleeFactsMap.foreach {
                  case (calleeNode, calleeFacts) =>
                    latticeMap += (calleeNode -> calleeFacts)
                }
                val rn = np.returnNode(currentNode)
                if (esl.isDefined) esl.get.exitSet(entrySet(rn))
                latticeMap += (rn -> retFacts)
              } else {
                if(esl.isDefined) esl.get.exitSet(s)
                val succs = cfg.successors(currentNode)
                succs.foreach(succ=>latticeMap += (succ -> s))
              }
          }

        val s = fS(l.statement, entrySet(currentNode), currentNode)
        l.statement match {
          case j: Jump =>
            jumpF(s, j)
          case _: Statement =>
            if(esl.isDefined) esl.get.exitSet(s)
            val succs = cfg.successors(currentNode)
            succs.foreach(succ=>latticeMap += (succ -> s))
        }
        latticeMap.toMap
      }

      def calculateResult(currentNode: N with AlirLoc,
                          esl: Option[EntrySetListener[LatticeElement]] = None): IMap[N, DFF] = {
        //    if (forward)
        visitForward(currentNode, esl)
        //    else visitBackward(currentNode, esl)
      }

      def visit(currentNode: N with AlirLoc,
                esl: Option[EntrySetListener[LatticeElement]] = None): Boolean = {
        calculateResult(currentNode, esl).map{case (n, facts) =>
          update(confluence(facts, getEntrySet(n)), n)}.exists(_ == true)
      }
    }

    val mdaf = new Mdaf(getEntrySet, initial)
    entrySetMap.put(startNode, iota)
    np.process(startNode, mdaf, callr)
    mdaf
  }
}