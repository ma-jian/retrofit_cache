package com.mm.http.cache

import androidx.annotation.IntDef

/**
 * Created by : majian
 * Date : 2021/8/24
 * 各个类型的缓存策略
 */
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    StrategyType.NO_CACHE,
    StrategyType.FORCE_CACHE,
    StrategyType.FORCE_NETWORK,
    StrategyType.IF_CACHE_ELSE_NETWORK,
    StrategyType.IF_NETWORK_ELSE_CACHE,
    StrategyType.CACHE_AND_NETWORK,
    StrategyType.CACHE_AND_NETWORK_DIFF
)
annotation class StrategyType {

    companion object {
        const val NO_CACHE = -1 //不缓存
        const val FORCE_NETWORK = 1 // 仅网络
        const val FORCE_CACHE = 2 // 仅缓存
        const val IF_CACHE_ELSE_NETWORK = 3 // 优先缓存
        const val IF_NETWORK_ELSE_CACHE = 4 // 优先网络
        const val CACHE_AND_NETWORK = 5 // 先缓存后网络
        const val CACHE_AND_NETWORK_DIFF = 6 // 暂未实现该策略
    }
}