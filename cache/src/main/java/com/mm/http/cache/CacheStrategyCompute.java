package com.mm.http.cache;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class CacheStrategyCompute {
    private final Response cacheResponse;

    CacheStrategyCompute(Response cacheResponse) {
        this.cacheResponse = cacheResponse;
    }

    public Response getCacheResponse() {
        return cacheResponse;
    }

    public static class Factory {
        private final Long nowMillis;
        private final Response cacheResponse;
        private final CacheHelper cache;

        public Factory(Long nowMillis, Long duration, TimeUnit timeUnit, Response cacheResponse, CacheHelper cache) {
            this.nowMillis = nowMillis;
            this.cacheResponse = cacheResponse;
            this.cache = cache;
            if (cacheResponse != null) {
                receivedResponseAtMillis = cacheResponse.receivedResponseAtMillis();
                expires = timeUnit.toMillis(duration);
            }
        }

        /**
         * The last modified date of the cached response, if known.
         */
        private Long receivedResponseAtMillis = 0L;

        /**
         * The expiration date of the cached response, if known. If both this field and the max age are
         * set, the max age is preferred.
         */
        private Long expires = 0L;

        private boolean isExpires() {
            return (nowMillis - receivedResponseAtMillis) > expires;
        }

        public CacheStrategyCompute compute() {
            return computeCandidate();
        }

        private CacheStrategyCompute computeCandidate() {
            if (cacheResponse == null) {
                return new CacheStrategyCompute(null);
            }

            if (isExpires()) {
                try {
                    cache.remove(cacheResponse.request());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new CacheStrategyCompute(null);
            }
            return new CacheStrategyCompute(cacheResponse);
        }
    }
}
