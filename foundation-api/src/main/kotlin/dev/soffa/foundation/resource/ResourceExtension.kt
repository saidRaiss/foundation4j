package dev.soffa.foundation.resource

import dev.soffa.foundation.core.Operation
import kotlin.reflect.KClass


fun <I, O, T : Operation<I, O>> Resource.invoke(clazz: KClass<T>, input: I): O {
    return this.invoke(clazz.java, input)
}
