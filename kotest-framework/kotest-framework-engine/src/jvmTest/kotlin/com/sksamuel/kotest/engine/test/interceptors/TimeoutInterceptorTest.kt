package com.sksamuel.kotest.engine.test.interceptors

import io.kotest.common.testTimeSource
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.annotation.enabledif.LinuxCondition
import io.kotest.core.descriptors.append
import io.kotest.core.names.TestNameBuilder
import io.kotest.core.source.sourceRef
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.core.test.TestType
import io.kotest.engine.descriptors.toDescriptor
import io.kotest.engine.test.interceptors.TimeoutInterceptor
import io.kotest.engine.test.scopes.NoopTestScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@EnabledIf(LinuxCondition::class)
class TimeoutInterceptorTest : FunSpec() {
   init {
      test("TimeoutInterceptor should return an error timeout") {

         val tc = TestCase(
            InvocationCountCheckInterceptorTest::class.toDescriptor().append("foo"),
            TestNameBuilder.builder("foo").build(),
            InvocationCountCheckInterceptorTest(),
            {},
            sourceRef(),
            TestType.Test,
         )

         TimeoutInterceptor(testTimeSource().markNow()).intercept(
            tc.copy(config = tc.config.copy(timeout = 1.milliseconds)),
            NoopTestScope(tc, coroutineContext)
         ) { _, _ ->
            delay(10000)
            TestResult.Success(0.milliseconds)
         }.isError shouldBe true
      }
   }
}
