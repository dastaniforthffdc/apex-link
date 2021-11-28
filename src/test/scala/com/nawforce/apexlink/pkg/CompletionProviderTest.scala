package com.nawforce.apexlink.pkg

import com.nawforce.apexlink.rpc.CompletionItemLink
import com.nawforce.apexlink.{FileSystemHelper, TestHelper}
import com.nawforce.pkgforce.path.PathLike
import org.scalatest.funsuite.AnyFunSuite

class CompletionProviderTest extends AnyFunSuite with TestHelper {

  test("Internal Completion") {
    FileSystemHelper.run(Map()) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content =
          """public class Dummy { public void someMethod() {m}
            |public String methodA(){}
            |public String methodB(String a, String b){}
            |public static String methodStatic(){}
            |private String methodPrivate(){}
            |public String myField;
            |public static String myStaticField;
            |private String myPrivateField;
            |public class MyInner{};
            |private interface MyPrivateInner{};
            |}""".stripMargin
        val offset = content.split('\n').head.length - 1
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset, content).toSet ==
            Set(CompletionItemLink("methodB(a, b)", "Method"), CompletionItemLink("methodA()", "Method"),
              CompletionItemLink("methodPrivate()", "Method"), CompletionItemLink("methodStatic()", "Method"),
              CompletionItemLink("myField", "Field"), CompletionItemLink("myStaticField", "Field"),
              CompletionItemLink("myPrivateField", "Field"), CompletionItemLink("MyInner", "Class"),
              CompletionItemLink("MyPrivateInner", "Interface"))
        )
    }
  }

  test("Internal Completion (multiple chars)") {
    FileSystemHelper.run(Map()) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content =
          """public class Dummy { public void someMethod() {me}
            |public String methodA(){}
            |public String methodB(String a, String b){}
            |public static String methodStatic(){}
            |private String methodPrivate(){}
            |public String meField;
            |public static String myStaticField;
            |private String myPrivateField;
            |public class MeInner{};
            |private interface MyPrivateInner{};
            |}""".stripMargin
        val offset = content.split('\n').head.length - 1
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset, content).toSet ==
            Set(CompletionItemLink("methodB(a, b)", "Method"), CompletionItemLink("methodA()", "Method"),
              CompletionItemLink("methodPrivate()", "Method"), CompletionItemLink("methodStatic()", "Method"),
              CompletionItemLink("meField", "Field"), CompletionItemLink("MeInner", "Class"))
        )
    }
  }

  test("Instance Completions") {
    FileSystemHelper.run(Map(
      "Dummy.cls" ->
        """public class Dummy {
          |public String methodA(){}
          |public String methodB(String a, String b){}
          |public static String methodStatic(){}
          |private String methodPrivate(){}
          |public String myField;
          |public static String myStaticField;
          |private String myPrivateField;
          |public class MyInner{};
          |private interface MyPrivateInner{};
          |}""".stripMargin)) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content = "public class Completion { public Completion() {String a = new Dummy().m"
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset = content.length, content).toSet ==
            Set(CompletionItemLink("methodB(a, b)", "Method"), CompletionItemLink("methodA()", "Method"),
              CompletionItemLink("myField", "Field")))
    }
  }

  test("Static Completions") {
    FileSystemHelper.run(Map(
      "Dummy.cls" ->
        """public class Dummy {
          |public String methodA(){}
          |public String methodB(String a, String b){}
          |public static String methodStatic(){}
          |private String methodPrivate(){}
          |public String myField;
          |public static String myStaticField;
          |private String myPrivateField;
          |public class MyInner{};
          |private interface MyPrivateInner{};
          |}""".stripMargin)) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content = "public class Completion { public Completion() {String a = Dummy.m"
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset = content.length, content).toSet ==
            Set(CompletionItemLink("methodStatic()", "Method"), CompletionItemLink("myStaticField", "Field"),
              CompletionItemLink("MyInner", "Class")))
    }
  }

  test("Instance Completions (in statement)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" ->
        """public class Dummy {
          |public String methodA(){}
          |public String methodB(String a, String b){}
          |public static String methodStatic(){}
          |private String methodPrivate(){}
          |public String myField;
          |public static String myStaticField;
          |private String myPrivateField;
          |public class MyInner{};
          |private interface MyPrivateInner{};
          |}""".stripMargin)) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content = "public class Completion { public Completion() {Dummy a; if ( a.m"
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset = content.length, content).toSet ==
            Set(CompletionItemLink("methodB(a, b)", "Method"), CompletionItemLink("methodA()", "Method"),
              CompletionItemLink("myField", "Field")
            ))
    }
  }

  test("Instance Completions (no context)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" ->
        """public class Dummy {
          |public String methodA(){}
          |public String methodB(String a, String b){}
          |public static String methodStatic(){}
          |private String methodPrivate(){}
          |public String myField;
          |public static String myStaticField;
          |private String myPrivateField;
          |public class MyInner{};
          |private interface MyPrivateInner{};
          |}""".stripMargin)) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content = "public class Completion { public Completion() {String a = new Dummy()."
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset = content.length, content).toSet ==
            Set(CompletionItemLink("methodA()", "Method"), CompletionItemLink("methodB(a, b)", "Method"),
              CompletionItemLink("clone()", "Method"), CompletionItemLink("hashCode()", "Method"),
              CompletionItemLink("toString()", "Method"), CompletionItemLink("equals(other)", "Method"),
              CompletionItemLink("myField", "Field")))
    }
  }

  test("External Method Completions (at end)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" ->
        """public class Dummy {
          |public String methodA(){}
          |public String methodB(String a, String b){}
          | public static String methodStatic(){}
          |private String methodPrivate(){}
          |}""".stripMargin)) {
      root: PathLike =>
        val org = createOrg(root)
        val path = root.join("Completion.cls")
        val content = "public class Completion { public Completion() {String a = new Dummy()"
        assert(
          org
            .getCompletionItems(path.toString, line = 1, offset = content.length, content).map(_.label).isEmpty)
    }
  }

  test("Empty Class Completions") {
    FileSystemHelper.run(Map("Dummy.cls" -> "")) {
      root: PathLike =>
        val org = createOrg(root)
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = 0, "").map(_.label)
            sameElements Array("abstract", "static", "class", "testmethod", "transient", "enum", "final", "virtual",
            "global", "webservice", "inherited", "with", "without", "interface", "override", "private", "protected", "public"))
    }
  }

  test("Class Declaration Completions") {
    FileSystemHelper.run(Map("Dummy.cls" -> "")) {
      root: PathLike =>
        val org = createOrg(root)
        val testSrc = "class Dummy"
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = testSrc.length, testSrc)
            .map(_.label) sameElements Array("implements", "extends"))
    }
  }

  test("Class Declaration Completions (extends)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" -> "",
      "Foo.cls" -> "public class Foo { }"
    )) {
      root: PathLike =>
        val org = createOrg(root)
        val testSrc = "class Dummy extends F"
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = testSrc.length, testSrc)
            .map(_.label) sameElements Array("Foo"))
    }
  }

  test("Primary Completions (variable)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" -> ""
    )) {
      root: PathLike =>
        val org = createOrg(root)
        val testSrc = "class Dummy { {String abc; a"
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = testSrc.length, testSrc)
            .map(_.label) sameElements Array("abc"))
    }
  }

  test("Primary Completions (variable type)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" -> ""
    )) {
      root: PathLike =>
        val org = createOrg(root)
        val testSrc = "class Dummy { {Boolean abc; abc."
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = testSrc.length, testSrc)
            .map(_.label) sameElements Array("addError(msg, escape)", "addError(msg, escape)", "addError(msg)", "addError(msg)"))
    }
  }

  test("Primary Completions (method type)") {
    FileSystemHelper.run(Map(
      "Dummy.cls" -> ""
    )) {
      root: PathLike =>
        val org = createOrg(root)
        val testSrc = "class Dummy { public Boolean func() {func()."
        assert(
          org
            .getCompletionItems(root.join("Dummy.cls").toString, line = 1, offset = testSrc.length, testSrc)
            .map(_.label) sameElements Array("addError(msg, escape)", "addError(msg, escape)", "addError(msg)", "addError(msg)"))
    }
  }



}
