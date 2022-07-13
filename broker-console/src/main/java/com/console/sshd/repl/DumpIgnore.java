/**
 * 
 */
package com.console.sshd.repl;

import java.lang.annotation.*;

/**
 * @author yama
 * 26 Dec, 2014
 */
@Target(ElementType.TYPE)  
@Retention(RetentionPolicy.RUNTIME)  
@Documented
@Inherited  
public @interface DumpIgnore {
	
}
