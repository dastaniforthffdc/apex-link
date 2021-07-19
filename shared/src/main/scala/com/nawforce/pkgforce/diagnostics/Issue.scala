/*
 [The "BSD licence"]
 Copyright (c) 2017 Kevin Jones
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.nawforce.pkgforce.diagnostics

import com.nawforce.pkgforce.path.PathLike
import upickle.default.{macroRW, ReadWriter => RW}

sealed case class Issue(path: String, diagnostic: Diagnostic) {
  def asString: String = s"$path ${diagnostic.asString}"
}

object Issue {
  val emptyArray: Array[Issue] = Array.empty

  implicit val rw: RW[Issue] = macroRW

  implicit val ordering: Ordering[Issue] = Ordering
    .by[Issue, Int](issue => if (DiagnosticCategory.isErrorType(issue.diagnostic.category)) 0 else 1)
    .orElseBy(_.diagnostic.location.startLine)
    .orElseBy(_.diagnostic.location.startPosition)

  def apply(path: PathLike, category: DiagnosticCategory, location: Location, message: String): Issue = {
    new Issue(path.toString, Diagnostic(category, location, message))
  }
}

sealed case class IssuesAnd[T](issues: Array[Issue], value: T)

object IssuesAnd {
  def apply[T](value: T) = new IssuesAnd[T](Array(), value)
}
