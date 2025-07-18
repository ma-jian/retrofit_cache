package com.mm.http

/**
 * host类型,默认 hostType 为 DOMAIN_URL。
 * @param value host for service
 * @param hostType  host type for service
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class HOST(val value: String, val hostType: Int = NORMAL) {

    companion object {
        const val NORMAL = 0 // URL 默认的使用value值作为host，其他类型自行添加
    }
}