package chess
package format.pgn

import scala.util.parsing.combinator._

object Parser {

  type TagType = (List[Tag], String)

  def apply(pgn: String): Valid[ParsedPgn] = for {
    parsed ← TagParser(pgn)
    (tags, moveString) = parsed
    sans ← MoveParser(moveString)
  } yield ParsedPgn(tags, sans)

  object TagParser extends RegexParsers {

    def apply(pgn: String): Valid[TagType] = parseAll(all, pgn) match {
      case f: Failure       ⇒ "Cannot parse tags: %s\n%s".format(f.toString, pgn).failNel
      case Success(sans, _) ⇒ scalaz.Scalaz.success(sans)
    }

    def all: Parser[TagType] = tags ~ """(.|\n)+""".r ^^ {
      case ts ~ ms ⇒ (ts, ms)
    }

    def tags: Parser[List[Tag]] = rep(tag)

    def tag: Parser[Tag] = tagName ~ tagValue ^^ {
      case name ~ value  ⇒ Tag(name, value)
    }

    val tagName: Parser[String] = "[" ~> """[a-zA-Z]+""".r

    val tagValue: Parser[String] = "\"" ~> """[^"]+""".r <~ "\"]"
  }

  object MoveParser extends RegexParsers {

    val CRLF = "\r\n" | "\n"
    val space = """\s+""".r

    override val whiteSpace = "".r

    def apply(pgn: String): Valid[List[San]] =
      parseAll(moves, (pgn.lines mkString " ")) match {
        case f: Failure       ⇒ "Cannot parse moves: %s\n%s".format(f.toString, pgn).failNel
        case Success(sans, _) ⇒ scalaz.Scalaz.success(sans)
      }

    def moves: Parser[List[San]] = repsep(move, space) <~ (result?)

    val result: Parser[String] = space ~> ("*" | "1/2-1/2" | "0-1" | "1-0")

    def move: Parser[San] =
      (number?) ~> (castle | standard) <~ (comment?)

    val comment: Parser[String] = space ~> "{" ~> """[^\}]+""".r <~ "}"

    def castle = (qCastle | kCastle) ~ suffixes ^^ {
      case side ~ suf ⇒ Castle(side) withSuffixes suf
    }

    val qCastle: Parser[Side] = "O-O-O" ^^^ QueenSide

    val kCastle: Parser[Side] = "O-O" ^^^ KingSide

    def standard: Parser[Std] = (complete | simple | disambiguated) ~ suffixes ^^ {
      case std ~ suf ⇒ std withSuffixes suf
    }

    val number: Parser[String] = """\d+\.+""".r <~ space

    def simple: Parser[Std] = role ~ x ~ dest ^^ {
      case ro ~ ca ~ de ⇒ Std(dest = de, role = ro, capture = ca)
    }

    def disambiguated: Parser[Std] = role ~ opt(file) ~ opt(rank) ~ x ~ dest ^^ {
      case ro ~ fi ~ ra ~ ca ~ de ⇒ Std(
        dest = de, role = ro, capture = ca, file = fi, rank = ra)
    }

    def complete: Parser[Std] = role ~ file ~ rank ~ x ~ dest ^^ {
      case ro ~ fi ~ ra ~ ca ~ de ⇒ Std(
        dest = de, role = ro, capture = ca, file = fi.some, rank = ra.some)
    }

    def suffixes: Parser[Suffixes] = opt(promotion) ~ check ~ checkmate ^^ {
      case p ~ c ~ cm ⇒ Suffixes(c, cm, p)
    }

    val x = exists("x")

    val check = exists("+")

    val checkmate = exists("#")

    val role = mapParser(Role.allByPgn, "role") | success(Pawn)

    val file = mapParser(rangeToMap('a' to 'h'), "file")

    val rank = mapParser(rangeToMap('1' to '8'), "rank")

    val promotion = "=" ~> mapParser(promotable, "promotion")

    val promotable = Role.allPromotableByPgn mapKeys (_.toUpper)

    val dest = mapParser(Pos.allKeys, "dest")

    def exists(c: String): Parser[Boolean] = c ^^^ true | success(false)

    def rangeToMap(r: Iterable[Char]) = r.zipWithIndex.toMap mapValues (_ + 1)

    def mapParser[A, B](map: Map[A, B], name: String): Parser[B] =
      map.foldLeft(failure(name + " not found"): Parser[B]) {
        case (acc, (a, b)) ⇒ a.toString ^^^ b | acc
      }
  }
}
