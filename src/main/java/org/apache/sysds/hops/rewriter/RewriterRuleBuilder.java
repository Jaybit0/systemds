package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class RewriterRuleBuilder {
	private final RuleContext ctx;
	private String ruleName = "?";
	private ArrayList<RewriterInstruction> instrSeq = new ArrayList<>();
	private ArrayList<RewriterInstruction> mappingSeq = new ArrayList<>();
	private HashMap<String, RewriterStatement> globalIds = new HashMap<>();
	private HashMap<String, RewriterStatement> instrSeqIds = new HashMap<>();
	private HashMap<String, RewriterStatement> mappingSeqIds = new HashMap<>();
	private RewriterStatement fromRoot = null;
	private RewriterStatement toRoot = null;
	private boolean isUnidirectional = false;
	private boolean buildSingleDAG = false;

	private RewriterStatement currentStatement = null;
	private boolean mappingState = false;

	public RewriterRuleBuilder(final RuleContext ctx) {
		this.ctx = ctx;
	}

	public RewriterRuleBuilder(final RuleContext ctx, String ruleName) {
		this.ctx = ctx;
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
		if (getCurrentInstruction() != null)
			getCurrentInstruction().consolidate(ctx);
		fromRoot.prepareForHashing();
		toRoot.prepareForHashing();
		fromRoot.recomputeHashCodes();
		toRoot.recomputeHashCodes();
		return new RewriterRule(ctx, ruleName, fromRoot, toRoot, isUnidirectional);
	}

	public RewriterStatement buildDAG() {
		if (!buildSingleDAG)
			throw new IllegalArgumentException("Cannot build a DAG if rule was specified");
		getCurrentInstruction().consolidate(ctx);
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

	public RewriterStatement getCurrentInstruction() {
		if (mappingState)
			if (mappingSeq.size() > 0)
				return mappingSeq.get(mappingSeq.size()-1);
			else if (toRoot != null)
				return toRoot;
			else
				throw new IllegalArgumentException("There is no current instruction in the mapping sequence");
		else
			if (instrSeq.size() > 0)
				return instrSeq.get(instrSeq.size()-1);
			else if (fromRoot != null)
				return fromRoot;
			else
				throw new IllegalArgumentException("There is no current instruction in the instruction sequence");
	}

	public RewriterDataType getCurrentOperand() {
		if (currentStatement instanceof RewriterDataType)
			return (RewriterDataType)currentStatement;
		else
			throw new IllegalArgumentException("The current operand is not a data type");
	}

	public RewriterRuleBuilder withDataType(String id, String type) {
		withDataType(id, type, null);
		return this;
	}

	public RewriterRuleBuilder withDataType(String id, String type, Object literal) {
		if (!instrSeq.isEmpty())
			throw new IllegalArgumentException("To define a single data type, the instruction sequence must be empty");
		fromRoot = new RewriterDataType().ofType(type).asLiteral(literal).as(id);
		storeVar(fromRoot);
		return this;
	}

	public RewriterRuleBuilder withInstruction(String instr) {
		if (mappingState)
			throw new IllegalArgumentException("Cannot add an instruction when a mapping instruction was already defined");
		if (instrSeq.size() > 0)
			getCurrentInstruction().consolidate(ctx);
		instrSeq.add(new RewriterInstruction().withInstruction(instr));
		return this;
	}

	public RewriterRuleBuilder withOps(RewriterDataType... operands) {
		((RewriterInstruction)getCurrentInstruction()).withOps(operands);
		currentStatement = null;
		return this;
	}

	public RewriterRuleBuilder addOp(String id) {
		RewriterDataType dt = new RewriterDataType().as(id);
		storeVar(dt);
		((RewriterInstruction)getCurrentInstruction()).addOp(dt);
		if (currentStatement != null)
			currentStatement.consolidate(ctx);
		currentStatement = dt;
		return this;
	}

	public RewriterRuleBuilder withCostFunction(Function<List<RewriterStatement>, Long> costFunction) {
		((RewriterInstruction)getCurrentInstruction()).withCostFunction(costFunction);
		return this;
	}

	public RewriterRuleBuilder asLiteral(Object literal) {
		getCurrentOperand().asLiteral(literal);
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
			currentStatement.consolidate(ctx);

		currentStatement = operand;
		((RewriterInstruction)getCurrentInstruction()).addOp(operand);

		return this;
	}

	public RewriterRuleBuilder ofType(String type) {
		getCurrentOperand().ofType(type);
		return this;
	}

	public RewriterRuleBuilder toInstruction(String instr) {
		if (buildSingleDAG)
			throw new IllegalArgumentException("Cannot create a mapping instruction when building a single DAG");
		getCurrentInstruction().consolidate(ctx);
		mappingSeq.add(new RewriterInstruction().withInstruction(instr));
		mappingState = true;
		return this;
	}

	public RewriterRuleBuilder toDataType(String id, String type) {
		toDataType(id, type, null);
		return this;
	}

	public RewriterRuleBuilder toDataType(String id, String type, Object literal) {
		if (!mappingSeq.isEmpty())
			throw new IllegalArgumentException("To define a single data type, the mapping sequence must be empty");
		toRoot = new RewriterDataType().ofType(type).asLiteral(literal).as(id);
		storeVar(toRoot);
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
