package com.getcapacitor.annotation; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Permission { String[] strings() default {}; String alias() default ""; }
