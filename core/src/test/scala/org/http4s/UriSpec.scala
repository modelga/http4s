package org.http4s

import org.specs2.matcher.MustThrownMatchers
import org.specs2.mutable.Specification
import util.CaseInsensitiveString._
import org.http4s.Uri._

import scalaz.Maybe

// TODO: this needs some more filling out
class UriSpec extends Http4sSpec with MustThrownMatchers {

  case class Ttl(seconds: Int)
  object Ttl {
    implicit val queryParamInstance = new QueryParamEncoder[Ttl] with QueryParam[Ttl] {
      def key: QueryParameterKey = QueryParameterKey("ttl")
      def encode(value: Ttl): QueryParameterValue = QueryParameterValue(value.seconds.toString)
    }
  }

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  "Uri" should {
    "Not UrlDecode the query String" in {
      getUri("http://localhost:8080/blah?x=abc&y=ijk").query should_== Query.fromPairs("x"->"abc", "y"->"ijk")
    }

    "Not UrlDecode the uri fragment" in {
      getUri("http://localhost:8080/blah#x=abc&y=ijk").fragment should_== Some("x=abc&y=ijk")
    }

    "decode the scheme" in {
      val uri = getUri("http://localhost/")
      uri.scheme should_== Some("http".ci)
    }

    "decode the authority" in {
      val uri1 = getUri("http://localhost/")
      uri1.authority.get.host should_== RegName("localhost")

      val uri2 = getUri("http://localhost")
      uri2.authority.get.host should_== RegName("localhost")

      val uri3 = getUri("/foo/bar")
      uri3.authority should_== None

      val auth = getUri("http://localhost:8080/").authority.get
      auth.host should_== RegName("localhost")
      auth.port should_== Some(8080)
    }

    "decode the port" in {
      val uri1 = getUri("http://localhost:8080/")
      uri1.port should_== Some(8080)

      val uri2 = getUri("http://localhost/")
      uri2.port should_== None
    }
  }

  "Uri's with a query and fragment" should {
    "parse propperly" in {
      val uri = getUri("http://localhost:8080/blah?x=abc#y=ijk")
      uri.query should_== Query.fromPairs("x"->"abc")
      uri.fragment should_== Some("y=ijk")
    }
  }

  "Uri Query decoding" should {

    def getQueryParams(uri: String): Map[String, String] = getUri(uri).params

    "Handle queries with no spaces properly" in {
      getQueryParams("http://localhost:8080/blah?x=abc&y=ijk") should_== Map("x" -> "abc", "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?") should_== Map("" -> "")
      getQueryParams("http://localhost:8080/blah") should_== Map.empty
    }

    "Handle queries with spaces properly" in {
      // Issue #75
      getQueryParams("http://localhost:8080/blah?x=a+bc&y=ijk") should_== Map("x" -> "a bc", "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?x=a%20bc&y=ijk") should_== Map("x" -> "a bc", "y" -> "ijk")
    }

  }

  "Uri to String" should {

    "render default URI" in {
      Uri().toString must be_==("/")
    }

    "render a IPv6 address, should be wrapped in brackets" in {
      val variants = "01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab" +: (for {
        h <- 0 to 7
        l <- 0 to 7 - h
        f = List.fill(h)("01ab").mkString(":")
        b = List.fill(l)("32ba").mkString(":")
      } yield (f + "::" + b))

      foreach (variants) { s =>
        Uri(Some("http".ci), Some(Authority(host = IPv6(s.ci))), "/foo", Query.fromPairs("bar" -> "baz")).toString must_==
          (s"http://[$s]/foo?bar=baz")
      }
    }

    "render URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci))), "/foo", Query.fromPairs("bar" -> "baz")).toString must_==("http://www.foo.com/foo?bar=baz")
    }

    "render URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci), port = Some(80)))).toString must_==("http://www.foo.com:80")
    }

    "render URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = RegName("www.foo.com".ci)))).toString must_==("http://www.foo.com")
    }

    "render IPv4 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(80))), "/c", Query.fromPairs("GB"->"object","Class"->"one")).toString must_==("http://192.168.1.1:80/c?GB=object&Class=one")
    }

    "render IPv4 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci), port = Some(8080)))).toString must_==("http://192.168.1.1:8080")
    }

    "render IPv4 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv4("192.168.1.1".ci)))).toString must_==("http://192.168.1.1")
    }

    "render IPv6 URL with parameters" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:db8::7".ci))), "/c", Query.fromPairs("GB"->"object","Class"->"one")).toString must_==("http://[2001:db8::7]/c?GB=object&Class=one")
    }

    "render IPv6 URL with port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci), port = Some(8080)))).toString must_==("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080")
    }

    "render IPv6 URL without port" in {
      Uri(Some("http".ci), Some(Authority(host = IPv6("2001:0db8:85a3:08d3:1319:8a2e:0370:7344".ci)))).toString must_==("http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]")
    }

    "render email address" in {
      Uri(Some("mailto".ci), path = "John.Doe@example.com").toString must_==("mailto:John.Doe@example.com")
    }

    "render an URL with username and password" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/", Query.empty, None).toString must_==("http://username:password@some.example.com")
    }

    "render an URL with username and password, path and params" in {
      Uri(Some("http".ci), Some(Authority(Some("username:password"), RegName("some.example.com"), None)), "/some/path", Query.fromString("param1=5&param-without-value"), None).toString  must_==("http://username:password@some.example.com/some/path?param1=5&param-without-value")
    }

    "render relative URI with empty query string" in {
      Uri(path = "/", query = Query.fromString(""), fragment = None).toString must_==("/?")
    }

    "render relative URI with empty query string and fragment" in {
      Uri(path = "/", query = Query.fromString(""), fragment = Some("")).toString must_==("/?#")
    }

    "render relative URI with empty fragment" in {
      Uri(path = "/", query = Query.empty, fragment = Some("")).toString must_== ("/#")
    }

    "render relative path with fragment" in {
      Uri(path = "/foo/bar", fragment = Some("an-anchor")).toString must_==("/foo/bar#an-anchor")
    }

    "render relative path with parameters" in {
      Uri(path = "/foo/bar", query = Query.fromString("foo=bar&ding=dong")).toString must_==("/foo/bar?foo=bar&ding=dong")
    }

    "render relative path with parameters and fragment" in {
      Uri(path = "/foo/bar", query = Query.fromString("foo=bar&ding=dong"), fragment = Some("an_anchor")).toString must_==("/foo/bar?foo=bar&ding=dong#an_anchor")
    }

    "render relative path without parameters" in {
      Uri(path = "/foo/bar").toString must_==("/foo/bar")
    }

    "render relative root path without parameters" in {
      Uri(path = "/").toString must_==("/")
    }

    "render a query string with a single param" in {
      Uri(query = Query.fromString("param1=test")).toString must_==("/?param1=test")
    }

    "render a query string with multiple value in a param" in {
      Uri(query = Query.fromString("param1=3&param2=2&param2=foo")).toString must_==("/?param1=3&param2=2&param2=foo")
    }

    "round trip over URI examples from wikipedia" in {
      /*
       * Examples from:
       * - http://de.wikipedia.org/wiki/Uniform_Resource_Identifier
       * - http://en.wikipedia.org/wiki/Uniform_Resource_Identifier
       *
       * URI.fromString fails for:
       * - "http://en.wikipedia.org/wiki/URI#Examples_of_URI_references",
       * - "file:///C:/Users/Benutzer/Desktop/Uniform%20Resource%20Identifier.html",
       * - "file:///etc/fstab",
       * - "relative/path/to/resource.txt",
       * - "//example.org/scheme-relative/URI/with/absolute/path/to/resource.txt",
       * - "../../../resource.txt",
       * - "./resource.txt#frag01",
       * - "resource.txt",
       * - "#frag01",
       * - ""
       *
       */
      val examples = Seq(
        "http://de.wikipedia.org/wiki/Uniform_Resource_Identifier",
        "ftp://ftp.is.co.za/rfc/rfc1808.txt",
        "geo:48.33,14.122;u=22.5",
        "ldap://[2001:db8::7]/c=GB?objectClass?one",
        "gopher://gopher.floodgap.com",
        "mailto:John.Doe@example.com",
        "sip:911@pbx.mycompany.com",
        "news:comp.infosystems.www.servers.unix",
        "data:text/plain;charset=iso-8859-7,%be%fa%be",
        "tel:+1-816-555-1212",
        "telnet://192.0.2.16:80",
        "urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
        "git://github.com/rails/rails.git",
        "crid://broadcaster.com/movies/BestActionMovieEver",
        "http://example.org/absolute/URI/with/absolute/path/to/resource.txt",
        "/relative/URI/with/absolute/path/to/resource.txt")
      foreach (examples) { e =>
        Uri.fromString(e) must beRightDisjunction.like { case u => u.toString must be_==(e) }
      }
    }

  }

  "Uri parameters" should {
    "parse empty query string" in {
      Uri(query = Query.fromString("")).multiParams must be_==(Map("" -> Nil))
    }
    "parse parameter without key but with empty value" in {
      Uri(query = Query.fromString("=")).multiParams must be_==(Map("" -> List("")))
    }
    "parse parameter without key but with value" in {
      Uri(query = Query.fromString("=value")).multiParams must be_==(Map("" -> List("value")))
    }
    "parse single parameter with empty value" in {
      Uri(query = Query.fromString("param1=")).multiParams must be_==(Map("param1" -> List("")))
    }
    "parse single parameter with value" in {
      Uri(query = Query.fromString("param1=value")).multiParams must be_==(Map("param1" -> List("value")))
    }
    "parse single parameter without value" in {
      Uri(query = Query.fromString("param1")).multiParams must be_==(Map("param1" -> Nil))
    }
    "parse many parameter with value" in {
      Uri(query = Query.fromString("param1=value&param2=value1&param2=value2&param3=value")).multiParams must_==(Map(
        "param1" -> List("value"),
        "param2" -> List("value1", "value2"),
        "param3" -> List("value")))
    }
    "parse many parameter without value" in {
      Uri(query = Query.fromString("param1&param2&param3")).multiParams must_==(Map(
        "param1" -> Nil,
        "param2" -> Nil,
        "param3" -> Nil))
    }
  }

  "Uri.params.+" should {
    "add parameter to empty query" in {
      val i = Uri(query = Query.empty).params + (("param", Seq("value")))
      i must be_==(Map("param" -> Seq("value")))
    }
    "add parameter" in {
      val i = Uri(query = Query.fromString("param1")).params + (("param2", Seq()))
      i must be_==(Map("param1" -> Seq(), "param2" -> Seq()))
    }
    "replace an existing parameter" in {
      val i = Uri(query = Query.fromString("param=value")).params + (("param", Seq("value1", "value2")))
      i must be_==(Map("param" -> Seq("value1", "value2")))
    }
    "replace an existing parameter with empty value" in {
      val i = Uri(query = Query.fromString("param=value")).params + (("param", Seq()))
      i must be_==(Map("param" -> Seq()))
    }
  }

  "Uri.params.-" should {
    "not do anything on an URI without a query" in {
      val i = Uri(query = Query.empty).params - "param"
      i must be_==(Map())
    }
    "not reduce a map if parameter does not match" in {
      val i = Uri(query = Query.fromString("param1")).params - "param2"
      i must be_==(Map("param1" -> ""))
    }
    "reduce a map if matching parameter found" in {
      val i = Uri(query = Query.fromString("param")).params - "param"
      i must be_==(Map())
    }
  }

  "Uri.params.iterate" should {
    "work on an URI without a query" in {
      foreach (Uri(query = Query.empty).params.iterator) { i =>
        throw new Error(s"should not have $i") // should not happen
      }
    }
    "work on empty list" in {
      foreach (Uri(query = Query.fromString("")).params.iterator) { case (k,v) =>
        k must_== ""
        v must_== ""
      }
    }
    "work with empty keys" in {
      val u = Uri(query = Query.fromString("=value1&=value2&=&"))
      val i = u.params.iterator
      i.next must be_==("" -> "value1")
      i.next must throwA [NoSuchElementException]
    }
    "work on non-empty query string" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      val i = u.params.iterator
      i.next must be_==("param1" -> "value1")
      i.next must be_==("param2" -> "value4")
      i.next must throwA [NoSuchElementException]
    }
  }

  "Uri.multiParams" should {
    "find first value of parameter with many values" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.multiParams must be_==(
        Map(
          "param1" -> Seq("value1", "value2", "value3"),
          "param2" -> Seq("value4", "value5")))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Query.fromString("param1=&=value-of-empty-key&param2=value"))
      u.multiParams must be_==(
        Map(
          "" -> Seq("value-of-empty-key"),
          "param1" -> Seq(""),
          "param2" -> Seq("value")))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Query.fromString("=value1&=value2")).multiParams must_== (
        Map("" -> Seq("value1", "value2")))
      Uri(query = Query.fromString("&=value1&=value2")).multiParams must_== (
        Map("" -> Seq("value1", "value2")))
      Uri(query = Query.fromString("&&&=value1&&&=value2&=&")).multiParams must_== (
        Map("" -> Seq("value1", "value2", "")))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Query.fromString("&")).multiParams must_==(Map("" -> Seq()))
      Uri(query = Query.fromString("&&")).multiParams must_==(Map("" -> Seq()))
      Uri(query = Query.fromString("&&&")).multiParams must_==(Map("" -> Seq()))
    }
    "find parameter with an empty value" in {
      Uri(query = Query.fromString("param1=")).multiParams must_==(Map("param1" -> Seq("")))
      Uri(query = Query.fromString("param1=&param2=")).multiParams must_== (Map("param1" -> Seq(""), "param2" -> Seq("")))
    }
    "find parameter with single value" in {
      Uri(query = Query.fromString("param1=value1&param2=value2")).multiParams must_==(
        Map(
          "param1" -> Seq("value1"),
          "param2" -> Seq("value2")))
    }
    "find parameter without value" in {
      Uri(query = Query.fromString("param1&param2&param3")).multiParams must_==(
        Map(
          "param1" -> Seq(),
          "param2" -> Seq(),
          "param3" -> Seq()))
    }
  }

  "Uri.params.get" should {
    "find first value of parameter with many values" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param1=value3&param2=value4&param2=value5"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value4"))
    }
    "find parameter with empty key and a value" in {
      val u = Uri(query = Query.fromString("param1=&=valueWithEmptyKey&param2=value2"))
      u.params.get("") must be_==(Some("valueWithEmptyKey"))
    }
    "find first value of parameter with empty key" in {
      Uri(query = Query.fromString("=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Query.fromString("&=value1&=value2")).params.get("") must be_==(Some("value1"))
      Uri(query = Query.fromString("&&&=value1")).params.get("") must be_==(Some("value1"))
    }
    "find parameter with empty key and without value" in {
      Uri(query = Query.fromString("&")).params.get("") must be_==(None)
      Uri(query = Query.fromString("&&")).params.get("") must be_==(None)
      Uri(query = Query.fromString("&&&")).params.get("") must be_==(None)
    }
    "find parameter with an empty value" in {
      val u = Uri(query = Query.fromString("param1=&param2=value2"))
      u.params.get("param1") must be_==(Some(""))
    }
    "find parameter with single value" in {
      val u = Uri(query = Query.fromString("param1=value1&param2=value2"))
      u.params.get("param1") must be_==(Some("value1"))
      u.params.get("param2") must be_==(Some("value2"))
    }
    "find parameter without value" in {
      val u = Uri(query = Query.fromString("param1&param2&param3"))
      u.params.get("param1") must be_==(None)
      u.params.get("param2") must be_==(None)
      u.params.get("param3") must be_==(None)
    }
    "not find an unknown parameter" in {
      Uri(query = Query.fromString("param1&param2&param3")).params.get("param4") must be_==(None)
    }
    "not find anything if query string is empty" in {
      Uri(query = Query.empty).params.get("param1") must be_==(None)
    }
  }

  "Uri parameter convenience methods" should {
    "add a parameter if no query is available" in {
      val u = Uri(query = Query.empty) +? ("param1", "value")
      u must be_==(Uri(query = Query.fromString("param1=value")))
    }
    "add a parameter" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2", "value")
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2=value")))
    }
    "add a parameter with boolean value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2", true)
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2=true")))
    }
    "add a parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param2")
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2")))
    }
    "add a parameter with many values" in {
      val u = Uri() +? ("param1", Seq("value1", "value2"))
      u must be_==(Uri(query = Query.fromString("param1=value1&param1=value2")))
    }
    "add a parameter with many long values" in {
      val u = Uri() +? ("param1", Seq(1L, -1L))
      u must be_==(Uri(query = Query.fromString(s"param1=1&param1=-1")))
    }
    "add a query parameter with a QueryParamEncoder" in {
      val u = Uri() +? ("test", Ttl(2))
      u must be_==(Uri(query = Query.fromString(s"test=2")))
    }
    "add a query parameter with a QueryParamEncoder and an implicit key" in {
      val u = Uri() +*? (Ttl(2))
      u must be_==(Uri(query = Query.fromString(s"ttl=2")))
    }
    "Work with queryParam" in {
      val u = Uri().withQueryParam[Ttl]
      u must be_==(Uri(query = Query.fromString(s"ttl")))
    }
    "add an optional query parameter (Just)" in {
      val u = Uri() +?? ("param1", Maybe.just(2))
      u must be_==(Uri(query = Query.fromString(s"param1=2")))
    }
    "add an optional query parameter (Empty)" in {
      val u = Uri() +?? ("param1", Maybe.empty[Int])
      u must be_==(Uri(query = Query.empty))
    }
    "contains not a parameter" in {
      Uri(query = Query.empty) ? "param1" must be_==(false)
    }
    "contains an empty parameter" in {
      Uri(query = Query.fromString("")) ? "" must be_==(true)
      Uri(query = Query.fromString("")) ? "param" must be_==(false)
      Uri(query = Query.fromString("&&=value&&")) ? "" must be_==(true)
      Uri(query = Query.fromString("&&=value&&")) ? "param" must be_==(false)
    }
    "contains a parameter" in {
      Uri(query = Query.fromString("param1=value&param1=value")) ? "param1" must be_==(true)
      Uri(query = Query.fromString("param1=value&param2=value")) ? "param2" must be_==(true)
      Uri(query = Query.fromString("param1=value&param2=value")) ? "param3" must be_==(false)
    }
    "contains a parameter with many values" in {
      Uri(query = Query.fromString("param1=value1&param1=value2&param1=value3")) ? "param1" must be_==(true)
    }
    "contains a parameter without a value" in {
      Uri(query = Query.fromString("param1")) ? "param1" must be_==(true)
    }
    "contains with many parameters" in {
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param1" must be_==(true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param2" must be_==(true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "" must be_==(true)
      Uri(query = Query.fromString("param1=value1&param1=value2&param2&=value3")) ? "param3" must be_==(false)
    }
    "remove a parameter if present" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value")) -? ("param1")
      u must be_==(Uri(query = Query.fromString("param2=value")))
    }
    "remove an empty parameter from an empty query string" in {
      val u = Uri(query = Query.fromString("")) -? ("")
      u must be_==(Uri(query = Query.empty))
    }
    "remove nothing if parameter is not present" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value"))
      u -? ("param3") must be_==(u)
    }
    "remove the last parameter" in {
      val u = Uri(query = Query.fromString("param1=value")) -? ("param1")
      u must be_==(Uri())
    }
    "replace a parameter" in {
      val u = Uri(query = Query.fromString("param1=value&param2=value")) +? ("param1", "newValue")
      u.multiParams must be_==(Uri(query = Query.fromString("param1=newValue&param2=value")).multiParams)
    }
    "replace a parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2=value")) +? ("param2")
      u.multiParams must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace the same parameter" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2")) +? ("param1", Seq("value1", "value2"))
      u.multiParams must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace the same parameter without a value" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2&param2")) +? ("param2")
      u.multiParams must be_==(Uri(query = Query.fromString("param1=value1&param1=value2&param2")).multiParams)
    }
    "replace a parameter set" in {
      val u = Uri(query = Query.fromString("param1=value1&param1=value2")) +? ("param1", "value")
      u.multiParams must be_==(Uri(query = Query.fromString("param1=value")).multiParams)
    }
    "set a parameter with a value" in {
      val ps = Map("param" -> List("value"))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=value")))
    }
    "set a parameter with a boolean values" in {
      val ps = Map("param" -> List(true, false))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=true&param=false")))
    }
    "set a parameter with a char values" in {
      val ps = Map("param" -> List('x', 'y'))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=x&param=y")))
    }
    "set a parameter with a double values" in {
      val ps = Map("param" -> List(1.2, 2.1))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1.2&param=2.1")))
    }
    "set a parameter with a float values" in {
      val ps = Map("param" -> List(1.2F, 2.1F))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1.2&param=2.1")))
    }
    "set a parameter with a integer values" in {
      val ps = Map("param" -> List(1, 2, 3))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=1&param=2&param=3")))
    }
    "set a parameter with a long values" in {
      val ps = Map("param" -> List(Long.MaxValue, 0L, Long.MinValue))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=9223372036854775807&param=0&param=-9223372036854775808")))
    }
    "set a parameter with a short values" in {
      val ps = Map("param" -> List(Short.MaxValue, Short.MinValue))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=32767&param=-32768")))
    }
    "set a parameter with a string values" in {
      val ps = Map("param" -> List("some", "none"))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param=some&param=none")))
    }
    "set a parameter without a value" in {
      val ps: Map[String, List[String]] = Map("param" -> Nil)
      Uri() =? ps must be_==(Uri(query = Query.fromString("param")))
    }
    "set many parameters" in {
      val ps = Map("param1" -> Nil, "param2" -> List("value1", "value2"), "param3" -> List("value"))
      Uri() =? ps must be_==(Uri(query = Query.fromString("param1&param2=value1&param2=value2&param3=value")))
    }
    "set the same parameters again" in {
      val ps = Map("param" -> List("value"))
      val u = Uri(query = Query.fromString("param=value"))
      u =? ps must be_==(u =? ps)
    }
  }
}