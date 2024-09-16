package org.apache.sysds.hops.rewriter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public class RuleContext {
	public static RuleContext currentContext;

	public HashMap<String, Function<List<RewriterStatement>, Long>> instrCosts = new HashMap<>();

	public HashMap<String, String> instrTypes = new HashMap<>();

	public HashMap<String, Function<RewriterInstruction, RewriterStatement>> simplificationRules = new HashMap<>();

	public HashMap<String, HashSet<String>> instrProperties = new HashMap<>();

	public HashMap<String, Function<RewriterStatement, String>> customStringRepr = new HashMap<>();

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

	public static RuleContext createContext(String contextString) {
		RuleContext ctx = new RuleContext();
		HashMap<String, String> instrTypes = ctx.instrTypes;
		HashMap<String, HashSet<String>> instrProps = ctx.instrProperties;
		String[] lines = contextString.split("\n");
		String fName = null;
		String fArgTypes = null;
		String fReturnType = null;
		for (String line : lines) {
			line = line.replaceFirst("^\\s+", "");
			if (line.isEmpty())
				continue;

			if (line.startsWith("impl")) {
				if (fArgTypes == null || fReturnType == null)
					throw new IllegalArgumentException();
				String newFName = line.substring(4).replace(" ", "");
				if (newFName.isEmpty())
					throw new IllegalArgumentException();

				instrTypes.put(newFName + fArgTypes, fReturnType);

				final String propertyFunction = fName + fArgTypes + "::" + fReturnType;

				if (instrProps.containsKey(newFName))
					instrProps.get(newFName).add(propertyFunction);
				else {
					HashSet<String> mset = new HashSet<>();
					mset.add(propertyFunction);
					instrProps.put(newFName, mset);
				}

				ctx.instrCosts.put(newFName + fArgTypes, d -> 1L);
			} else {
				String[] keyVal = readFunctionDefinition(line);
				fName = keyVal[0];
				fArgTypes = keyVal[1];
				fReturnType = keyVal[2];
				instrTypes.put(fName + fArgTypes, fReturnType);
				ctx.instrCosts.put(fName + fArgTypes, d -> 1L);
			}
		}

		return ctx;
	}

	public static String[] readFunctionDefinition(String line) {
		int leftParanthesisIdx = line.indexOf('(');

		if (leftParanthesisIdx == -1)
			throw new IllegalArgumentException();

		String fName = line.substring(0, leftParanthesisIdx).replace(" ", "");
		String rest = line.substring(leftParanthesisIdx+1);

		int parenthesisCloseIdx = rest.indexOf(')');

		if (parenthesisCloseIdx == -1)
			throw new IllegalArgumentException();

		String argsStr = rest.substring(0, parenthesisCloseIdx);
		String[] args = argsStr.split(",");

		args = Arrays.stream(args).map(arg -> arg.replace(" ", "")).toArray(String[]::new);

		if (Arrays.stream(args).anyMatch(String::isEmpty))
			throw new IllegalArgumentException();

		if (!rest.substring(parenthesisCloseIdx+1, parenthesisCloseIdx+3).equals("::"))
			throw new IllegalArgumentException();

		String returnDataType = rest.substring(parenthesisCloseIdx+3);
		return new String[] { fName, "(" + String.join(",", args) + ")", returnDataType };
	}
}
