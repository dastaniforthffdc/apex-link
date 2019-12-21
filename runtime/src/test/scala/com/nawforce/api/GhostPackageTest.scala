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
package com.nawforce.api

import com.nawforce.FileSystemHelper
import com.nawforce.documents.{DocumentType, MetadataDocumentType}
import com.nawforce.names.{Name, TypeName}
import com.nawforce.path.PathLike
import com.nawforce.types.TypeDeclaration
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

class GhostPackageTest extends AnyFunSuite with BeforeAndAfter{

  private var defaultOrg: Org = new Org
  private var root: PathLike = _

  def typeDeclarations(classes: Map[String, String]): Seq[TypeDeclaration] = {
    FileSystemHelper.run(classes) { root: PathLike =>
      this.root = root
      Org.current.withValue(defaultOrg) {
        defaultOrg.unmanaged.deployMetadata(
          classes.map(p => DocumentType(root.join(p._1)).get.asInstanceOf[MetadataDocumentType]).toSeq)
        defaultOrg.unmanaged.getTypes(classes.keys.map(k => TypeName(Name(k.replaceAll("\\.cls$", "")))).toSeq)
      }
    }
  }

  before {
    defaultOrg = new Org
    root = null
  }

  test("Ghost package suppresses declared type error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy extends package.SuperClass {}"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has declared type error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy extends package.SuperClass {}"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls"))
      == "Error: line 1 at 13-18: No type declaration found for 'package.SuperClass'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses declared interface type error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy implements package.Interface {}"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has declared interface type error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy implements package.Interface {}"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls"))
      == "Error: line 1 at 13-18: No declaration found for interface 'package.Interface'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses implicit type error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = package.A.class;} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has implicit type error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = package.A.class;} }"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls"))
      == "Error: line 1 at 33-48: No type declaration found for 'package.A'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses custom object error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__c();} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has custom object error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__c();} }"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls"))
      == "Error: line 1 at 37-52: No type declaration found for 'Schema.package__Foo__c'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses custom metadata error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__mdt();} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has custom metadata error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__mdt();} }"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls")) ==
      "Error: line 1 at 37-54: No type declaration found for 'Schema.package__Foo__mdt'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses platform event error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__e();} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package with wrong namespace has platform event error") {
    defaultOrg.addPackage("silly", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy { {Object a = new package__Foo__e();} }"))
    assert(defaultOrg.issues.getMessages(root.join("Dummy.cls")) ==
      "Error: line 1 at 37-52: No type declaration found for 'Schema.package__Foo__e'\n")
    assert(tds.head.dependencies().isEmpty)
  }

  test("Ghost package suppresses possible field reference error") {
    defaultOrg.addPackage("package", Array(), Array())

    val tds = typeDeclarations(Map("Dummy.cls" -> "public class Dummy extends package.SuperClass { {Object a = b.foo();} }"))
    assert(!defaultOrg.issues.hasMessages)
    assert(tds.head.dependencies().isEmpty)
  }
}