/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.amandroid.core.dedex

import org.argus.amandroid.core.decompile.{DecompileLayout, DecompileStrategy, DecompilerSettings}
import org.argus.jawa.core.{JawaType, NoLibraryAPISummary, NoReporter}
import org.argus.jawa.core.sourcefile.SourcefileParser
import org.scalatest.{FlatSpec, Matchers}
import org.argus.jawa.core.util.FileUtil

/**
  * Created by fgwei on 4/8/17.
  */
class DedexTest extends FlatSpec with Matchers {

  val recordFilter: (JawaType => Boolean) = { _ =>
    true
  }

  val settings = DecompilerSettings(debugMode = false, forceDelete = true, DecompileStrategy(DecompileLayout(""), NoLibraryAPISummary), new NoReporter)

  "Dedex data.dex" should "produce expected code" in {
    val dedex = new JawaDeDex
    val dexUri = FileUtil.toUri(getClass.getResource("/dexes/data.dex").getPath)
    dedex.decompile(dexUri, settings)
    dedex.getCodes map { case (_, code) =>
      noException should be thrownBy SourcefileParser.parse(code, new NoReporter)
    }
    assert(!dedex.getCodes.exists{case (_, code) => code.contains("@INVALID_")})
  }

  "Dedex comprehensive.dex" should "produce expected code" in {
    val dedex = new JawaDeDex
    val dexUri = FileUtil.toUri(getClass.getResource("/dexes/comprehensive.dex").getPath)
    dedex.decompile(dexUri, settings)
    dedex.getCodes map { case (_, code) =>
      noException should be thrownBy SourcefileParser.parse(code, new NoReporter)
    }
    assert(!dedex.getCodes.exists{case (_, code) => code.contains("@INVALID_")})
  }

  "Dedex comprehensive.odex" should "produce expected code" in {
    val dedex = new JawaDeDex
    val dexUri = FileUtil.toUri(getClass.getResource("/dexes/comprehensive.odex").getPath)
    dedex.decompile(dexUri, settings)
    dedex.getCodes map { case (_, code) =>
      noException should be thrownBy SourcefileParser.parse(code, new NoReporter)
    }
    assert(!dedex.getCodes.exists{case (_, code) => code.contains("@INVALID_")})
  }

  "Dedex oat file BasicDreams.odex" should "produce expected code" in {
    val dedex = new JawaDeDex
    val dexUri = FileUtil.toUri(getClass.getResource("/dexes/BasicDreams.odex").getPath)
    dedex.decompile(dexUri, settings)
    dedex.getCodes map { case (_, code) =>
      noException should be thrownBy SourcefileParser.parse(code, new NoReporter)
    }
    assert(!dedex.getCodes.exists{case (_, code) => code.contains("@INVALID_")})
  }
}
