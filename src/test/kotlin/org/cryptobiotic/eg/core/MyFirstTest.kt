package org.cryptobiotic.eg.core

import io.kotest.core.spec.style.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class MyFirstTestClass : FunSpec({

    test("my first test") {
        1 + 2 shouldBe 3
    }

})

class NestedTestExamples : DescribeSpec({

    describe("an outer test") {

        it("an inner test") {
            1 + 2 shouldBe 3
        }

        it("an inner test too!") {
            3 + 4 shouldBe 7
        }
    }

})

class DynamicTests : FunSpec({

    listOf(
        "sam",
        "pam",
        "tim",
    ).forEach {
        test("$it should be a three letter name") {
            it.shouldHaveLength(3)
        }
    }
})

class AnnotationSpecExample : AnnotationSpec() {

    @BeforeEach
    fun beforeTest() {
        println("Before each test")
    }

    @Test
    fun test1() {
        1 shouldBe 1
    }

    @Test
    fun test2() {
        3 shouldBe 3
    }
}

class MyTests : ShouldSpec({
    context("String.length") {
        should("return the length of the string") {
            "sammy".length shouldBe 5
            "".length shouldBe 0
        }
    }
})



