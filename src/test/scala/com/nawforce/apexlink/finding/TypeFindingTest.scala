/*
 Copyright (c) 2017 Kevin Jones, All rights reserved.
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
 */
package com.nawforce.apexlink.finding

import com.nawforce.apexlink.names._
import com.nawforce.apexlink.{FileSystemHelper, TestHelper}
import com.nawforce.pkgforce.names.{Name, Names, TypeName}
import com.nawforce.pkgforce.path.PathLike
import org.scalatest.funsuite.AnyFunSuite

class TypeFindingTest extends AnyFunSuite with TestHelper {

  test("Bad type not") {
    withEmptyOrg(org => {
      assert(org.unmanaged.modules.head.findType(TypeName(Name("Hello")), None).toOption.isEmpty)
    })
  }

  test("Platform type") {
    withEmptyOrg(org => {
      val typeName = TypeName(Seq(Name("String"))).withOuter(Some(TypeName(Names.System)))
      assert(
        org.unmanaged.modules.head
          .findType(TypeName(Name("String")), None)
          .toOption
          .get
          .typeName == typeName)
    })
  }

  test("Platform type (wrong case)") {
    withEmptyOrg(org => {
      val typeName = TypeName(Seq(Name("String"))).withOuter(Some(TypeName(Names.System)))
      assert(
        org.unmanaged.modules.head
          .findType(TypeName(Name("STRING")), None)
          .toOption
          .get
          .typeName == typeName)
    })
  }

  test("Custom Outer type") {
    val td = typeDeclaration("public class Dummy {}")
    assert(!hasIssues)
    withOrg(org => {
      assert(
        org.unmanaged.modules.head
          .findType(TypeName(Name("Dummy")), None)
          .toOption
          .get
          .typeName == td.typeName)
    })
  }

  test("Custom Outer type (Wrong Case)") {
    val td = typeDeclaration("public class Dummy {}")
    assert(!hasIssues)
    withOrg(org => {
      assert(
        org.unmanaged.modules.head
          .findType(TypeName(Name("duMmy")), None)
          .toOption
          .get
          .typeName == td.typeName)
    })
  }

  test("Custom Inner type") {
    val td = typeDeclaration("public class Dummy {class Inner {}}")
    assert(!hasIssues)
    withOrg(org => {
      val innerTypeName = TypeName(Name("Inner"), Nil, Some(TypeName(Name("Dummy"))))
      assert(
        org.unmanaged.modules.head
          .findType(innerTypeName, None)
          .toOption
          .get
          .typeName == innerTypeName)
    })
  }

  test("Custom Inner type (Wrong case)") {
    val td = typeDeclaration("public class Dummy {class Inner {}}")
    assert(!hasIssues)
    withOrg(org => {
      val innerTypeName = TypeName(Name("iNner"), Nil, Some(TypeName(Name("Dummy"))))
      assert(
        org.unmanaged.modules.head
          .findType(innerTypeName, None)
          .toOption
          .get
          .typeName == innerTypeName)
    })
  }

  test("Custom Outer type visible outside package") {
    FileSystemHelper.run(
      Map(
        "sfdx-project.json" ->
          """{
            |"namespace": "pkg2",
            |"packageDirectories": [{"path": "pkg2"}],
            |"plugins": {"dependencies": [{"namespace": "pkg1", "path": "pkg1"}]}
            |}""".stripMargin,
        "pkg1/Dummy.cls" -> "global class Dummy {}",
        "pkg2/Use.cls" -> "global class Use { {pkg1.Dummy value;}}",
      )) { root: PathLike =>
      createOrg(root)
      assert(!hasIssues)
    }
  }

  test("Custom Outer type not visible outside package") {
    FileSystemHelper.run(
      Map(
        "sfdx-project.json" ->
          """{
            |"namespace": "pkg2",
            |"packageDirectories": [{"path": "pkg2"}],
            |"plugins": {"dependencies": [{"namespace": "pkg1", "path": "pkg1"}]}
            |}""".stripMargin,
        "pkg1/Dummy.cls" -> "public class Dummy {}",
        "pkg2/Use.cls" -> "global class Use { {pkg1.Dummy value;}}",
      )) { root: PathLike =>
      val org = createOrg(root)
      assert(org.issues
        .getMessages("/pkg2/Use.cls") == "Missing: line 1 at 31-36: No type declaration found for 'pkg1.Dummy'\n")
    }
  }

  test("Custom Inner type visible outside package") {
    FileSystemHelper.run(
      Map(
        "sfdx-project.json" ->
          """{
            |"namespace": "pkg2",
            |"packageDirectories": [{"path": "pkg2"}],
            |"plugins": {"dependencies": [{"namespace": "pkg1", "path": "pkg1"}]}
            |}""".stripMargin,
        "pkg1/Dummy.cls" -> "global class Dummy {class Inner {}}",
        "pkg2/Use.cls" -> "global class Use { {pkg1.Dummy.Inner value;}}",
      )) { root: PathLike =>
      createOrg(root)
      assert(!hasIssues)
    }
  }

  test("Custom Inner type not visible outside package") {
    FileSystemHelper.run(
      Map(
        "sfdx-project.json" ->
          """{
            |"namespace": "pkg2",
            |"packageDirectories": [{"path": "pkg2"}],
            |"plugins": {"dependencies": [{"namespace": "pkg1", "path": "pkg1"}]}
            |}""".stripMargin,
        "pkg1/Dummy.cls" -> "public class Dummy {class Inner {}}",
        "pkg2/Use.cls" -> "global class Use { {pkg1.Dummy.Inner value;}}",
      )) { root: PathLike =>
      val org = createOrg(root)
      assert(org.issues
        .getMessages("/pkg2/Use.cls") == "Missing: line 1 at 37-42: No type declaration found for 'pkg1.Dummy.Inner'\n")
    }
  }
}