package wyone.core;

import java.util.*;
import wyone.util.*;

public class WyoneFile {
	public final String pkg;
	public final String name;
	public final String filename;
	public final ArrayList<Decl> declarations;
	
	public WyoneFile(String pkg, String name, String filename, Collection<Decl> declarations) {
		this.pkg = pkg;
		this.name = name;
		this.filename = filename;
		this.declarations = new ArrayList<Decl>(declarations);
	}
	
	public interface Decl extends SyntacticElement {}
	
	public static class TermDecl extends SyntacticElement.Impl implements Decl {
		public final Type.Term type;
		
		public TermDecl(Type.Term data, Attribute... attributes) {
			super(attributes);
			this.type = data;
		}
		
		public TermDecl(Type.Term data, Collection<Attribute> attributes) {
			super(attributes);
			this.type = data;
		}
	}
	
	public static class ClassDecl extends SyntacticElement.Impl implements Decl {
		public final String name;
		public final List<String> children;
		
		public ClassDecl(String n, Collection<String> children, Attribute... attributes) {
			super(attributes);
			this.name = n;
			this.children = new ArrayList<String>(children);
		}
		
		public ClassDecl(String n, Collection<String> children, Collection<Attribute> attributes) {
			super(attributes);
			this.name = n;
			this.children = new ArrayList<String>(children);
		}
	}
	
	public static class FunDecl extends SyntacticElement.Impl implements Decl {
		public final String name;
		public final Type.Fun type;
		public final ArrayList<Type> types;
		public final ArrayList<Code> codes;

		public FunDecl(String name, Type.Fun type, List<Type> types,
				List<Code> codes, Attribute... attributes) {
			super(attributes);
			this.name = name;
			this.type = type;
			this.types = new ArrayList<Type>(types);
			this.codes = new ArrayList<Code>(codes);
		}
		
		public FunDecl(String name, Type.Fun type, List<Type> types,
				List<Code> codes, Collection<Attribute> attributes) {
			super(attributes);
			this.name = name;
			this.type = type;
			this.types = new ArrayList<Type>(types);
			this.codes = new ArrayList<Code>(codes);
		}
	}
}