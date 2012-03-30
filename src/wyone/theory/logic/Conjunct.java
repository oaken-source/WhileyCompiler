// This file is part of the Wyone automated theorem prover.
//
// Wyone is free software; you can redistribute it and/or modify 
// it under the terms of the GNU General Public License as published 
// by the Free Software Foundation; either version 3 of the License, 
// or (at your option) any later version.
//
// Wyone is distributed in the hope that it will be useful, but 
// WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See 
// the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public 
// License along with Wyone. If not, see <http://www.gnu.org/licenses/>
//
// Copyright 2010, David James Pearce. 

package wyone.theory.logic;

import java.util.*;

import wyil.lang.Type;
import static wyone.core.Constructor.*;
import wyone.core.*;
import wyone.util.*;

public final class Conjunct extends Base<Constraint> implements Constraint {	
	
	/**
	 * <p>
	 * Construct a formula from a collection of formulas.
	 * </p>
	 * 
	 * @param clauses
	 */
	public Conjunct(Collection<Constraint> fs) {
		super("&&",fs);
	}			
		
	/**
	 * This method substitutes all variable names for names given in the
	 * binding. If no binding is given for a variable, then it retains its
	 * original name.
	 * 
	 * @param binding
	 * @return
	 */
	public Constraint substitute(Map<Constructor,Constructor> binding) {	
		ArrayList<Constraint> nparams = new ArrayList<Constraint>();		
		boolean pchanged = false;
		boolean composite = true;
		for(Constraint p : subterms) {						
			Constraint np = p.substitute(binding);			
			composite &= np instanceof Value;			
			if(np instanceof Conjunct) {
				Conjunct c = (Conjunct) np;
				nparams.addAll(c.subterms);
				pchanged=true;
			} else if(p==Value.TRUE) {
				pchanged=true;
			} else if(np != p) {						
				nparams.add(np);				
				pchanged=true;	
			} else { 
				nparams.add(p);
			}
		}		
		if(composite) {			
			for(Constructor e : nparams) {				
				Value.Bool b = (Value.Bool) e;
				if(!b.value) {
					return Value.Bool.FALSE;
				}
			}
			return Value.TRUE;
		} else if(nparams.size() == 1) {
			return nparams.iterator().next();
		} else if(pchanged) {
			if(nparams.size() == 0) {
				return Value.TRUE;			
			} else {
				return new Conjunct(nparams);
			}
		} else {
			return this;
		}
	}	
	
	public Disjunct not() {
		ArrayList<Constraint> nparams = new ArrayList<Constraint>();
		for(Constraint p : subterms) {
			Constraint np = p.not();																
			nparams.add(np);											
		}				
		return new Disjunct(nparams);		 
	}
	
	public String toString() {
		boolean firstTime = true;
		String r = "";
		for(Constraint f : subterms) {
			if(!firstTime) {
				r += " && ";
			}
			firstTime=false;
			r += "(" + f + ")";			
		}
		return r;
	}
}
