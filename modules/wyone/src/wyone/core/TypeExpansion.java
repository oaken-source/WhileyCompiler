package wyone.core;

import static wyone.util.SyntaxError.syntaxError;

import java.io.File;
import java.util.*;
import static wyone.util.type.Types.*;

public class TypeExpansion {
	public void expand(SpecFile spec) {
		HashMap<String,Type.Term> terms = gatherTerms(spec);
		HashMap<String,Type> macros = gatherMacros(spec,terms);
		HashSet<String> expanded = new HashSet<String>();
		expandTypeDeclarations(spec,terms,macros,expanded);
	}
	
	protected void expandTypeDeclarations(SpecFile spec,
			HashMap<String, Type.Term> terms, HashMap<String, Type> macros,
			HashSet<String> expanded) {
		for (SpecFile.Decl d : spec.declarations) {
			if (d instanceof SpecFile.IncludeDecl) {
				SpecFile.IncludeDecl id = (SpecFile.IncludeDecl) d;
				gatherTerms(id.file, terms);
			} else if (d instanceof SpecFile.TermDecl) {
				SpecFile.TermDecl td = (SpecFile.TermDecl) d;
				td.type = (Type.Term) expandAsTerm(td.type.name(), spec, terms, macros, expanded);
			}
		}
	}
	
	protected HashMap<String,Type.Term> gatherTerms(SpecFile spec) {
		HashMap<String,Type.Term> map = new HashMap();
		gatherTerms(spec,map);
		return map;
	}
	
	protected void gatherTerms(SpecFile spec, HashMap<String, Type.Term> terms) {
		for (SpecFile.Decl d : spec.declarations) {
			if (d instanceof SpecFile.IncludeDecl) {
				SpecFile.IncludeDecl id = (SpecFile.IncludeDecl) d;
				gatherTerms(id.file, terms);
			} else if (d instanceof SpecFile.TermDecl) {
				SpecFile.TermDecl td = (SpecFile.TermDecl) d;
				String name = td.type.name();
				if (terms.get(name) != null) {
					syntaxError("type " + name + " is already defined",
							spec.file, td);
				}
				terms.put(name, td.type);
			}
		}
	}
	
	protected HashMap<String,Type> gatherMacros(SpecFile spec, HashMap<String, Type.Term> terms) {
		HashSet<String> openClasses = new HashSet<String>();
		HashMap<String,Type> macros = new HashMap<String,Type>();
		gatherMacros(spec,openClasses,macros,terms);
		return macros;
	}
	
	protected void gatherMacros(SpecFile spec, HashSet<String> openClasses,
			HashMap<String, Type> macros, HashMap<String, Type.Term> terms) {			
		// First, we have to inline all the type declarations.
		for (SpecFile.Decl d : spec.declarations) {
			if (d instanceof SpecFile.IncludeDecl) {
				SpecFile.IncludeDecl id = (SpecFile.IncludeDecl) d;
				gatherMacros(id.file, openClasses, macros, terms);				
			} else if (d instanceof SpecFile.ClassDecl) {
				SpecFile.ClassDecl cd = (SpecFile.ClassDecl) d;
				Type type = macros.get(cd.name);

				if (type != null && !openClasses.contains(cd.name)) {
					syntaxError("type " + cd.name + " is not open", spec.file,
							cd);
				} else if (type != null && !cd.isOpen) {
					syntaxError("type " + cd.name
							+ " cannot be closed (i.e. it's already open)",
							spec.file, cd);
				} else if (terms.containsKey(cd.name)) {
					syntaxError(cd.name + " is defined as a term", spec.file,
							cd);
				}

				if (type == null) {
					type = cd.type;
				} else {
					type = Type.T_OR(type,cd.type);
				}

				macros.put(cd.name, type);

				if (cd.isOpen) {
					openClasses.add(cd.name);
				}
			}
		}
	}
	
	/**
	 * Fully expand the type associated with a given name. The name must be a
	 * key into the <code>types</code> map. In the case that the name is already
	 * in the <code>expanded</code> set, then type it currently maps to is
	 * returned. Otherwise, the type is traversed and all subcomponents
	 * expanded.
	 * 
	 * @param name
	 *            --- name of type to expand.
	 * @param spec
	 *            --- spec file containing type.
	 * @param types
	 *            --- types map to be updated with expanded type.
	 * @param expanded
	 *            --- set of previously expanded types.
	 * @return
	 */
	protected Type.Term expandAsTerm(String name, SpecFile spec,
			HashMap<String, Type.Term> terms, HashMap<String, Type> macros,
			HashSet<String> expanded) {
		Type.Term type = terms.get(name);

		if (expanded.contains(name)) {
			return type;
		} else {
			Automaton in = type.automaton;
			ArrayList<Automaton.State> states = new ArrayList<Automaton.State>();
			int root = expand(in.root(0), in, states,
					new HashMap<String, Integer>(), spec, macros);
			Automaton out = new Automaton(SCHEMA, states);
			out.mark(root);
			type = (Type.Term) Type.construct(out);
		}
		
		System.err.println("EXPANDED: " + terms.get(name) + " => " + type);
		terms.put(name, type);
		expanded.add(name);

		return type;
	}

	protected int expand(int node, Automaton in,
			ArrayList<Automaton.State> out, HashMap<String, Integer> roots,
			SpecFile spec, HashMap<String, Type> macros) {

		Automaton.State state = in.get(node);
		int myIndex = out.size();
		out.add(null); // temporary

		if (state instanceof Automaton.Constant) {
			// do nothing
		} else if (state instanceof Automaton.Compound) {
			Automaton.Compound ac = (Automaton.Compound) state;
			int[] nelements = new int[ac.size()];
			for (int i = 0; i != nelements.length; ++i) {
				nelements[i] = expand(ac.get(i), in, out, roots, spec, macros);
			}
			if (state instanceof Automaton.Set) {
				state = new Automaton.Set(nelements);
			} else if (state instanceof Automaton.Bag) {
				state = new Automaton.Bag(nelements);
			} else {
				state = new Automaton.List(nelements);
			}
		} else {
			Automaton.Term t = (Automaton.Term) state;
			int ncontents = Automaton.K_VOID;
			if (t.kind == K_Term) {
				// this is the potential problem case.
				Automaton.List l = (Automaton.List) in.get(t.contents);
				Automaton.Strung s = (Automaton.Strung) in.get(l.get(0));
				String name = s.value;
				Type macro = macros.get(name);
				if (macro != null) {
					if (l.size() > 1) {
						throw new RuntimeException("Cannot use " + name
								+ " with an operand!");
					} else if (roots.containsKey(name)) {
						out.remove(myIndex); // back track
						return roots.get(name);
					} else {
						roots.put(name, myIndex);
						out.remove(myIndex); // back track
						in = macro.automaton;
						return expand(in.root(0), in, out, roots, spec, macros);
					}
				}
			}
			if (t.contents != Automaton.K_VOID) {
				ncontents = expand(t.contents, in, out, roots, spec, macros);
			}
			state = new Automaton.Term(t.kind, ncontents);
		}
		
		out.set(myIndex,state);
		return myIndex;
	}
}