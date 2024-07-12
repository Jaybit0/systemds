package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RewriterInstruction implements RewriterStatement {
	private static final HashMap<String, Function<List<RewriterStatement>, Long>> instrCosts = new HashMap<>()
	{
		{
			put("+(float,float)", d -> 1l);
			put("*(float,float)", d -> 1l);
		}
	};

	private static final HashMap<String, String> instrTypes = new HashMap<>()
	{
		{
			put("+(float,float)", "float");
			put("*(float,float)", "float");
		}
	};

	private String instr;
	private RewriterDataType result = new RewriterDataType();
	private ArrayList<RewriterStatement> operands = new ArrayList<>();
	private Function<List<RewriterStatement>, Long> costFunction = null;
	private boolean consolidated = false;
	private HashMap<RewriterStatement, RewriterStatement> links = null;

	@Override
	public String getId() {
		return result.getId();
	}

	@Override
	public String getResultingDataType() {
		return result.getResultingDataType();
	}

	@Override
	public boolean isLiteral() {
		return false;
	}

	@Override
	public void consolidate() {
		if (consolidated)
			return;

		if (instr == null || instr.isEmpty())
			throw new IllegalArgumentException("Instruction type cannot be empty");

		if (getCostFunction() == null)
			throw new IllegalArgumentException("Could not find a matching cost function for " + typedInstruction());

		for (RewriterStatement operand : operands)
			operand.consolidate();

		getResult().consolidate();

		consolidated = true;
	}

	@Override
	public boolean isConsolidated() {
		return consolidated;
	}

	@Override
	public boolean match(RewriterStatement stmt, HashMap<RewriterStatement, RewriterStatement> dependencyMap, HashMap<RewriterStatement, RewriterStatement> links) {
		if (stmt instanceof RewriterInstruction
				&& getResultingDataType().equals(stmt.getResultingDataType())) {
			RewriterInstruction inst = (RewriterInstruction)stmt;
			if(!inst.instr.equals(this.instr))
				return false;
			if (this.operands.size() != inst.operands.size())
				return false;

			int s = inst.operands.size();

			for (int i = 0; i < s; i++) {
				if (!links.getOrDefault(operands.get(i), operands.get(i)).match(inst.operands.get(i), dependencyMap, links))
					return false;
			}

			return true;
		}

		return false;
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

	public RewriterInstruction withLinks(HashMap<RewriterStatement, RewriterStatement> links) {
		this.links = links;
		return this;
	}

	public HashMap<RewriterStatement, RewriterStatement> getLinks() {
		return links;
	}

	public ArrayList<RewriterStatement> getOperands() {
		return operands;
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
		this.operands.add(new RewriterDataType().withId(id));
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

	public Function<List<RewriterStatement>, Long> getCostFunction() {
		if (this.costFunction == null)
			this.costFunction = instrCosts.get(typedInstruction());

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

	public RewriterInstruction as(String id) {
		if (consolidated)
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");
		this.result.withId(id);
		return this;
	}

	public RewriterDataType getResult() {
		if (this.result.getType() == null) {
			String type = instrTypes.get(typedInstruction());

			if (type == null)
				throw new IllegalArgumentException("Type mapping cannot be found for instruction: " + type);

			this.result.ofType(type);
		}

		return this.result;
	}

	public String typedInstruction() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.instr);
		builder.append("(");

		if (!operands.isEmpty())
			builder.append(operands.get(0).getResultingDataType());

		for (int i = 1; i < operands.size(); i++) {
			builder.append(",");
			builder.append(operands.get(i).getResultingDataType());
		}
		builder.append(")");
		return builder.toString();
	}

	@Override
	public String toStringWithLinking(HashMap<RewriterStatement, RewriterStatement> links) {
		StringBuilder builder = new StringBuilder();
		if (operands.size() == 2) {
			builder.append("(");
			builder.append(links.getOrDefault(operands.get(0), operands.get(0)).toStringWithLinking(links));
			builder.append(" ");
			builder.append(instr);
			builder.append(" ");
			builder.append(links.getOrDefault(operands.get(1), operands.get(1)).toStringWithLinking(links));
			builder.append(")");
			return builder.toString();
		}

		builder.append(instr);
		builder.append("(");
		for (int i = 0; i < operands.size(); i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(links.getOrDefault(operands.get(i), operands.get(i)).toStringWithLinking(links));
		}
		builder.append(")");
		return builder.toString();
	}

	public String toString() {
		if (links != null)
			return toStringWithLinking(links);

		if (operands.size() == 2)
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

}
