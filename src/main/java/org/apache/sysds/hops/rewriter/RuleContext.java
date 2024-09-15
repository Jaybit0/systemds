package org.apache.sysds.hops.rewriter;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class RuleContext {
	public HashMap<String, Function<List<RewriterStatement>, Long>> instrCosts = new HashMap<>();

	public HashMap<String, String> instrTypes = new HashMap<>();

	public HashMap<String, Function<RewriterInstruction, RewriterStatement>> simplificationRules = new HashMap<>();

	public static RuleContext floatArithmetic = new RuleContext();
	public static RuleContext selectionPushdownContext = new RuleContext();

	static {
		floatArithmetic.instrCosts.put("+(float,float)", d -> 1l);
		floatArithmetic.instrCosts.put("*(float,float)", d ->  1l);

		floatArithmetic.instrTypes.put("+(float,float)", "float");
		floatArithmetic.instrTypes.put("*(float,float)", "float");

		floatArithmetic.simplificationRules.put("+(float,float)", i -> {
			RewriterStatement op1 = i.getOperands().get(0);
			RewriterStatement op2 = i.getOperands().get(1);

			if (op1.isLiteral() && op2.isLiteral()) {
				op1.setLiteral(((Float)op1.getLiteral()) + ((Float)op2.getLiteral()));
				return op1;
			}

			return null;
		});
		floatArithmetic.simplificationRules.put("*(float, float)", i -> {
			RewriterStatement op1 = i.getOperands().get(0);
			RewriterStatement op2 = i.getOperands().get(1);

			if (op1.isLiteral() && op2.isLiteral()) {
				op1.setLiteral(((Float)op1.getLiteral()) * ((Float)op2.getLiteral()));
				return op1;
			}

			return null;
		});

		selectionPushdownContext.instrCosts.put("RowSelectPushableBinaryInstruction(MATRIX,MATRIX)", d -> 1l); // Just temporary costs
		selectionPushdownContext.instrTypes.put("RowSelectPushableBinaryInstruction(MATRIX,MATRIX)", "MATRIX");
		selectionPushdownContext.instrCosts.put("rowSelect(MATRIX,INT,INT)", d -> 1l);
		selectionPushdownContext.instrTypes.put("rowSelect(MATRIX,INT,INT)", "MATRIX");
		selectionPushdownContext.instrCosts.put("min(INT,INT)", d -> 1l);
		selectionPushdownContext.instrTypes.put("min(INT,INT)", "INT");
		selectionPushdownContext.instrCosts.put("max(INT,INT)", d -> 1l);
		selectionPushdownContext.instrTypes.put("max(INT,INT)", "INT");

		selectionPushdownContext.instrCosts.put("+(MATRIX,MATRIX)", d -> 1l);
		selectionPushdownContext.instrTypes.put("+(MATRIX,MATRIX)", "MATRIX");
	}
}
