package org.extendedmind.test

import org.scalatest.FunSpec
import java.util.UUID

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AdHocSpec extends FunSpec{
  describe("Regular expressions in Scala/Java"){
    it("should work with negative lookahead"){
    	val pattern = "^(?!(/api/|/static/)).+$".r
    	assert((pattern replaceAllIn("/api/", "/index.html")) === "/api/")
    	assert((pattern replaceAllIn("/api/authenticate", "/index.html")) == "/api/authenticate")
    	assert((pattern replaceAllIn("/my", "/index.html")) === "/index.html")
    	assert((pattern replaceAllIn("/something/api/", "/index.html")) === "/index.html")
    	assert((pattern replaceAllIn("/api", "/index.html")) === "/index.html")
    	assert((pattern replaceAllIn("/static/", "/index.html")) === "/static/")
    }
  }
}