/*
 [The "BSD license"]
 Copyright (c) 2011 Terence Parr
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.antlr.v4.runtime.atn;

/** A tree node for tracking the call chains for ATNs that invoke
 *  other ATNs.  These trees only have to point upwards to their parents
 *  so we can walk back up the tree (i.e., pop stuff off the stack).  We
 *  never walk from stack down down through the children.
 *
 *  Each alt predicted in a decision has its own context tree,
 *  representing all possible return nodes.  The initial stack has
 *  EOF ("$") in it.  So, for m alternative productions, the lookahead
 *  DFA will have m ATNContext trees.
 *
 *  To "push" a new context, just do "new ATNContext(context-parent, state)"
 *  which will add itself to the parent.  The root is ATNContext(null, null).
 *
 *  The complete context for an ATN configuration is the set of invoking states
 *  on the path from this node thru the parent pointers to the root.
 */
public class ATNContext {
	public ATNContext parent;

    /** The ATN state following state that invoked another rule's start state
	 *  is recorded on the rule invocation context stack.
     */
    public ATNState returnState;

    /** Computing the hashCode is very expensive and closureBusy()
     *  uses it to track when it's seen a state|ctx before to avoid
     *  infinite loops.  As we add new contexts, record the hash code
     *  as this.invokingState + parent.cachedHashCode.  Avoids walking
     *  up the tree for every hashCode().  Note that this caching works
     *  because a context is a monotonically growing tree of context nodes
     *  and nothing on the stack is ever modified...ctx just grows
     *  or shrinks.
     */
    protected int cachedHashCode;

	public ATNContext(ATNContext parent, ATNState returnState) {
        this.parent = parent;
        this.returnState = returnState;
        if ( returnState !=null ) {
            this.cachedHashCode = returnState.stateNumber;
        }
        if ( parent!=null ) {
            this.cachedHashCode += parent.cachedHashCode;
        }
    }

	/** Dup context so we can turn on approximated or whatever */
	public ATNContext(ATNContext proto) {
		this.parent = proto.parent;
		this.returnState = proto.returnState;
		this.cachedHashCode = proto.cachedHashCode;
//		this.approximated = proto.approximated;
	}

	public static ATNContext EMPTY() { return new ATNContext(null, null); }


	/** Is s anywhere in the context? */
	public boolean contains(ATNState s) {
		ATNContext sp = this;
		while ( sp!=null ) {
			if ( sp.returnState == s ) return true;
			sp = sp.parent;
		}
		return false;
	}

	/** Two contexts are equals() if both have
	 *  same call stack; walk upwards to the root.
	 *  Recall that the root sentinel node has no parent.
	 *  Note that you may be comparing contextsv in different alt trees.
	 */
	public boolean equals(Object o) {
		ATNContext other = ((ATNContext)o);
		if ( this.cachedHashCode != other.cachedHashCode ) {
			return false; // can't be same if hash is different
		}
		if ( this==other ) return true;

		// System.out.println("comparing "+this+" with "+other);
		ATNContext sp = this;
		while ( sp.parent!=null && other.parent!=null ) {
			if ( sp.returnState != other.returnState) return false;
			sp = sp.parent;
			other = other.parent;
		}
		if ( !(sp.parent==null && other.parent==null) ) {
			return false; // both pointers must be at their roots after walk
		}
		return true;
	}

	/** [$] suffix any context
	 *  [21 $] suffix [21 12 $]
	 *  [21 12 $] suffix [21 $]
	 *  [21 18 $] suffix [21 18 12 9 $]
	 *  [21 18 12 9 $] suffix [21 18 $]
	 *  [21 12 $] not suffix [21 9 $]
	 *
	 *  Example "[21 $] suffix [21 12 $]" means: rule r invoked current rule
	 *  from state 21.  Rule s invoked rule r from state 12 which then invoked
	 *  current rule also via state 21.  While the context prior to state 21
	 *  is different, the fact that both contexts emanate from state 21 implies
	 *  that they are now going to track perfectly together.  Once they
	 *  converged on state 21, there is no way they can separate.  In other
	 *  words, the prior stack state is not consulted when computing where to
	 *  go in the closure operation.  beta $ and beta alpha $ are considered the same stack.
	 *  If beta is popped off then $ and alpha$ remain; there is now an empty and
	 *  nonempty context comparison.  So, if one stack is a suffix of
	 *  another, then it will still degenerate to the simple empty / nonempty stack
	 *  comparison case.
	 */
	protected boolean suffix(ATNContext other) {
		ATNContext sp = this;
		// if one of the contexts is empty, it never enters loop and returns true
		while ( sp.parent!=null && other.parent!=null ) {
			if ( sp.returnState != other.returnState) {
				return false;
			}
			sp = sp.parent;
			other = other.parent;
		}
		//System.out.println("suffix");
		return true;
	}

	/** Given an ATN state number, how many times does it appear on stack?
	 *  The ATN-to-DFA conversion pushes "return" states as it does
	 *  rule invocations.  The ATN state number must be a rule return state
	 *  (following state from invocation state).
	 */
	public int occurrences(int state) {
		ATNContext sp = this;
		int n = 0; // track recursive invocations of target from this state
		//System.out.println("this.context is "+sp);
		while ( sp.parent!=null ) {
			if ( sp.returnState.stateNumber == state ) {
				n++;
			}
			sp = sp.parent;
		}
		return n;
	}

	public int hashCode() {
		return cachedHashCode; // works with tests; don't recompute.
//		int h = 0;
//		ATNContext sp = this;
//		while ( sp.parent!=null ) {
//			h += sp.returnState.stateNumber;
//			sp = sp.parent;
//		}
//		return h;
	}

	/** How many rule invocations in this context? I.e., how many
	 *  elements in stack (path to root, not including root placeholder)?
	 */
	public int depth() {
		int n = 0;
		ATNContext sp = this;
		while ( !sp.isEmpty() ) {
			n++;
			sp = sp.parent;
		}
		return n;
	}

	/** A context is empty if there is no parent; meaning nobody pushed
	 *  anything on the call stack.
	 */
	public boolean isEmpty() {
		return parent==null;
	}

    public String toString() {
        StringBuffer buf = new StringBuffer();
        ATNContext sp = this;
        buf.append("[");
        while ( sp.parent!=null ) {
            buf.append(sp.returnState.stateNumber);
            buf.append(" ");
            sp = sp.parent;
        }
        buf.append("$]");
        return buf.toString();
    }
}