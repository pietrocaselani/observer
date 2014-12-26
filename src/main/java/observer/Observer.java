package observer;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Created by Pietro Caselani
 * On 11/17/14
 * Property-Observer
 */
@Retention(CLASS)
@Target(FIELD)
public @interface Observer {
	String value() default "";
}