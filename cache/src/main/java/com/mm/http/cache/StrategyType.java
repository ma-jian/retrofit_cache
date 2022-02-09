package com.mm.http.cache;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

@Retention(RetentionPolicy.RUNTIME)
@IntDef({StrategyType.NO_CACHE, StrategyType.FORCE_CACHE, StrategyType.FORCE_NETWORK,
        StrategyType.IF_CACHE_ELSE_NETWORK, StrategyType.IF_NETWORK_ELSE_CACHE,
        StrategyType.CACHE_AND_NETWORK, StrategyType.CACHE_AND_NETWORK_DIFF})
public @interface StrategyType {
    int NO_CACHE = -1; //不缓存
    int FORCE_NETWORK = 1; // 仅网络
    int FORCE_CACHE = 2; // 仅缓存
    int IF_CACHE_ELSE_NETWORK = 3; // 优先缓存
    int IF_NETWORK_ELSE_CACHE = 4; // 优先网络
    int CACHE_AND_NETWORK = 5; // 先缓存后网络
    int CACHE_AND_NETWORK_DIFF = 6; //
}
