package org.http4s
package rho.bits

import scala.language.existentials

import rho.{ RequestLineBuilder, QueryBuilder }

import shapeless.ops.hlist.Prepend
import shapeless.{ ::, HList }

import scala.reflect.runtime.universe.TypeTag

import org.http4s.rho.UriConvertible

/** Actual elements which build up the AST */
object PathAST {

  /** Typed shell for the Path operations of the DSL */
  case class TypedPath[T <: HList](rule: PathRule) extends UriConvertible {

    /** Match this rule and then `next` rule
      *
      * Alias of `&&`
      */
    def and[T2 <: HList](next: TypedPath[T2])(implicit prep: Prepend[T2, T]): TypedPath[prep.Out] =
      TypedPath(PathAnd(this.rule, next.rule))

    /** Match this rule and then `next` rule
      *
      * Alias of `and`
      */
    def &&[T2 <: HList](p2: TypedPath[T2])(implicit prep: Prepend[T2, T]): TypedPath[prep.Out] = and(p2)

    /** Match this rule or `alt` rule if this rule fails
      *
      * Alias of `||`
      */
    def or(alt: TypedPath[T]): TypedPath[T] = TypedPath(PathOr(this.rule, alt.rule))

    /** Match this rule or `alt` rule if this rule fails
      *
      * Alias of `or`
      */
    def ||(alt: TypedPath[T]): TypedPath[T] = or(alt)

    /** Match against a `String`
      *
      * @param segment `String` to match against.
      * @return a new [[TypedPath]] that will match against the provided `String`.
      */
    def /(segment: String): TypedPath[T] = TypedPath(PathAnd(this.rule, PathMatch(segment)))

    /** Capture a `String` from the path
      *
      * @param symbol `Symbol` representing the name of the segment to capture.
      * @return a new [[TypedPath]] that will capture a uri segment.
      */
    def /(symbol: Symbol): TypedPath[String :: T] = {
      val capture = PathCapture(symbol.name, None, StringParser.strParser, implicitly[TypeTag[String]])
      TypedPath(PathAnd(this.rule, capture))
    }

    /** Append the path rules to the [[TypedPath]]
      *
      * @param rules [[TypedPath]] rules to append to the path capture rules.
      * @return a new [[TypedPath]] that will execute the appended rules.
      */
    def /[T2 <: HList](rules: TypedPath[T2])(implicit prep: Prepend[T2, T]): TypedPath[prep.Out] =
      TypedPath(PathAnd(this.rule, rules.rule))

    /** Prepend this path rule to the [[RequestLineBuilder]]
      *
      * @param builder [[RequestLineBuilder]] rules to append to the path capture rules.
      * @return a new [[RequestLineBuilder]] with this rule prepended.
      */
    def /[T2 <: HList](builder: RequestLineBuilder[T2])(implicit prep: Prepend[T, T2]): RequestLineBuilder[prep.Out] =
      RequestLineBuilder(PathAnd(this.rule, builder.path), builder.rules)

    /** Capture a query rule
      *
      * @param query Query capture rule
      * @tparam T1 The types of elements captured by query.
      * @return A [[QueryBuilder]] with which to continue building the route.
      */
    def +?[T1 <: HList](query: TypedQuery[T1])(implicit prep: Prepend[T1, T]): RequestLineBuilder[prep.Out] =
      RequestLineBuilder(rule, query.rule)

    private val uriTemplate =
      for (p <- UriConverter.createPath(rule))
        yield UriTemplate(path = p)

    override def asUriTemplate(request: Request) =
      UriConvertible.respectPathInfo(uriTemplate, request)
  }

  /** The root type of the parser AST */
  sealed trait PathRule

  sealed trait PathRoute extends PathRule

  sealed trait PathOperation extends PathRule

  case class PathAnd(p1: PathRule, p2: PathRule) extends PathRoute

  case class PathOr(p1: PathRule, p2: PathRule) extends PathRoute

  case class PathMatch(s: String) extends PathOperation

  case class PathCapture(name: String, description: Option[String], parser: StringParser[_], m: TypeTag[_]) extends PathOperation

  case object CaptureTail extends PathOperation

  case class MetaCons(path: PathRule, meta: Metadata) extends PathOperation

}
