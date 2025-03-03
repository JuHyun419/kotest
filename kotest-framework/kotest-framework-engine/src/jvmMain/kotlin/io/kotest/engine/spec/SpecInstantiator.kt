package io.kotest.engine.spec

import io.kotest.core.config.ExtensionRegistry
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.extensions.Extension
import io.kotest.core.extensions.PostInstantiationExtension
import io.kotest.core.spec.Spec
import io.kotest.engine.instantiateOrObject
import io.kotest.engine.mapError
import io.kotest.mpp.annotation
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf

/**
 * Creates an instance of a [Spec].
 *
 * Firstly, by delegating to any [ConstructorExtension]s then with
 * a fallback to a reflection based zero-args constructor.
 *
 * If the reference represents an object, then the singleton object instance will be returned.
 *
 * After instantiation any [PostInstantiationExtension]s will be invoked.
 */
class SpecInstantiator(private val registry: ExtensionRegistry) {

   suspend fun <T : Spec> createAndInitializeSpec(
      kclass: KClass<T>,
   ): Result<Spec> {
      return objectOrInstantiateClass(kclass)
         .onSuccess { spec ->
            spec.globalExtensions().forEach { registry.add(it) }
         }
   }

   private suspend fun objectOrInstantiateClass(kclass: KClass<out Spec>): Result<Spec> {
      val obj = kclass.objectInstance
      return if (obj != null) Result.success(obj) else instantiate(kclass)
   }

   private suspend fun instantiate(kclass: KClass<out Spec>): Result<Spec> {
      return runCatching {
         val initial: Spec? = null

         val constructorExtensions = constructorExtensions(registry, kclass)
         val spec = constructorExtensions
            .fold(initial) { spec, ext -> spec ?: ext.instantiate(kclass) }
            ?: instantiateOrObject(kclass)
               .mapError { SpecInstantiationException("Could not create instance of $kclass", it) }
               .getOrThrow()

         postInstantiationExtensions(registry, kclass)
            .fold(spec) { acc, ext -> ext.instantiated(acc) }
      }
   }

   /**
    * Returns any Extensions of type T registered via @ApplyExtension on the spec.
    */
   private inline fun <reified T : Extension> extensionsFromApplyExtension(kclass: KClass<*>): List<T> {
      val components = kclass.annotation<ApplyExtension>()?.extensions ?: return emptyList()
      return components.filter { it.isSubclassOf(T::class) }.map { (it.objectInstance ?: it.createInstance()) as T }
   }

   private fun constructorExtensions(
      registry: ExtensionRegistry,
      kclass: KClass<*>
   ): List<ConstructorExtension> {
      return registry.all().filterIsInstance<ConstructorExtension>() +
         extensionsFromApplyExtension<ConstructorExtension>(kclass)
   }

   private fun postInstantiationExtensions(
      registry: ExtensionRegistry,
      kclass: KClass<*>
   ): List<PostInstantiationExtension> {
      return registry.all().filterIsInstance<PostInstantiationExtension>() +
         extensionsFromApplyExtension<PostInstantiationExtension>(kclass)
   }
}

class SpecInstantiationException(msg: String, t: Throwable?) : RuntimeException(msg, t)
