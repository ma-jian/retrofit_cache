package com.mm.http.cache

import androidx.annotation.IntDef

/**
 * 各个类型的缓存策略
 */
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    StrategyType.NO_CACHE,
    StrategyType.FORCE_CACHE,
    StrategyType.FORCE_NETWORK,
    StrategyType.IF_CACHE_ELSE_NETWORK,
    StrategyType.IF_NETWORK_ELSE_CACHE,
    StrategyType.CACHE_AND_NETWORK
)
annotation class StrategyType {

    companion object {
        const val NO_CACHE = -1 //不缓存
        const val FORCE_NETWORK = 1 // 仅网络
        const val FORCE_CACHE = 2 // 仅缓存
        const val IF_CACHE_ELSE_NETWORK = 3 // 优先缓存
        const val IF_NETWORK_ELSE_CACHE = 4 // 优先网络
        const val CACHE_AND_NETWORK = 5 // 先缓存后网络
    }
}