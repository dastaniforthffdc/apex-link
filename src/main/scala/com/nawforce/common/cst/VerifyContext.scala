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
package com.nawforce.common.cst

import com.nawforce.common.api.Org
import com.nawforce.common.documents.Location
import com.nawforce.common.finding.{TypeError, TypeRequest}
import com.nawforce.common.metadata.{Dependent, DependencyHolder}
import com.nawforce.common.names.{EncodedName, Name, TypeName}
import com.nawforce.common.types._
import com.nawforce.common.types.apex.{ApexDeclaration, TriggerDeclaration}

import scala.collection.mutable

trait VerifyContext {
  def parent(): Option[VerifyContext]

  /* Package for current outer type */
  def pkg: PackageDeclaration

  /* Get type declaration of 'this', option as not set in trigger */
  def thisType: Option[TypeDeclaration]

  /* Get type declaration of 'super' */
  def superType: Option[TypeDeclaration]

  /* Get closest dependency holder */
  def holder: DependencyHolder

  /* Declare a dependency on dependent */
  def addDependency(dependent: Dependent): Unit

  /* Locate a type, typeName may be relative so searching must be performed wrt a typeDeclaration */
  def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                 excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration]

  /* Helper to locate a relative or absolute type and add as dependency if found */
  def getTypeAndAddDependency(typeName: TypeName, from: Option[TypeDeclaration],
                              excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration]

  def suppressWarnings: Boolean = parent().exists(_.suppressWarnings)

  def missingType(location: Location, typeName: TypeName): Unit = {
    if (!pkg.isGhostedType(typeName))
      logMessage(location, s"No type declaration found for '$typeName'")
  }

  def missingIdentifier(location: Location, typeName: TypeName, name: Name): Unit = {
    if (!pkg.isGhostedType(EncodedName(name).asTypeName))
      logMessage(location, s"No variable or type found for '$name' on '$typeName'")
  }

  def logMessage(location: Location, msg: String): Unit = {
    if (!suppressWarnings)
      Org.logMessage(location, msg)
  }
}

trait HolderVerifyContext {
  private val _dependencies = mutable.Set[Dependent]()

  def dependencies: Set[Dependent] = _dependencies.toSet

  /* Locate a type, typeName may be relative so searching must be performed wrt a typeDeclaration */
  def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                 excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration]

  def addDependency(dependent: Dependent): Unit = {
    dependent match {
      case _: ApexDeclaration => _dependencies += dependent
      case _: ApexFieldDeclaration => _dependencies += dependent
      case _: ApexPropertyDeclaration => _dependencies += dependent
      case _: ApexMethodDeclaration => _dependencies += dependent
      case _ => ()
    }
  }

  def getTypeAndAddDependency(typeName: TypeName, from: Option[TypeDeclaration],
                              excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    val result = getTypeFor(typeName, from, excludeSObjects)
    result.foreach(addDependency)
    result
  }
}

class TriggerVerifyContext(packageDeclaration: PackageDeclaration, typeDeclaration: TriggerDeclaration)
  extends HolderVerifyContext with VerifyContext {

  override def parent(): Option[VerifyContext] = None

  override def pkg: PackageDeclaration = packageDeclaration

  override def thisType: Option[TypeDeclaration] = Some(typeDeclaration)

  override def superType: Option[TypeDeclaration] = typeDeclaration.superClassDeclaration

  override def holder: DependencyHolder = typeDeclaration

  override def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                          excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    TypeRequest(typeName, from, thisType.flatMap(_.packageDeclaration), excludeSObjects)
  }

  override def suppressWarnings: Boolean =
    typeDeclaration.modifiers.contains(SUPPRESS_WARNINGS_ANNOTATION) || parent().exists(_.suppressWarnings)
}


class TypeVerifyContext(parentContext: Option[VerifyContext], typeDeclaration: ApexDeclaration)
    extends HolderVerifyContext with VerifyContext {

  override def parent(): Option[VerifyContext] = parentContext

  override def pkg: PackageDeclaration = typeDeclaration.pkg

  override def thisType: Option[TypeDeclaration] = Some(typeDeclaration)

  override def superType: Option[TypeDeclaration] = typeDeclaration.superClassDeclaration

  override def holder: DependencyHolder = typeDeclaration

  override def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                          excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    TypeRequest(typeName, from, thisType.flatMap(_.packageDeclaration), excludeSObjects)
  }

  override def suppressWarnings: Boolean =
    typeDeclaration.modifiers.contains(SUPPRESS_WARNINGS_ANNOTATION) || parent().exists(_.suppressWarnings)
}

class BodyDeclarationVerifyContext(parentContext: TypeVerifyContext, classBodyDeclaration: ClassBodyDeclaration)
  extends HolderVerifyContext with VerifyContext {

  override def parent(): Option[VerifyContext] = Some(parentContext)

  override def pkg: PackageDeclaration = parentContext.pkg

  override def thisType: Option[TypeDeclaration] = parentContext.thisType

  override def superType: Option[TypeDeclaration] = parentContext.superType

  override def holder: DependencyHolder = classBodyDeclaration

  override def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                          excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    parentContext.getTypeFor(typeName, from, excludeSObjects)
  }

  override def suppressWarnings: Boolean =
    classBodyDeclaration.modifiers.contains(SUPPRESS_WARNINGS_ANNOTATION) || parent().exists(_.suppressWarnings)
}

abstract class BlockVerifyContext(parentContext: VerifyContext)
  extends VerifyContext {

  private val vars = mutable.Map[Name, TypeDeclaration]()

  override def parent(): Option[VerifyContext] = Some(parentContext)

  override def pkg: PackageDeclaration = parentContext.pkg

  override def thisType: Option[TypeDeclaration] = parentContext.thisType

  override def superType: Option[TypeDeclaration] = parentContext.superType

  override def holder: DependencyHolder = parentContext.holder

  override def addDependency(dependent: Dependent): Unit = parentContext.addDependency(dependent)

  override def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                          excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    parentContext.getTypeFor(typeName, from, excludeSObjects)
  }

  override def getTypeAndAddDependency(typeName: TypeName, from: Option[TypeDeclaration],
                                       excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    parentContext.getTypeAndAddDependency(typeName, from, excludeSObjects)
  }

  def isVar(name: Name): Option[TypeDeclaration] = {
    vars.get(name)
  }

  def addVar(name: Name, typeDeclaration: TypeDeclaration): Unit = {
    vars.put(name, typeDeclaration)
  }

  def addVar(name: Name, location: Location, typeName: TypeName): Unit = {
    val td = getTypeAndAddDependency(typeName, thisType).toOption
    if (td.isEmpty)
      missingType(location, typeName)

    vars.put(name, td.getOrElse(pkg.any()))
  }

  def isStatic: Boolean
}

class OuterBlockVerifyContext(parentContext: VerifyContext, isStaticContext: Boolean)
  extends BlockVerifyContext(parentContext) {

  assert(!parentContext.isInstanceOf[BlockVerifyContext])

  override val isStatic: Boolean = isStaticContext
}

class InnerBlockVerifyContext(parentContext: BlockVerifyContext)
  extends BlockVerifyContext(parentContext) {

  override def isStatic: Boolean = parentContext.isStatic

  override def isVar(name: Name): Option[TypeDeclaration] = {
    super.isVar(name).orElse(parentContext.isVar(name))
  }
}

class ExpressionVerifyContext(parentContext: BlockVerifyContext)
  extends VerifyContext {

  override def parent(): Option[VerifyContext] = Some(parentContext)

  override def pkg: PackageDeclaration = parentContext.pkg

  override def thisType: Option[TypeDeclaration] = parentContext.thisType

  override def superType: Option[TypeDeclaration] = parentContext.superType

  override def holder: DependencyHolder = parentContext.holder

  override def addDependency(dependent: Dependent): Unit = parentContext.addDependency(dependent)

  override def getTypeFor(typeName: TypeName, from: Option[TypeDeclaration],
                          excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    parentContext.getTypeFor(typeName, from, excludeSObjects)
  }

  override def getTypeAndAddDependency(typeName: TypeName, from: Option[TypeDeclaration],
                                       excludeSObjects: Boolean = false): Either[TypeError, TypeDeclaration] = {
    parentContext.getTypeAndAddDependency(typeName, from, excludeSObjects)
  }

  def isVar(name: Name): Option[TypeDeclaration] = parentContext.isVar(name)

  def defaultNamespace(name: Name): Name = {
    EncodedName(name).defaultNamespace(pkg.namespace).fullName
  }
}
