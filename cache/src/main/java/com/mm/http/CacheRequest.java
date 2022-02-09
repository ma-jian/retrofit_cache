package com.mm.http;

import com.mm.http.cache.StrategyType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class CacheRequest {
    private final int cacheStrategy;
    private final Long duration;
    private final TimeUnit timeUnit;
    private final List<String> ignoreKey;

    private CacheRequest(int cacheStrategy, long duration, TimeUnit timeUnit, List<String> ignoreKey) {
        this.cacheStrategy = cacheStrategy;
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.ignoreKey = ignoreKey;
    }

    public int getCacheStrategy() {
        return cacheStrategy;
    }

    public Long getDuration() {
        return duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public List<String> getIgnoreKey() {
        return ignoreKey;
    }

    public static class Builder {
        private int strategy = StrategyType.NO_CACHE;
        private Long duration = 24L;
        private TimeUnit timeUnit = TimeUnit.HOURS;
        private List<String> ignoreKey = new ArrayList<>();

        Builder cacheStrategy(@StrategyType int cacheStrategy) {
            this.strategy = cacheStrategy;
            return this;
        }

        Builder duration(Long duration) {
            this.duration = duration;
            return this;
        }

        Builder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        Builder ignoreKey(List<String> ignoreKey) {
            this.ignoreKey = ignoreKey;
            return this;
        }

        CacheRequest build() {
            return new CacheRequest(strategy, duration, timeUnit, ignoreKey);
        }
    }
}
