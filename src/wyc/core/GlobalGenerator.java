package wyc.core;

import wyil.ModuleLoader;

/**
 * <p>
 * The global generator is responsible for generating wyil bytecode for "global"
 * items. Essentially, this comes down to type constraints and partial
 * constants. For example:
 * </p>
 * 
 * <pre>
 * define nat as int where $ >= 0
 * 
 * int f(nat x):
 *    return x-1
 * </pre>
 * 
 * <p>
 * The global generator is responsible for generating the code for the
 * constraint on <code>nat</code>. Note, local generator are responsible for
 * inlining that constraint into the body of function <code>f</code>.
 * </p>
 * 
 * <p>
 * The code generated by the global generator for the constraint on
 * <code>nat</code> would look like this:
 * </p>
 * 
 * <pre>
 * define nat as int
 * where:
 *     load $
 *     const 0
 *     ifge goto exit
 *     fail("type constraint not satisfied")
 *  .exit:
 * </pre>
 * 
 * This wyil bytecode simply compares the special variable $ against 0. Here, $
 * represents the value held in a variable of type <code>nat</code>. If the
 * constraint fails, then the given message is printed.
 * 
 * @author David J. Pearce
 * 
 */
public class GlobalGenerator {
	private CompilationGroup srcfiles;
	private ModuleLoader loader;
	
	public GlobalGenerator(ModuleLoader loader, CompilationGroup files) {
		this.srcfiles = files;
		this.loader = loader;
	}
}
