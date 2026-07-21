package com.twocircle.bike.common.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the region-manifest URL string.
 *
 * Lives in :core:common (not :app) so feature modules can reference it without depending
 * on :app — :app is the dependency root, not a leaf, so features can't see its sources.
 *
 * A custom qualifier (not @Named) sidesteps KSP2 + Hilt 2.56's intermittent trouble
 * resolving @Named strings across module boundaries.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
)
annotation class ManifestUrl
