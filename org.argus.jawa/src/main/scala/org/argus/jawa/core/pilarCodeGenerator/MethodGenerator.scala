/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.jawa.core.pilarCodeGenerator

import java.util

import org.stringtemplate.v4.STGroupString

import org.argus.jawa.core.util._
import org.stringtemplate.v4.ST

import org.argus.jawa.core._

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 */ 
abstract class MethodGenerator(global: Global) {
  
  private final val TITLE = "MethodGenerator"
  
  protected var currentComponent: JawaType = _
  protected var classes: ISet[JawaType] = isetEmpty
  /**
   * Map from class (i.e. container class) to list of callback method
   */
  protected var callbackFunctions: IMap[JawaType, ISet[Signature]] = imapEmpty
  protected var conditionCounter: Int = 0
  protected var codeCounter: Int = 0
  protected val template = new STGroupString(JawaCodeModelProvider.getJawaCodeModel)
  protected val procDeclTemplate: ST = template.getInstanceOf("ProcedureDecl")
  protected val localVarsTemplate: ST = template.getInstanceOf("LocalVars")
  protected val bodyTemplate: ST = template.getInstanceOf("Body")
  protected val varGen = new VariableGenerator()
  protected val localVars = new util.ArrayList[String]
  protected val codeFragments = new util.ArrayList[CodeFragmentGenerator]
  
  /**
   * map from a clazz to it's substitute clazz
   */
  protected var substituteClassMap: IMap[JawaType, JawaType] = imapEmpty
  
  /**
   * Map of it's local variables
   */
  protected var localVarsForClasses: IMap[JawaType, String] = imapEmpty

  /**
   * Set of param's clazz name
   */
  protected var paramClasses: ISet[JawaType] = isetEmpty
  
  /**
   * set the substituteClassMap
   */
  def setSubstituteClassMap(map: IMap[JawaType, JawaType]): Unit = this.substituteClassMap = map
  
  /**
   * Registers a list of classes to be automatically scanned for Android
   * lifecycle methods
   * @param classes The list of classes to be automatically scanned for
   * Android lifecycle methods
   */
  def setEntryPointClasses(classes: ISet[JawaType]): Unit = {
    this.classes = classes
  }
  
  def setCurrentComponent(clazz: JawaType): Unit = {
    this.currentComponent = clazz
  }
  
    
  def setCodeCounter(codeCtr: Int): Unit = {
    this.codeCounter = codeCtr
  }
  
   def getCodeCounter: Int = {
    this.codeCounter
  }
  
  /**
   * Sets the list of callback functions to be integrated into the Android
   * lifecycle
   * @param callbackFunctions The list of callback functions to be integrated
   * into the Android lifecycle. This is a mapping from the Android element
   * class (activity, service, etc.) to the list of callback methods for that
   * element.
   */
  def setCallbackFunctions(callbackFunctions: IMap[JawaType, ISet[Signature]]) {
    if(this.callbackFunctions.isEmpty)
      this.callbackFunctions = callbackFunctions
    else {
      val funcs: MMap[JawaType, MSet[Signature]] = mmapEmpty
      this.callbackFunctions.foreach {
        case (k, v) => funcs.getOrElseUpdate(k, msetEmpty) ++= v
      }
      callbackFunctions.foreach {
        case (k, v) => funcs.getOrElseUpdate(k, msetEmpty) ++= v
      }
      this.callbackFunctions = funcs.map{case (k, v) => k -> v.toSet}.toMap
    }
  }
  
  def generate(name: String): (JawaMethod, String) = {
    generate(List(), name)
  }
  
  /**
   * generate environment with predefined methods list
   */
  def generate(methods: List[Signature], name: String): (JawaMethod, String) = {
    val className = this.currentComponent.jawaName
    val methodName = className + "." + name
//    val annotations = new util.ArrayList[ST]
    val signature = JavaKnowledge.genSignature(JavaKnowledge.formatTypeToSignature(this.currentComponent), name, "()V")
    initMethodHead("void", methodName, className, signature, "STATIC")
    val code = generateInternal(methods)
    global.reporter.echo(TITLE, "Environment code:\n" + code)
    (global.resolveMethodCode(signature, code), code)
  }
  
  def generateWithParam(params: List[(JawaType, String)], methods: List[Signature], name: String, kind: String): (JawaMethod, String) = {
    val className = this.currentComponent.jawaName
    val methodName = className + "." + name
//    val annotations = new util.ArrayList[ST]
    var parSigStr: String = ""
    params.foreach{param => if(param._2 != "this") parSigStr += JavaKnowledge.formatTypeToSignature(param._1)}
    val signature = JavaKnowledge.genSignature(JavaKnowledge.formatTypeToSignature(this.currentComponent), name, "(" + parSigStr+ ")V")

    initMethodHead("void", methodName, className, signature, kind)
    val paramArray = new util.ArrayList[ST]
    params.indices.foreach{ i =>
      val paramVar = template.getInstanceOf("ParamVar")
      val (param, kind) = params(i)
      val p = varGen.generate(param)
      localVarsForClasses += (param -> p)
      this.paramClasses += param
      paramVar.add("typ", param)
      paramVar.add("name", p)
      val annot = generateExpAnnotation("kind", List(kind))
      paramVar.add("annotations", new util.ArrayList[ST](util.Arrays.asList(annot)))
      paramArray.add(i, paramVar)
    }
    procDeclTemplate.add("params", paramArray)
    val code = generateInternal(methods)
    global.reporter.echo(TITLE, "Environment code:\n" + code)
    (global.resolveMethodCode(signature, code), code)
  }
  
  protected def generateExpAnnotation(flag: String, exps: List[String]): ST = {
    val expArray = new util.ArrayList[String]
    exps.foreach(exp => expArray.add(exp))
    val annot = template.getInstanceOf("annotationWithExp")
    annot.add("flag", flag)
    annot.add("exps", expArray)
  }
  
  protected def initMethodHead(retTyp: String, methodName: String, owner: String, signature: Signature, access: String): ST = {
    procDeclTemplate.add("retTyp", retTyp)
    procDeclTemplate.add("procedureName", methodName)
    val annotations = new util.ArrayList[ST]
    annotations.add(generateExpAnnotation("owner", List("^`" + owner + "`")))
    annotations.add(generateExpAnnotation("signature", List("`" + signature.signature + "`")))
    annotations.add(generateExpAnnotation("AccessFlag", List(access)))
    procDeclTemplate.add("annotations", annotations)
  }

  def generateInternal(methods: List[Signature]): String

  protected def generateBody(): util.ArrayList[String] = {
    val body: util.ArrayList[String] = new util.ArrayList[String]
    for(i <- 0 until codeFragments.size()){
      body.add(i, codeFragments.get(i).generate())
    }
    body
  }

  protected def generateInstanceCreation(classType: JawaType, codefg: CodeFragmentGenerator): String = {
    val rhs =
      if(classType.jawaName == "java.lang.String"){
        val stringAnnot = generateExpAnnotation("kind", List("object"))
        "\"\" " + stringAnnot.render() 
      } else {
        val newExp = template.getInstanceOf("NewExp")
        newExp.add("name", classType.jawaName)
        newExp.render()
      }
    val va = varGen.generate(classType)
    val variable = template.getInstanceOf("LocalVar")
    variable.add("typ", classType.jawaName)
    variable.add("name", va)
    localVars.add(variable.render())
    val asmt = template.getInstanceOf("AssignmentStmt")
    asmt.add("lhs", va)
    asmt.add("rhs", rhs)
    codefg.setCode(asmt)
    va
  }


  def generateClassConstructor(r: JawaClass, constructionStack: MSet[JawaType], codefg: CodeFragmentGenerator): Signature = {
    constructionStack.add(r.getType)
    val ps = r.getDeclaredMethods
    var cons: Signature = null
    val conMethods = ps.filter(p => p.isConstructor && !p.isStatic && !p.getParamTypes.contains(new JawaType("java.lang.Class")))
    if(conMethods.nonEmpty){
      val p = conMethods.minBy(_.getParamTypes.size)
      cons = p.getSignature
    }
    if(cons != null){
      generateMethodCall(cons, "direct", localVarsForClasses(r.getType), constructionStack, codefg)
    } else {
      global.reporter.warning(TITLE, "Cannot find constructor for " + r)
    }
    cons
  }


  protected def generateMethodCall(pSig: Signature, typ: String, localClassVar: String, constructionStack: MSet[JawaType], codefg: CodeFragmentGenerator): Unit = {
    val paramNum = pSig.getParameterNum
    val params = pSig.getObjectParameters
    var paramVars: Map[Int, String] = Map()
    params.foreach{
      case(i, param) =>
        var r = global.getClassOrResolve(param)
        if(!r.isConcrete){
          val substClassName = this.substituteClassMap.getOrElse(r.getType, null)
          if(substClassName != null) r = global.getClassOrResolve(substClassName)
          else if(r.isInterface) global.getClassHierarchy.getAllImplementersOf(r).foreach(i => if(constructionStack.contains(i.getType)) r = i)
          else if(r.isAbstract) global.getClassHierarchy.getAllSubClassesOf(r).foreach(s => if(s.isConcrete && constructionStack.contains(s.getType)) r = s)
        }
        // to protect from going into dead constructor create loop
        if(localVarsForClasses.contains(r.getType)) paramVars += (i -> localVarsForClasses(r.getType))
        else if(!r.isConcrete){
          val va = varGen.generate(r.getType)
          localVarsForClasses += (r.getType -> va)
          paramVars += (i -> va)
          global.reporter.warning(TITLE, "Cannot create valid constructor for " + r + ", because it is " + r.getAccessFlagsStr + " and cannot find substitute.")
        } else if(!constructionStack.contains(r.getType)){
          val va = generateInstanceCreation(r.getType, codefg)
          localVarsForClasses += (r.getType -> va)
          paramVars += (i -> va)
          generateClassConstructor(r, constructionStack, codefg)
        } else {
          paramVars += (i -> localVarsForClasses(r.getType))
        }
    }
    val invokeStmt = template.getInstanceOf("InvokeStmtWithoutReturn")
    invokeStmt.add("funcName", pSig.getClassName + "." + pSig.methodName)
    val finalParamVars: util.ArrayList[String] = new util.ArrayList[String]
    finalParamVars.add(0, localClassVar)
    var index = 0
    for(i <- 0 until paramNum){
      if(paramVars.contains(i)){
        finalParamVars.add(index + 1, paramVars(i))
      } else {
        finalParamVars.add(index + 1, "x")
//        val paramName = pSig.getParameterTypes(i).name
//        if(paramName == "double" || paramName == "long"){
//          index += 1
//          finalParamVars.add(index + 1, "x")
//        }
      }
      index += 1
    }
    invokeStmt.add("params", finalParamVars)
    val annotations = new util.ArrayList[ST]
    annotations.add(generateExpAnnotation("signature", List("`" + pSig.signature + "`")))
    annotations.add(generateExpAnnotation("classDescriptor", List("^`" + pSig.getClassName + "`")))
    annotations.add(generateExpAnnotation("kind", List(typ)))
    invokeStmt.add("annotations", annotations)
    codefg.setCode(invokeStmt)
  }

  protected def generateCallToAllCallbacks(callbackClass: JawaClass, callbackMethods: Set[JawaMethod], classLocalVar: String, codefg: CodeFragmentGenerator): Unit = {
    var oneCallBackFragment = codefg
    callbackMethods.foreach{
      callbackMethod =>
        val pSig = callbackMethod.getSignature
        val thenStmtFragment = new CodeFragmentGenerator
        createIfStmt(thenStmtFragment, oneCallBackFragment)
        val elseStmtFragment = new CodeFragmentGenerator
        createGotoStmt(elseStmtFragment, oneCallBackFragment)
        thenStmtFragment.addLabel()
        codeFragments.add(thenStmtFragment)
        generateMethodCall(pSig, "virtual", classLocalVar, msetEmpty + callbackClass.getType, thenStmtFragment)
        elseStmtFragment.addLabel()
        codeFragments.add(elseStmtFragment)
        oneCallBackFragment = new CodeFragmentGenerator
        oneCallBackFragment.addLabel()
        codeFragments.add(oneCallBackFragment)
    }
  }

  protected def searchAndBuildMethodCall(subsignature: String, clazz: JawaClass, entryPoints: MList[Signature], constructionStack: MSet[JawaType], codefg: CodeFragmentGenerator): Any = {
    val apopt = findMethod(clazz, subsignature)
    apopt match{
      case Some(ap) =>
        if(ap.getDeclaringClass.isApplicationClass) {
          entryPoints -= ap.getSignature
          assert(ap.isStatic || localVarsForClasses(clazz.getType) != null)
          generateMethodCall(ap.getSignature, "virtual", localVarsForClasses(clazz.getType), constructionStack, codefg)
        }
      case None =>
        global.reporter.warning(TITLE, "Could not find entry point method " + subsignature + " for " + clazz)
        null
    }
  }

  protected def addCallbackMethods(clazz: JawaClass, parentClassLocalVar: String, codefg: CodeFragmentGenerator): Unit = {
    if(!this.callbackFunctions.contains(clazz.getType)) return
    var callbackClasses: Map[JawaClass, ISet[JawaMethod]] = Map()
    this.callbackFunctions(clazz.getType).foreach{
      case (pSig) => 
        val theClass = global.getClassOrResolve(pSig.getClassType)
        val theMethod = findMethod(theClass, pSig.getSubSignature)
        theMethod match {
          case Some(method) =>
            callbackClasses += (theClass -> (callbackClasses.getOrElse(theClass, isetEmpty) + method))
          case None =>
            global.reporter.warning(TITLE, "Could not find callback method " + pSig)
        }
    }
    var oneCallBackFragment = codefg
    callbackClasses.foreach{
      case(callbackClass, callbackMethods) =>
        val classLocalVar: String =
          if(isCompatible(clazz, callbackClass)) parentClassLocalVar
          // create a new instance of this class
          else if(callbackClass.isConcrete){
          val va = generateInstanceCreation(callbackClass.getType, oneCallBackFragment)
            this.localVarsForClasses += (callbackClass.getType -> va)
            generateClassConstructor(callbackClass, msetEmpty + clazz.getType, oneCallBackFragment)
            va
          } else null
        if(classLocalVar != null){
          // build the calls to all callback methods in this clazz
          generateCallToAllCallbacks(callbackClass, callbackMethods, classLocalVar, oneCallBackFragment)
        } else {
          global.reporter.warning(TITLE, "Constructor cannot be generated for callback class " + callbackClass)
        }
        oneCallBackFragment = new CodeFragmentGenerator
        oneCallBackFragment.addLabel()
        codeFragments.add(oneCallBackFragment)
    }
  }

  protected def isCompatible(actual: JawaClass, expected: JawaClass): Boolean = {
    expected.isAssignableFrom(actual)
  }

  protected def createIfStmt(targetfg: CodeFragmentGenerator, codefg: CodeFragmentGenerator): AnyVal = {
    val target = targetfg.getLabel
    if(target != null){
      val condExp = template.getInstanceOf("CondExp")
      condExp.add("lhs", "RandomCoinToss")
      condExp.add("rhs", "head")
      val ifStmt = template.getInstanceOf("IfStmt")
      ifStmt.add("cond", condExp)
      ifStmt.add("label", target)
      codefg.setCode(ifStmt)
    }
  }

  protected def createGotoStmt(targetfg: CodeFragmentGenerator, codefg: CodeFragmentGenerator): AnyVal = {
    val target = targetfg.getLabel
    if(target != null){
      val gotoStmt = template.getInstanceOf("GotoStmt")
      gotoStmt.add("label", target)
      codefg.setCode(gotoStmt)
    }
  }

  protected def createReturnStmt(variable: String, codefg: CodeFragmentGenerator): Boolean = {
    val returnStmt = template.getInstanceOf("ReturnStmt")
    returnStmt.add("variable", variable)
    codefg.setCode(returnStmt)
  }

  protected def createFieldSetStmt(base: String, field: String, rhs: String, annoTyps: List[String], fieldType: String, codefg: CodeFragmentGenerator): Boolean = {
    val mBaseField = template.getInstanceOf("FieldAccessExp")
    mBaseField.add("base", base)
    mBaseField.add("field", field)
    val asmt = template.getInstanceOf("AssignmentStmt")
    asmt.add("lhs", mBaseField)
    asmt.add("rhs", rhs)
    val annotations = new util.ArrayList[ST]
    annotations.add(generateExpAnnotation("kind", annoTyps))
    annotations.add(generateExpAnnotation("type", List("^`" + fieldType + "`")))
    asmt.add("annotations", annotations)
    codefg.setCode(asmt)
  }

  protected class CodeFragmentGenerator {
    protected val codeFragment: ST = template.getInstanceOf("CodeFragment")
    protected val codes: util.ArrayList[ST] = new util.ArrayList[ST]
    protected var label: ST = template.getInstanceOf("Label")
    
    def addLabel(): Unit = {
      label.add("num", conditionCounter)
      codeFragment.add("label", label)
      conditionCounter += 1
    }
    def getLabel: ST = label
    def setCode(code: ST): Boolean = {
      codes.add(code)
    }
    def generate(): String = {
      val finalCodes = new util.ArrayList[ST]
      for(i <- 0 until codes.size){
        val code = template.getInstanceOf("Code")
        code.add("num", codeCounter)
        codeCounter += 1
        code.add("code", codes.get(i))
        finalCodes.add(i, code)
      }
      codeFragment.add("codes", finalCodes)
      codeFragment.render()
    }
  }

  protected def findMethod(currentClass: JawaClass, subSig: String): Option[JawaMethod] = {
    if(currentClass.declaresMethod(subSig)) currentClass.getMethod(subSig)
    else if(currentClass.hasSuperClass) findMethod(currentClass.getSuperClass, subSig)
    else None
  }
}
