package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class RewriterRuleBuilder {
	private String ruleName = "?";
	private ArrayList<RewriterInstruction> instrSeq = new ArrayList<>();
	private ArrayList<RewriterInstruction> mappingSeq = new ArrayList<>();
	private HashMap<String, RewriterStatement> globalIds = new HashMap<>();
	private HashMap<String, RewriterStatement> instrSeqIds = new HashMap<>();
	private HashMap<String, RewriterStatement> mappingSeqIds = new HashMap<>();
	private RewriterInstruction fromRoot = null;
	private RewriterInstruction toRoot = null;
	private boolean isUnidirectional = false;
	private boolean buildSingleDAG = false;

	private RewriterStatement currentStatement = null;
	private boolean mappingState = false;

	public RewriterRuleBuilder() {

	}

	public RewriterRuleBuilder(String ruleName) {
		this.ruleName = ruleName;
	}

	public RewriterRule build() {
		if (buildSingleDAG)
			throw new IllegalArgumentException("Cannot build a rule if DAG was specified");
		if (!mappingState)
			throw new IllegalArgumentException("No mapping expression");
		if (fromRoot == null)
			throw new IllegalArgumentException("From-root statement cannot be null");
		if (toRoot == null)
			throw new IllegalArgumentException("To-root statement cannot be null");
		getCurrentInstruction().consolidate();
		fromRoot.prepareForHashing();
		toRoot.prepareForHashing();
		fromRoot.recomputeHashCodes();
		toRoot.recomputeHashCodes();
		return new RewriterRule(ruleName, fromRoot, toRoot, isUnidirectional);
	}

	public RewriterInstruction buildDAG() {
		if (!buildSingleDAG)
			throw new IllegalArgumentException("Cannot build a DAG if rule was specified");
		getCurrentInstruction().consolidate();
		fromRoot.prepareForHashing();
		fromRoot.recomputeHashCodes();
		return fromRoot;
	}

	public RewriterRuleBuilder asDAGBuilder() {
		buildSingleDAG = true;
		return this;
	}

	public RewriterRuleBuilder setUnidirectional(boolean unidirectional) {
		this.isUnidirectional = unidirectional;
		return this;
	}

	public RewriterInstruction getCurrentInstruction() {
		if (mappingState)
			if (mappingSeq.size() > 0)
				return mappingSeq.get(mappingSeq.size()-1);
			else
				throw new IllegalArgumentException("There is no current instruction in the mapping sequence");
		else
			if (instrSeq.size() > 0)
				return instrSeq.get(instrSeq.size()-1);
			else
				throw new IllegalArgumentException("There is no current instruction in the instruction sequence");
	}

	public RewriterDataType getCurrentOperand() {
		if (currentStatement instanceof RewriterDataType)
			return (RewriterDataType)currentStatement;
		else
			throw new IllegalArgumentException("The current operand is not a data type");
	}

	public RewriterRuleBuilder withInstruction(String instr) {
		if (mappingState)
			throw new IllegalArgumentException("Cannot add an instruction when a mapping instruction was already defined");
		if (instrSeq.size() > 0)
			getCurrentInstruction().consolidate();
		instrSeq.add(new RewriterInstruction().withInstruction(instr));
		return this;
	}

	public RewriterRuleBuilder withOps(RewriterDataType... operands) {
		getCurrentInstruction().withOps(operands);
		currentStatement = null;
		return this;
	}

	public RewriterRuleBuilder addOp(String id) {
		RewriterDataType dt = new RewriterDataType().withId(id);
		storeVar(dt);
		getCurrentInstruction().addOp(dt);
		if (currentStatement != null)
			currentStatement.consolidate();
		currentStatement = dt;
		return this;
	}

	public RewriterRuleBuilder withCostFunction(Function<List<RewriterStatement>, Long> costFunction) {
		getCurrentInstruction().withCostFunction(costFunction);
		return this;
	}

	public RewriterRuleBuilder as(String id) {
		getCurrentInstruction().as(id);
		currentVars().put(id, getCurrentInstruction());
		storeVar(getCurrentInstruction());
		return this;
	}

	public RewriterRuleBuilder asRootInstruction() {
		if (mappingState) {
			if (toRoot != null)
				throw new IllegalArgumentException("Cannot have more than one root instruction");
			toRoot = getCurrentInstruction().as("result");
		} else {
			if (fromRoot != null)
				throw new IllegalArgumentException("Cannot have more than one root instruction");
			fromRoot = getCurrentInstruction().as("result");
			/*if (buildSingleDAG)
				fromRoot.withLinks(new DualHashBidiMap<>());*/
		}
		return this;
	}

	public RewriterRuleBuilder addExistingOp(String id) {
		RewriterStatement operand = findVar(id);

		if (operand == null)
			throw new IllegalArgumentException("Operand with id '" + id + "' does not exist");

		if (currentStatement != null)
			currentStatement.consolidate();

		currentStatement = operand;
		getCurrentInstruction().addOp(operand);

		return this;
	}

	public RewriterRuleBuilder ofType(String type) {
		getCurrentOperand().ofType(type);
		return this;
	}

	public RewriterRuleBuilder toInstruction(String instr) {
		if (buildSingleDAG)
			throw new IllegalArgumentException("Cannot create a mapping instruction when building a single DAG");
		getCurrentInstruction().consolidate();
		mappingSeq.add(new RewriterInstruction().withInstruction(instr));
		mappingState = true;
		return this;
	}

	private HashMap<String, RewriterStatement> currentVars() {
		return mappingState ? mappingSeqIds : instrSeqIds;
	}

	private RewriterStatement findVar(String id) {
		RewriterStatement stmt = null;

		if (mappingState) {
			stmt = mappingSeqIds.get(id);
			if (stmt != null)
				return stmt;
		} else {
			stmt = instrSeqIds.get(id);
			if (stmt != null)
				return stmt;
		}
		return globalIds.get(id);
	}

	private void storeVar(RewriterStatement var) {
		if (var.getId() == null)
			throw new IllegalArgumentException("The id of a statement cannot be null!");

		if (mappingState) {
			mappingSeqIds.put(var.getId(), var);
		} else {
			if (var instanceof RewriterDataType)
				globalIds.put(var.getId(), var);
			else
				instrSeqIds.put(var.getId(), var);
		}
	}
}
