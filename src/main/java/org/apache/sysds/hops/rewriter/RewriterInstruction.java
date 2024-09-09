package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class RewriterInstruction extends RewriterStatement {
	private static final HashSet<String> writeAsBinaryInstruction = new HashSet<>() {
		{
			add("+");
			add("*");
		}
	};

	private String instr;
	private RewriterDataType result = new RewriterDataType();
	private ArrayList<RewriterStatement> operands = new ArrayList<>();
	private Function<List<RewriterStatement>, Long> costFunction = null;
	private boolean consolidated = false;
	private int hashCode;
	//private DualHashBidiMap<RewriterStatement, RewriterStatement> links = null;

	@Override
	public String getId() {
		return result.getId();
	}

	@Override
	public String getResultingDataType(final RuleContext ctx) {
		return getResult(ctx).getResultingDataType(ctx);
	}

	@Override
	public boolean isLiteral() {
		return false;
	}

	@Override
	public Object getLiteral() {
		return null;
	}

	@Override
	public void consolidate(final RuleContext ctx) {
		if (consolidated)
			return;

		if (instr == null || instr.isEmpty())
			throw new IllegalArgumentException("Instruction type cannot be empty");

		if (getCostFunction(ctx) == null)
			throw new IllegalArgumentException("Could not find a matching cost function for " + typedInstruction(ctx));

		for (RewriterStatement operand : operands)
			operand.consolidate(ctx);

		getResult(ctx).consolidate(ctx);

		hashCode = Objects.hash(rid, refCtr, instr, result, operands);
		consolidated = true;
	}
	@Override
	public int recomputeHashCodes() {
		result.recomputeHashCodes();
		operands.forEach(RewriterStatement::recomputeHashCodes);
		hashCode = Objects.hash(rid, refCtr, instr, result, operands);
		return hashCode;
	}

	@Override
	public boolean isConsolidated() {
		return consolidated;
	}

	@Override
	public boolean match(final RuleContext ctx, RewriterStatement stmt, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues) {
		//System.out.println(stmt.toStringWithLinking(links) + "::" + link.stmt.getResultingDataType());
		if (stmt instanceof RewriterInstruction
				&& getResultingDataType(ctx).equals(stmt.getResultingDataType(ctx))) {
			RewriterInstruction inst = (RewriterInstruction)stmt;

			if(!inst.instr.equals(this.instr))
				return false;
			if (this.operands.size() != inst.operands.size())
				return false;

			int s = inst.operands.size();

			for (int i = 0; i < s; i++) {
				if (!operands.get(i).match(ctx, inst.operands.get(i), dependencyMap, literalsCanBeVariables, ignoreLiteralValues)) {
					return false;
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public RewriterStatement copyNode() {
		RewriterInstruction mCopy = new RewriterInstruction();
		mCopy.instr = instr;
		mCopy.result = (RewriterDataType)result.copyNode();
		mCopy.operands = new ArrayList<>(operands);
		mCopy.costFunction = costFunction;
		mCopy.consolidated = consolidated;
		return mCopy;
	}

	@Override
	public RewriterStatement nestedCopyOrInject(Map<RewriterStatement, RewriterStatement> copiedObjects, Function<RewriterStatement, RewriterStatement> injector) {
		RewriterStatement mCpy = copiedObjects.get(this);
		if (mCpy != null)
			return mCpy;
		mCpy = injector.apply(this);
		if (mCpy != null) {
			// Then change the reference to the injected object
			copiedObjects.put(this, mCpy);
			return mCpy;
		}


		RewriterInstruction mCopy = new RewriterInstruction();
		mCopy.instr = instr;
		mCopy.result = (RewriterDataType)result.copyNode();
		mCopy.costFunction = costFunction;
		mCopy.consolidated = consolidated;
		mCopy.operands = new ArrayList<>(operands.size());
		copiedObjects.put(this, mCopy);
		operands.forEach(op -> mCopy.operands.add(op.nestedCopyOrInject(copiedObjects, injector)));

		return mCopy;
	}

	@Override
	public RewriterStatement clone() {
		RewriterInstruction mClone = new RewriterInstruction();
		mClone.instr = instr;
		mClone.result = (RewriterDataType)result.clone();
		ArrayList<RewriterStatement> clonedOperands = new ArrayList<>(operands.size());

		for (RewriterStatement stmt : operands)
			clonedOperands.add(stmt.clone());

		mClone.operands = clonedOperands;
		mClone.costFunction = costFunction;
		mClone.consolidated = consolidated;
		return mClone;
	}

	public void injectData(final RuleContext ctx, RewriterInstruction origData) {
		instr = origData.instr;
		result = (RewriterDataType)origData.getResult(ctx).copyNode();
		operands = new ArrayList<>(origData.operands);
		costFunction = origData.costFunction;
	}

	/*public RewriterInstruction withLinks(DualHashBidiMap<RewriterStatement, RewriterStatement> links) {
		this.links = links;
		return this;
	}

	public DualHashBidiMap<RewriterStatement, RewriterStatement> getLinks() {
		return links;
	}*/

	@Override
	public ArrayList<RewriterStatement> getOperands() {
		return operands;
	}


	@Override
	public RewriterStatement simplify(final RuleContext ctx) {
		for (int i = 0; i < operands.size(); i++) {
			RewriterStatement stmt = operands.get(i).simplify(ctx);
			if (stmt != null)
				operands.set(i, stmt);
		}

		Function<RewriterInstruction, RewriterStatement> rule = ctx.simplificationRules.get(typedInstruction(ctx));
		if (rule != null) {
			RewriterStatement stmt = rule.apply(this);

			if (stmt != null)
				return stmt;
		}
		return this;
	}

	public RewriterInstruction withInstruction(String instr) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.instr = instr;
		return this;
	}

	public RewriterInstruction withOps(RewriterStatement... operands) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.operands = new ArrayList<>(Arrays.asList(operands));
		return this;
	}

	public RewriterInstruction addOp(String id) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.operands.add(new RewriterDataType().as(id));
		return this;
	}

	public RewriterInstruction addOp(RewriterStatement operand) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.operands.add(operand);
		return this;
	}

	public RewriterInstruction ofType(String type) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		RewriterStatement stmt = this.operands.get(this.operands.size()-1);

		if (stmt instanceof RewriterDataType)
			((RewriterDataType)stmt).ofType(type);
		else
			throw new IllegalArgumentException("Can only set the data type of RewriterDataType class");

		return this;
	}

	public Function<List<RewriterStatement>, Long> getCostFunction(final RuleContext ctx) {
		if (this.costFunction == null)
			this.costFunction = ctx.instrCosts.get(typedInstruction(ctx));

		return this.costFunction;
	}

	public RewriterInstruction withCostFunction(Function<List<RewriterStatement>, Long> costFunction) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.costFunction = costFunction;
		return this;
	}

	public Optional<RewriterStatement> findOperand(String id) {
		return this.operands.stream().filter(op -> op.getId().equals(id)).findFirst();
	}

	@Override
	public RewriterInstruction as(String id) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.result.as(id);
		return this;
	}

	public RewriterDataType getResult(final RuleContext ctx) {
		if (this.result.getType() == null) {
			String type = ctx.instrTypes.get(typedInstruction(ctx));

			if (type == null)
				throw new IllegalArgumentException("Type mapping cannot be found for instruction: " + type);

			this.result.ofType(type);
		}

		return this.result;
	}

	public String typedInstruction(final RuleContext ctx) {
		StringBuilder builder = new StringBuilder();
		builder.append(this.instr);
		builder.append("(");

		if (!operands.isEmpty())
			builder.append(operands.get(0).getResultingDataType(ctx));

		for (int i = 1; i < operands.size(); i++) {
			builder.append(",");
			builder.append(operands.get(i).getResultingDataType(ctx));
		}
		builder.append(")");
		return builder.toString();
	}

	/*public String linksToString() {
		if (links == null)
			return "Links: []";

		StringBuilder sb = new StringBuilder();
		sb.append("Links: \n");
		for (Map.Entry<RewriterStatement, RewriterStatement> link : links.entrySet()) {
			sb.append(" - " + link.getKey().toString() + " -> " + link.getValue().toStringWithLinking(links) + "\n");
		}

		sb.append("\n");

		return sb.toString();
	}*/

	/*@Override
	public String toStringWithLinking(int dagId, DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links) {
		StringBuilder builder = new StringBuilder();
		if (operands.size() == 2) {
			builder.append("(");
			RewriterStatementLink link = RewriterStatement.resolveNode(new RewriterStatementLink(operands.get(0), dagId), links);
			builder.append(link.stmt.toStringWithLinking(link.dagID, links));
			builder.append(" ");
			builder.append(instr);
			builder.append(" ");
			link = RewriterStatement.resolveNode(new RewriterStatementLink(operands.get(1), dagId), links);
			builder.append(link.stmt.toStringWithLinking(link.dagID, links));
			builder.append(")");
			return builder.toString();
		}

		builder.append(instr);
		builder.append("(");
		for (int i = 0; i < operands.size(); i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(RewriterStatement.resolveNode(operands.get(i), links).toStringWithLinking(links));
		}
		builder.append(")");
		return builder.toString();
	}*/

	public String toString() {
		/*if (links != null)
			return toStringWithLinking(links);*/

		if (operands.size() == 2 && writeAsBinaryInstruction.contains(instr))
			return "(" + operands.get(0) + " " + instr + " " + operands.get(1) + ")";

		StringBuilder builder = new StringBuilder();
		builder.append(instr);
		builder.append("(");
		for (int i = 0; i < operands.size(); i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(operands.get(i));
		}
		builder.append(")");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public long getCost() {
		if (costFunction == null)
			throw new NullPointerException("No cost function has been defined for the instruction: '" + instr + "'");
		long cost = costFunction.apply(operands);
		for (RewriterStatement stmt : operands)
			cost += stmt.getCost();
		return cost;
	}

}
