package org.antlr.v4.runtime.atn;

import org.antlr.v4.analysis.LL1Analyzer;
import org.antlr.v4.automata.ATNSerializer;
import org.antlr.v4.misc.*;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.tool.*;

import java.util.*;

/** */
// TODO: split into runtime / analysis time?
public class ATN {
	public static final int INVALID_ALT_NUMBER = -1;
	public static final int INVALID_DECISION_NUMBER = -1;

	public Grammar g;
	public List<ATNState> states = new ArrayList<ATNState>();
	public List<ATNState> rules = new ArrayList<ATNState>(); // rule index to start state

	/** Each subrule/rule is a decision point and we must track them so we
	 *  can go back later and build DFA predictors for them.  This includes
	 *  all the rules, subrules, optional blocks, ()+, ()* etc...
	 */
	public List<DecisionState> decisionToATNState = new ArrayList<DecisionState>();

	public Map<Rule, RuleStartState> ruleToStartState = new LinkedHashMap<Rule, RuleStartState>();
	public Map<Rule, RuleStopState> ruleToStopState = new LinkedHashMap<Rule, RuleStopState>();
	public Map<String, TokensStartState> modeNameToStartState =
		new LinkedHashMap<String, TokensStartState>();

	// runtime
	public int grammarType; // ANTLRParser.LEXER, ...
	public List<TokensStartState> modeToStartState = new ArrayList<TokensStartState>();

	// runtime for lexer
	public List<Integer> ruleToTokenType = new ArrayList<Integer>();
	public List<Integer> ruleToActionIndex = new ArrayList<Integer>();

	public int maxTokenType;

	int stateNumber = 0;

	// TODO: for runtime all we need is states, decisionToATNState I think

	public ATN(Grammar g) {
		this.g = g;
		if ( g.isLexer() ) {
			ruleToTokenType.add(0); // no rule index 0
			ruleToActionIndex.add(0); // no action index 0
			for (Rule r : g.rules.values()) {
				ruleToTokenType.add(g.getTokenType(r.name));
				if ( r.actionIndex>0 ) ruleToActionIndex.add(r.actionIndex);
				else ruleToActionIndex.add(0);
			}
		}
	}

	/** Used for runtime deserialization of ATNs from strings */
	public ATN() { }

	public IntervalSet nextTokens(RuleContext ctx) {
		return nextTokens(ctx.s, ctx);
	}

	public IntervalSet nextTokens(int stateNumber, RuleContext ctx) {
		ATNState s = states.get(stateNumber);
		if ( s == null ) return null;
		LL1Analyzer anal = new LL1Analyzer(this);
		IntervalSet next = anal.LOOK(s, ctx);
		return next;
	}

	public void addState(ATNState state) {
		state.atn = this;
		states.add(state);
		state.stateNumber = stateNumber++;
	}

	public int defineDecisionState(DecisionState s) {
		decisionToATNState.add(s);
		s.decision = decisionToATNState.size()-1;
		return s.decision;
	}

	public int getNumberOfDecisions() {
		return decisionToATNState.size();
	}

	/** Used by Java target to encode short/int array as chars in string. */
	public String getSerializedAsString() {
		return new String(Utils.toCharArray(getSerialized()));
	}

	public List<Integer> getSerialized() {
		return new ATNSerializer(this).serialize();
	}

	public char[] getSerializedAsChars() {
		return Utils.toCharArray(new ATNSerializer(this).serialize());
	}

	public String getDecoded() {
		return new ATNSerializer(this).decode(Utils.toCharArray(getSerialized()));
	}

}