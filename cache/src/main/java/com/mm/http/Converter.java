package com.mm.http;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.Part;
import retrofit2.http.PartMap;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public interface Converter<F, T> {
    @Nullable
    T convert(F value) throws IOException;

    /** Creates {@link Converter} instances based on a type and target usage. */
    abstract class Factory {

        public @Nullable
        Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, RetrofitCache retrofit) {
            return null;
        }

        /**
         * Returns a {@link Converter} for converting {@code type} to an HTTP request body, or null if
         * {@code type} cannot be handled by this factory. This is used to create converters for types
         * specified by {@link Body @Body}, {@link Part @Part}, and {@link PartMap @PartMap} values.
         */
        public @Nullable
        Converter<?, RequestBody> requestBodyConverter(
                Type type,
                Annotation[] parameterAnnotations,
                Annotation[] methodAnnotations,
                RetrofitCache retrofit) {
            return null;
        }

        /**
         * Extract the upper bound of the generic parameter at {@code index} from {@code type}. For
         * example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
         */
        protected static Type getParameterUpperBound(int index, ParameterizedType type) {
            return Utils.getParameterUpperBound(index, type);
        }

        /**
         * Extract the raw class type from {@code type}. For example, the type representing {@code
         * List<? extends Runnable>} returns {@code List.class}.
         */
        protected static Class<?> getRawType(Type type) {
            return Utils.getRawType(type);
        }
    }
}
