// This file is part of the Whiley-to-Java Compiler (wyjc).
//
// The Whiley-to-Java Compiler is free software; you can redistribute 
// it and/or modify it under the terms of the GNU General Public 
// License as published by the Free Software Foundation; either 
// version 3 of the License, or (at your option) any later version.
//
// The Whiley-to-Java Compiler is distributed in the hope that it 
// will be useful, but WITHOUT ANY WARRANTY; without even the 
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
// PURPOSE.  See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public 
// License along with the Whiley-to-Java Compiler. If not, see 
// <http://www.gnu.org/licenses/>
//
// Copyright 2010, David James Pearce. 

package wyjc.stages;

import java.util.*;

import static wyil.util.SyntaxError.*;
import wyil.ModuleLoader;
import wyil.util.*;
import wyil.lang.*;
import wyil.lang.Type.Tuple;
import wyjc.lang.*;
import wyjc.lang.WhileyFile.*;
import wyjc.lang.Stmt.*;
import wyjc.lang.Expr.*;
import wyjc.util.*;

public class TypeResolution {
	private final ModuleLoader loader;
	private HashSet<ModuleID> modules;
	private HashMap<NameID,List<Type.Fun>> functions;	
	private HashMap<NameID,Pair<Type,Block>> types;	
	private HashMap<NameID,UnresolvedType> unresolved;
	
	public TypeResolution(ModuleLoader loader) {
		this.loader = loader;
	}
	
	public void resolve(List<WhileyFile> files) {			
		modules = new HashSet<ModuleID>();
		functions = new HashMap<NameID,List<Type.Fun>>();
		types = new HashMap<NameID,Pair<Type,Block>>();		
		unresolved = new HashMap<NameID,UnresolvedType>();
		
		// now, init data
		for (WhileyFile f : files) {			
			modules.add(f.module);
		}
						
		// Stage 1 ... resolve and check types of all named types
		generateTypes(files);
					
		// Stage 2 ... resolve and check types for all functions / methods
		for(WhileyFile f : files) {
			for(WhileyFile.Decl d : f.declarations) {				
				if(d instanceof FunDecl) {
					partResolve(f.module,(FunDecl)d);
				}				
			}
		}
		
		// Stage 3 ... resolve, propagate types for all expressions
		for(WhileyFile f : files) {
			for(WhileyFile.Decl d : f.declarations) {				
				if(d instanceof TypeDecl) {
					resolve((TypeDecl)d);
				} else if(d instanceof ConstDecl) {
					resolve((ConstDecl)d);
				} else if(d instanceof FunDecl) {
					resolve((FunDecl)d);
				}				
			}
		}
	}

	/**
	 * The following method visits every define type statement in every whiley
	 * file being compiled, and determines its true type.  
	 * 
	 * @param files
	 */
	protected void generateTypes(List<WhileyFile> files) {		
		HashMap<NameID,SyntacticElement> srcs = new HashMap();
		
		// second construct list.
		for(WhileyFile f : files) {
			for(Decl d : f.declarations) {
				if(d instanceof TypeDecl) {
					TypeDecl td = (TypeDecl) d;
					NameID key = new NameID(f.module,td.name());					
					unresolved.put(key, td.type);
					srcs.put(key,d);
				}
			}
		}
		
		// third expand all types
		for(NameID key : unresolved.keySet()) {
			try {
				HashMap<NameID, Type> cache = new HashMap<NameID,Type>();			
				Pair<Type,Block> p = expandType(key,cache);				
				Type t = simplifyRecursiveTypes(p.first());				
				types.put(key,new Pair<Type,Block>(t,p.second()));				
			} catch(ResolveError ex) {
				syntaxError(ex.getMessage(),srcs.get(key),ex);
			}
		}
	}
		
	protected Pair<Type,Block> expandType(NameID key,
			HashMap<NameID, Type> cache) throws ResolveError {

		Pair<Type,Block> t = types.get(key);
		Type cached = cache.get(key);					
		
		if(cached != null) { 
			return new Pair<Type,Block>(cached,null); 
		} else if(t != null) {
			return t;
		} else if(!modules.contains(key.module())) {			
			// indicates a non-local key which we can resolve immediately
			Module mi = loader.loadModule(key.module());
			Module.TypeDef td = mi.type(key.name());			
			return new Pair<Type,Block>(td.type(),null);
		}

		// following is needed to terminate any recursion
		cache.put(key, Type.T_RECURSIVE(key.toString(),null));
		
		// Ok, expand the type properly then
		UnresolvedType ut = unresolved.get(key);
		
		t = expandType(ut, cache);
				 		
		// Now, we need to test whether the current type is open and recursive
		// on this name. In such case, we must close it in order to complete the
		// recursive type.
		if(Type.isOpenRecursive(key,t.first())) {
			t = new Pair<Type,Block>(Type.T_RECURSIVE(key.toString(),t.first()),null);			
		} 
		
		cache.put(key, t.first());
		
		// Done
		return t;		
	}	
	
	protected Pair<Type,Block> expandType(UnresolvedType t, HashMap<NameID, Type> cache) {
		if(t instanceof UnresolvedType.List) {
			UnresolvedType.List lt = (UnresolvedType.List) t;
			Pair<Type,Block> p = expandType(lt.element, cache);
			return new Pair<Type,Block>(Type.T_LIST(p.first()),p.second());
		} else if(t instanceof UnresolvedType.Set) {
			UnresolvedType.Set st = (UnresolvedType.Set) t;
			Pair<Type,Block> p = expandType(st.element, cache);
			return new Pair<Type,Block>(Type.T_SET(p.first()),p.second());
		} else if(t instanceof UnresolvedType.Tuple) {
			UnresolvedType.Tuple tt = (UnresolvedType.Tuple) t;
			HashMap<String,Type> types = new HashMap<String,Type>();
			for(Map.Entry<String,UnresolvedType> e : tt.types.entrySet()) {
				Pair<Type,Block> p = expandType(e.getValue(),cache);
				types.put(e.getKey(),p.first());
			}
			return new Pair<Type,Block>(Type.T_TUPLE(types),null);
		} else if(t instanceof UnresolvedType.Union) {
			UnresolvedType.Union ut = (UnresolvedType.Union) t;
			HashSet<Type.NonUnion> bounds = new HashSet<Type.NonUnion>();
			for(UnresolvedType b : ut.bounds) {
				Pair<Type,Block> p = expandType(b,cache);
				Type bt = p.first();
				if(bt instanceof Type.NonUnion) {
					bounds.add((Type.NonUnion)bt);
				} else {
					bounds.addAll(((Type.Union)bt).bounds);
				}
			}
			if(bounds.size() == 1) {
				return new Pair<Type,Block>(bounds.iterator().next(),null);
			} else {
				return new Pair<Type,Block>(Type.T_UNION(bounds),null);
			}
		} else if(t instanceof UnresolvedType.Process) {	
			UnresolvedType.Process ut = (UnresolvedType.Process) t;
			Pair<Type,Block> p = expandType(ut.element,cache);
			return new Pair<Type, Block>(Type.T_PROCESS(p.first()), p.second());			
		} else if(t instanceof UnresolvedType.Named) {
			UnresolvedType.Named dt = (UnresolvedType.Named) t;						
			Attribute.Module modInfo = dt.attribute(Attribute.Module.class);
			NameID name = new NameID(modInfo.module,dt.name);
			
			try {
				Pair<Type, Block> et = expandType(name, cache);

				if (Type.isExistential(et.first())) {
					return new Pair<Type, Block>(Type.T_NAMED(modInfo.module,
							dt.name, et.first()), et.second());
				} else {
					return et;
				}
			} catch (ResolveError rex) {
				syntaxError(rex.getMessage(), t, rex);
				return null;
			}			
		}  else {
			// for base cases
			return resolve(t);
		}
	}
	
	protected void partResolve(ModuleID module, FunDecl fd) {
		
		ArrayList<Type> parameters = new ArrayList<Type>();
		for (WhileyFile.Parameter p : fd.parameters) {						
			parameters.add(resolve(p.type).first());			
		}
		
		// method return type
		Type ret = resolve(fd.ret).first();
		
		// method receiver type (if applicable)
		Type.Process rec = null;
		if (fd.receiver != null) {			
			Type t = resolve(fd.receiver).first();
			checkType(t, Type.Process.class, fd.receiver);
			rec = (Type.Process) rec;			
		}
		
		Type.Fun ft = Type.T_FUN(rec, ret, parameters);
		
		List<Type.Fun> types = functions.get(fd.name);
		if(types == null) {
			types = new ArrayList<Type.Fun>();
			functions.put(new NameID(module, fd.name), types);
		}
		
		types.add(ft);		
		fd.attributes().add(new Attribute.Fun(ft));
	}

	protected void resolve(ConstDecl td) {
		// FIXME: this looks problematic if the constant contains method calls.
		// The problem is that we may not have generated the type for the given
		// method call, which means we won't be able to bind it. One option is
		// simply to disallow function calls in constant definitions.
		resolve(td.constant,new HashMap<String,Type>(),new HashMap<String,Type>());		
	}
	
	protected void resolve(TypeDecl td) {		
		Pair<Type,Block> p = resolve(td.type);
		Type t = p.first();
		td.attributes().add(new Attribute.Type(t));
		
		if(td.constraint != null) {
			// FIXME: at this point, would be good to add types for other
			// exposed variables.
			HashMap<String,Type> environment = new HashMap<String,Type>();
			HashMap<String,Type> declared = new HashMap<String,Type>();
			environment.put("$", t);			
			t = resolve(td.constraint, environment, declared).first();			
			checkType(t,Type.Bool.class, td.constraint);			
		}				
	}		
		
	protected void resolve(FunDecl fd) {
		
		// The declared environment holds the declared type of a given local
		// variable. This is separate from the environment (see below), whose
		// types may change as a result of assignment and type inference.
		HashMap<String,Type> declared = new HashMap<String,Type>();
		
		// The environment holds the current type of a given local variable. The
		// current type is affected by assignments to that variable.
		HashMap<String,Type> environment = new HashMap<String,Type>();		
		
		// method parameter types
		for (WhileyFile.Parameter p : fd.parameters) {						
			Pair<Type,Block> t = resolve(p.type);
			environment.put(p.name(),t.first());
			declared.put(p.name(),t.first());
		}
				
		// method return type
		Pair<Type,Block> ret = resolve(fd.ret);		
		
		// method receiver type (if applicable)			
		if(fd.receiver != null) {			
			Pair<Type,Block> rec = resolve(fd.receiver);
			environment.put("this", rec.first());
			declared.put("this", rec.first());
		}
			
		if(fd.constraint != null) {			
			environment.put("$",ret.first());
			resolve(fd.constraint, environment, declared);			
			environment.remove("$");
		}
		
		List<Stmt> stmts = fd.statements;
		for (int i=0;i!=stmts.size();++i) {
			resolve(stmts.get(i), fd, environment, declared);							
		}
	}
	
	public Block resolve(Stmt s, FunDecl fd, HashMap<String, Type> environment,
			HashMap<String, Type> declared) {		
		try {
			if(s instanceof Skip) {				
			} else if(s instanceof VarDecl) {
				return resolve((VarDecl)s, environment, declared);
			} else if(s instanceof Assign) {
				return resolve((Assign)s, environment, declared);
			} else if(s instanceof Assert) {
				return resolve((Assert)s, environment, declared);
			} else if(s instanceof Return) {
				return resolve((Return)s, fd, environment, declared);
			} else if(s instanceof Debug) {
				return resolve((Debug)s, environment, declared);
			} else if(s instanceof IfElse) {
				return resolve((IfElse)s, fd, environment, declared);
			} else if(s instanceof Invoke) {
				return resolve((Invoke)s, environment, declared).second();
			} else if(s instanceof Spawn) {
				return resolve((UnOp)s, environment, declared).second();
			} else {
				syntaxError("unknown statement encountered: "
						+ s.getClass().getName(), s);				
			}
		} catch(ResolveError rex) {
			syntaxError(rex.getMessage(),s,rex);
		} catch(SyntaxError sex) {
			throw sex;
		} catch(Exception ex) {
			syntaxError("internal failure", s, ex);			
		}
		return null;
	}
	
	protected Block resolve(VarDecl s, HashMap<String, Type> environment,
			HashMap<String, Type> declared) throws ResolveError {
		Expr init = s.initialiser;
		Pair<Type,Block> tb = resolve(s.type);
		Type type = tb.first();
		if(init != null) {
			Pair<Type,Block> initT = resolve(init,environment, declared);
			checkIsSubtype(type,initT.first(),init);
			environment.put(s.name,type);
		} 
		declared.put(s.name,type);
		return null;
	}
	
	protected Block resolve(Assign s, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		Pair<Type,Block> rhs_tb = resolve(s.rhs, environment, declared);
		
		if(s.lhs instanceof Variable) {
			// perform type inference as a result of this assignment
			Variable v = (Variable) s.lhs;			
			Type declared_t = declared.get(v.var);
			if(declared_t == null) {
				syntaxError("unknown variable",v);
			}
			checkIsSubtype(declared_t,rhs_tb.first(),s.rhs);
			environment.put(v.var, rhs_tb.first());
		} else {
			Pair<Type,Block> lhs_tb = resolve(s.lhs, environment, declared);
			checkIsSubtype(lhs_tb.first(), rhs_tb.first(), s.rhs);					
		}
		return null;
	}

	protected Block resolve(Assert s, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		Pair<Type,Block> t = resolve(s.expr, environment, declared);
		checkIsSubtype(t.first(),Type.T_BOOL,s.expr);
		return null;
	}

	protected Block resolve(Return s, FunDecl fd, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		if(s.expr != null) {
			Pair<Type,Block> t = resolve(s.expr, environment, declared);
			Type.Fun ft = fd.attribute(Attribute.Fun.class).type;
			checkIsSubtype(ft.ret,t.first(),s.expr);
		}
		return null;
	}
	
	protected Block resolve(Debug s, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		Pair<Type,Block> t = resolve(s.expr, environment, declared);
		checkIsSubtype(t.first(),Type.T_LIST(Type.T_INT),s.expr);
		return null;
	}

	protected Block resolve(IfElse s, FunDecl fd, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		Pair<Type,Block> tb = resolve(s.condition, environment, declared);
		checkType(tb.first(),Type.Bool.class,s.condition);
		
		// FIXME: need to perform some type inference here
		
		HashMap<String,Type> tenv = new HashMap<String,Type>(environment);		
		HashMap<String,Type> tdec = new HashMap<String,Type>(declared);
		for (Stmt st : s.trueBranch) {
			resolve(st, fd, tenv, tdec);
		}
		if (s.falseBranch != null) {			
			HashMap<String,Type> fenv = new HashMap<String,Type>(environment);
			HashMap<String,Type> fdec = new HashMap<String,Type>(declared);
			for (Stmt st : s.falseBranch) {
				resolve(st, fd, fenv, fdec);
			}
		}
		return null;
	}
	
	protected Pair<Type,Block> resolve(Expr e, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		try {
			if (e instanceof Constant) {
				return resolve((Constant)e, environment, declared);
			} else if (e instanceof Variable) {
				return resolve((Variable)e, environment, declared);
			} else if (e instanceof NaryOp) {
				return resolve((BinOp)e, environment, declared);
			} else if (e instanceof BinOp) {
				return resolve((BinOp)e, environment, declared);
			} else if (e instanceof UnOp) {
				return resolve((UnOp)e, environment, declared);
			} else if (e instanceof Invoke) {
				return resolve((Invoke)e, environment, declared);
			} else if (e instanceof Comprehension) {
				return resolve((Comprehension) e, environment, declared);
			} else if (e instanceof TupleAccess) {
				return resolve((TupleAccess) e, environment, declared);
			} else if (e instanceof TupleGen) {
				return resolve((TupleGen) e, environment, declared);
			} else {				
				syntaxError("unknown expression encountered: "
							+ e.getClass().getName(), e);			
			}
		} catch(SyntaxError se) {
			throw se;
		} catch(Exception ex) {
			syntaxError("internal failure", e, ex);			
		}	
		
		return null;
	}
	
	protected Pair<Type,Block> resolve(Invoke s, HashMap<String,Type> environment, HashMap<String,Type> declared)
			throws ResolveError {
		List<Expr> args = s.arguments;
		
		ArrayList<Type> ptypes = new ArrayList<Type>();		
		for(Expr e : args) {			
			Pair<Type,Block> e_tb = resolve(e,environment, declared);
			ptypes.add(e_tb.first());			
		}
		
		Type.Process receiver = null;
		if(s.receiver != null) {			
			Pair<Type,Block> tb = resolve(s.receiver,environment, declared);
			checkType(tb.first(),Type.Process.class,s.receiver);
			receiver = (Type.Process) tb.first();
		}
		
		Attribute.Module modInfo = s.attribute(Attribute.Module.class);
		Type.Fun funtype = bindFunction(modInfo.module, s.name, receiver, ptypes,s);
		
		if(funtype == null) {
			syntaxError("invalid or ambiguous method call",s);
		}
		
		s.attributes().add(new Attribute.Fun(funtype));
		
		return new Pair<Type,Block>(funtype.ret,null);									
	}
			
	protected Pair<Type,Block> resolve(Constant c, HashMap<String,Type> environment, HashMap<String,Type> declared)  {
		return new Pair<Type,Block>(c.val.type(),null);
	}
	
	protected Pair<Type,Block> resolve(Variable v, HashMap<String,Type> environment, HashMap<String,Type> declared)  {
		Type t = environment.get(v.var);
		if(t == null) {		
			syntaxError("unknown variable",v);			
		}
		return new Pair<Type,Block>(t,null);
	}
	
	protected Pair<Type,Block> resolve(UnOp v, HashMap<String,Type> environment, HashMap<String,Type> declared,
			ArrayList<PkgID> imports)  {
		Expr mhs = v.mhs;
		Pair<Type,Block> tb = resolve(mhs, environment, declared); 
		Type t = tb.first();
		
		if(v.op == UOp.NEG) {
			checkIsSubtype(Type.T_REAL,t,mhs);
		} else if(v.op == UOp.NOT) {
			checkIsSubtype(Type.T_BOOL,t,mhs);			
		} else if(v.op == UOp.LENGTHOF) {
			checkIsSubtype(Type.T_SET(Type.T_ANY),t,mhs);
			t = Type.T_INT;
		} else if(v.op == UOp.PROCESSACCESS) {
			Type.Process tp = checkType(t,Type.Process.class,mhs);
			t = tp.element;
		} else if(v.op == UOp.PROCESSSPAWN){
			t = Type.T_PROCESS(t);
		} 
		
		return new Pair<Type,Block>(t,null);
	}
	
	protected Pair<Type,Block> resolve(BinOp v, HashMap<String,Type> environment, HashMap<String,Type> declared) {
		
		Pair<Type,Block> lhs_tb = resolve(v.lhs, environment, declared);
		Pair<Type,Block> rhs_tb = resolve(v.rhs, environment, declared);
		Type lhs_t = lhs_tb.first();
		Type rhs_t = rhs_tb.first();
		BOp bop = v.op;
			
		if(bop == BOp.OR || bop == BOp.AND) {
			checkIsSubtype(Type.T_BOOL, lhs_t, v);
			checkIsSubtype(Type.T_BOOL, rhs_t, v);
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if (bop == BOp.ADD || bop == BOp.SUB || bop == BOp.MUL
				|| bop == BOp.DIV) {
			checkIsSubtype(Type.T_REAL, lhs_t, v);
			checkIsSubtype(Type.T_REAL, rhs_t, v);
			return new Pair<Type,Block>(Type.leastUpperBound(lhs_t,rhs_t),null);
		} else if (bop == BOp.LT || bop == BOp.LTEQ || bop == BOp.GT
				|| bop == BOp.GTEQ) {
			checkIsSubtype(Type.T_REAL, lhs_t, v);
			checkIsSubtype(Type.T_REAL, rhs_t, v);
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if(bop == BOp.UNION || bop == BOp.INTERSECTION) {
			checkIsSubtype(Type.T_SET(Type.T_ANY), lhs_t, v);
			checkIsSubtype(Type.T_SET(Type.T_ANY), rhs_t, v);
			return new Pair<Type,Block>(Type.leastUpperBound(lhs_t,rhs_t),null);
		} else if (bop == BOp.SUBSET || bop == BOp.SUBSETEQ) {
			checkIsSubtype(Type.T_SET(Type.T_ANY), lhs_t, v);
			checkIsSubtype(Type.T_SET(Type.T_ANY), rhs_t, v);
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if(bop == BOp.EQ || bop == BOp.NEQ){
			if(!Type.isSubtype(lhs_t, rhs_t) && !Type.isSubtype(rhs_t, lhs_t)) {
				syntaxError("Cannot compare types",v);
			}
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if(bop == BOp.ELEMENTOF) {
			checkType(rhs_t, Type.Set.class, v);
			Type.Set st = (Type.Set) rhs_t;
			if(!Type.isSubtype(lhs_t, st.element) && !Type.isSubtype(st.element, lhs_t)) {
				syntaxError("Cannot compare types",v);
			}
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if(bop == BOp.LISTACCESS) {
			checkType(lhs_t, Type.List.class, v);
			checkIsSubtype(Type.T_INT, rhs_t, v);
			Type.List lt = (Type.List) lhs_t;  
			return new Pair<Type,Block>(lt.element,null);		
		} 			
		
		throw new RuntimeException("NEED TO ADD MORE CASES TO TYPE RESOLUTION BINOP");
	}
	
	protected Pair<Type,Block> resolve(NaryOp v, HashMap<String,Type> environment, HashMap<String,Type> declared)  {		
		if(v.nop == NOp.SUBLIST) {
			if(v.arguments.size() != 3) {
				syntaxError("incorrect number of arguments",v);
			}
			Pair<Type,Block> src = resolve(v.arguments.get(0), environment, declared);
			Pair<Type,Block> start = resolve(v.arguments.get(1), environment, declared);
			Pair<Type,Block> end = resolve(v.arguments.get(2), environment, declared);
			checkType(src.first(),Type.List.class,v.arguments.get(0));
			checkType(start.first(),Type.Int.class,v.arguments.get(1));
			checkType(end.first(),Type.Int.class,v.arguments.get(2));
			return new Pair<Type,Block>(((Type.List)src.first()).element,null);
		} else {

			Type etype = Type.T_VOID;

			for(Expr e : v.arguments) {
				Pair<Type,Block> t = resolve(e, environment, declared);
				etype = Type.leastUpperBound(t.first(),etype);
			}		

			if(v.nop == NOp.LISTGEN) {
				return new Pair<Type,Block>(Type.T_LIST(etype),null);
			} else {
				return new Pair<Type,Block>(Type.T_SET(etype),null);
			} 
		}
	}
	
	protected Pair<Type,Block> resolve(Comprehension e, HashMap<String,Type> environment, HashMap<String,Type> declared)  {				
		throw new RuntimeException("Need to implement type resolution for Comprehension");
	}
		
	protected Pair<Type,Block> resolve(TupleGen sg, HashMap<String,Type> environment, HashMap<String,Type> declared)
			 {
		HashMap<String, Type> types = new HashMap<String, Type>();
		for (Map.Entry<String, Expr> e : sg.fields.entrySet()) {
			Pair<Type,Block> tb = resolve(e.getValue(), environment, declared);
			types.put(e.getKey(), tb.first());
		}
		return new Pair<Type,Block>(Type.T_TUPLE(types),null);
	}
	
	protected Pair<Type,Block> resolve(TupleAccess sg, HashMap<String,Type> environment, HashMap<String,Type> declared)
			 {
		Pair<Type,Block> lhs = resolve(sg.lhs, environment, declared);
		// FIXME: will need to determine effective tuple type here
		Type.Tuple tup = checkType(lhs.first(), Type.Tuple.class, sg.lhs);
		Type ft = tup.types.get(sg.name);
		if (ft == null) {
			syntaxError("type has no field named: " + sg.name, sg.lhs);
		}
		return new Pair<Type,Block>(ft,null);
	}
	
	protected Pair<Type,Block> resolve(UnresolvedType t) {
		if(t instanceof UnresolvedType.Any) {
			return new Pair<Type,Block>(Type.T_ANY,null);
		} else if(t instanceof UnresolvedType.Existential) {
			return new Pair<Type,Block>(Type.T_EXISTENTIAL,null);
		} else if(t instanceof UnresolvedType.Void) {
			return new Pair<Type,Block>(Type.T_VOID,null);
		} else if(t instanceof UnresolvedType.Bool) {
			return new Pair<Type,Block>(Type.T_BOOL,null);
		} else if(t instanceof UnresolvedType.Int) {
			return new Pair<Type,Block>(Type.T_INT,null);
		} else if(t instanceof UnresolvedType.Real) {
			return new Pair<Type,Block>(Type.T_REAL,null);
		} else if(t instanceof UnresolvedType.List) {
			UnresolvedType.List lt = (UnresolvedType.List) t;
			Pair<Type,Block> p = resolve(lt.element);
			return new Pair<Type,Block>(Type.T_LIST(p.first()),null);
		} else if(t instanceof UnresolvedType.Set) {
			UnresolvedType.Set st = (UnresolvedType.Set) t;
			Pair<Type,Block> p = resolve(st.element);
			return new Pair<Type,Block>(Type.T_SET(p.first()),null);			
		} else if(t instanceof UnresolvedType.Tuple) {
			UnresolvedType.Tuple tt = (UnresolvedType.Tuple) t;
			HashMap<String,Type> types = new HashMap<String,Type>();
			for(Map.Entry<String,UnresolvedType> e : tt.types.entrySet()) {
				Pair<Type,Block> p = resolve(e.getValue());
				types.put(e.getKey(),p.first());
			}
			return new Pair<Type,Block>(Type.T_TUPLE(types),null);
		} else if(t instanceof UnresolvedType.Named) {
			UnresolvedType.Named dt = (UnresolvedType.Named) t;						
			ModuleID mid = dt.attribute(Attribute.Module.class).module;
			if(modules.contains(mid)) {
				return types.get(new NameID(mid,dt.name));
			} else {
				try {
					Module mi = loader.loadModule(mid);
					Module.TypeDef td = mi.type(dt.name);			
					return new Pair<Type,Block>(td.type(),null);
				} catch(ResolveError rex) {
					syntaxError(rex.getMessage(),t,rex);
					return null;
				}
			}
		} else if(t instanceof UnresolvedType.Union) {
			UnresolvedType.Union ut = (UnresolvedType.Union) t;
			HashSet<Type.NonUnion> bounds = new HashSet<Type.NonUnion>();
			for(UnresolvedType b : ut.bounds) {
				Pair<Type,Block> p = resolve(b);
				Type bt = p.first();
				if(bt instanceof Type.NonUnion) {
					bounds.add((Type.NonUnion)bt);
				} else {
					bounds.addAll(((Type.Union)bt).bounds);
				}
			}
			if(bounds.size() == 1) {
				return new Pair<Type,Block>(bounds.iterator().next(),null);
			} else {
				return new Pair<Type,Block>(Type.T_UNION(bounds),null);
			}
		} else {	
			UnresolvedType.Process ut = (UnresolvedType.Process) t;
			Pair<Type,Block> p = resolve(ut.element);
			return new Pair<Type,Block>(Type.T_PROCESS(p.first()),null);			
		} 
	}
	
	protected Type.Fun bindFunction(ModuleID mid, String name,
			Type.Process receiver,
			List<Type> paramTypes,
			SyntacticElement elem) throws ResolveError {
		
		Type.Fun target = Type.T_FUN(receiver, Type.T_ANY,paramTypes);
		Type.Fun candidate = null;
								
		for (Type.Fun ft : lookupMethod(mid,name)) {										
			Type funrec = ft.receiver;
			
			if (receiver == funrec
					|| (receiver != null && funrec != null && Type.isSubtype(
							funrec, receiver))) {
				// receivers match up OK ...
				if (ft.params.size() == paramTypes.size()						
						&& Type.isSubtype(ft, target)
						&& (candidate == null || Type.isSubtype(candidate, ft))) {
					// This declaration is a candidate. Now, we need to see if
					// our
					// candidate type signature is as precise as possible.
					if (candidate == null) {
						candidate = ft;
					} else if (Type.isSubtype(candidate, ft)) {
						candidate = ft;
					}
				}
			}
		}				
		
		return candidate;
	}
	
	protected List<Type.Fun> lookupMethod(ModuleID mid, String name)
			throws ResolveError {
		if (modules.contains(mid)) {
			NameID key = new NameID(mid, name);
			return functions.get(key);
		} else {
			Module module = loader.loadModule(mid);
			ArrayList<Type.Fun> rs = new ArrayList<Type.Fun>();
			for (Module.Method m : module.method(name)) {
				rs.add(m.type());
			}
			return rs;
		}
	}
	
	protected <T extends Type> T checkType(Type t, Class<T> clazz,
			SyntacticElement elem) {
		if(t instanceof Type.Named) {
			t = ((Type.Named)t).type;
		}
		if (clazz.isInstance(t)) {
			return (T) t;
		} else {
			syntaxError("expected type " + clazz.getName() + ", found " + t,
					elem);
			return null;
		}
	}
	
	// Check t1 :> t2
	protected void checkIsSubtype(Type t1, Type t2, SyntacticElement elem) {
		if (!Type.isSubtype(t1, t2)) {
			syntaxError("expected type " + t1 + ", found " + t2, elem);
		}
	}

	/**
	 * The purpose of this method is to making the naming of recursive types a
	 * little more human-readable.
	 * 
	 * @param t
	 * @return
	 */
	public static Type simplifyRecursiveTypes(Type t) {
		Set<String> _names = Type.recursiveTypeNames(t);
		ArrayList<String> names = new ArrayList<String>(_names);
		HashMap<String,String> binding = new HashMap<String,String>();
		
		for(int i=0;i!=names.size();++i) {
			int let = (i+20) % 26;
			int num = i / 26;
			String n = "" + (char) ('A' + let);
			if(num > 0) {
				n += num;
			}
			binding.put(names.get(i), n);
		}
		
		return Type.substituteRecursiveTypes(t, binding);
	}
}
