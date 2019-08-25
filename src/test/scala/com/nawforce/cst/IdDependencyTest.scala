/*
 [The "BSD licence"]
 Copyright (c) 2019 Kevin Jones
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
package com.nawforce.cst

import java.io.ByteArrayInputStream
import java.nio.file.{Path, Paths}

import com.nawforce.api.Org
import com.nawforce.documents.StreamProxy
import com.nawforce.types.TypeDeclaration
import com.nawforce.utils.{DotName, Name}
import org.scalatest.{BeforeAndAfter, FunSuite}

class IdDependencyTest extends FunSuite with BeforeAndAfter {
  private val defaultName: Name = Name("Dummy.cls")
  private val defaultPath: Path = Paths.get(defaultName.toString)
  private var defaultOrg: Org = new Org

  private val systemClass = defaultOrg.unmanaged.getType(DotName(Name.System)).get
  private val objectClass = defaultOrg.unmanaged.getType(DotName(Name.Object)).get

  def typeDeclarations(classes: Map[String, String]): Seq[TypeDeclaration] = {
    val paths = classes.map(kv => {
      val fakePath = Paths.get(kv._1 + ".cls")
      StreamProxy.setInputStream(fakePath, new ByteArrayInputStream(kv._2.getBytes()))
      fakePath
    }).toSeq

    Org.current.withValue(defaultOrg) {
      defaultOrg.unmanaged.deployMetadata(paths)
      defaultOrg.unmanaged.getTypes(classes.keys.map(k => DotName(k)).toSeq)
    }
  }

  before {
    StreamProxy.clear()
    defaultOrg = new Org
  }

  test("Local func does not create dependencies") {
    val tds = typeDeclarations(Map("Dummy" -> "public class Dummy {void func() {func();} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.methods.head.dependencies().isEmpty)
  }

  test("Missing Static func creates error") {
    val tds = typeDeclarations(Map("Dummy" -> "public class Dummy {void func() {A.func();} }"))
    assert(defaultOrg.issues.getMessages(defaultPath) ==
      "line 1 at 33-34: Identifier 'A' not found\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Static func creates method dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {static void func() {A.func();} }",
      "A" -> "public class A {}"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.methods.head.dependencies() == tds.tail.toSet)
  }

  test("Platform func creates method dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {static void func() {System.debug('Hello');} }"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.methods.head.dependencies() == Set(systemClass))
  }

  test("Field reference creates method dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {Object a; void func() {a.b = null;} }"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.methods.head.dependencies() == Set(objectClass))
  }

  test("Superclass field reference creates method dependent") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy extends A {void func() {a.b = null;} }",
      "A" -> "public virtual class A {Object a;}"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies() == tds.tail.toSet)
    assert(tds.head.methods.head.dependencies() == Set(objectClass))
  }

  test("Hidden outer class field reference creates error") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {Object a; class B {void func() {a = null;} } }",
    ))
    assert(defaultOrg.issues.getMessages(defaultPath) ==
      "line 1 at 52-53: Identifier 'a' not found\n")
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.nestedTypes.head.dependencies().isEmpty)
    assert(tds.head.nestedTypes.head.methods.head.dependencies().isEmpty)
  }

  test("Outer class static field creates dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {static Object a; class B {void func() {a = null;} } }",
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.nestedTypes.head.dependencies().isEmpty)
    assert(tds.head.nestedTypes.head.methods.head.dependencies() == Set(objectClass))
  }

  test("Property creates dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {Object a {get;} void func() {a = null;} }"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
    assert(tds.head.methods.head.dependencies() == Set(objectClass))
  }

  test("Superclass property creates dependency") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy extends A {void func() {a = null;} }",
      "A" -> "public virtual class A {Object a {get;}}"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies() == tds.tail.toSet)
    assert(tds.head.methods.head.dependencies() == Set(objectClass))
  }

  test("Local var not dependent") {
    val tds = typeDeclarations(Map(
      "Dummy" -> "public class Dummy {void func() {Object a; a.b = null;} }"
    ))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.methods.head.dependencies() == Set(objectClass))
  }
}