package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewriterUtils {
	public static Function<RewriterStatement, Boolean> propertyExtractor(final List<String> desiredProperties, final RuleContext ctx) {
		return el -> {
			if (el instanceof RewriterInstruction) {
				Set<String> properties = ((RewriterInstruction) el).getProperties(ctx);
				String trueInstr = ((RewriterInstruction)el).trueTypedInstruction(ctx);
				//if (properties != null) {
					for (String desiredProperty : desiredProperties) {
						if (trueInstr.equals(desiredProperty) || (properties != null && properties.contains(desiredProperty))) {
							System.out.println("Found property: " + desiredProperty + " (for " + el + ")");
							String oldInstr = ((RewriterInstruction) el).changeConsolidatedInstruction(desiredProperty, ctx);
							if (el.getMeta("trueInstr") == null) {
								el.unsafePutMeta("trueInstr", oldInstr);
								el.unsafePutMeta("trueName", oldInstr);
							}
							break;
							//System.out.println("Property found: " + desiredProperty);
						}
					}
				//}
			}
			return true;
		};
	}

	public static BiFunction<RewriterStatement, RuleContext, String> binaryStringRepr(String op) {
		return (stmt, ctx) -> {
			List<RewriterStatement> operands = ((RewriterInstruction)stmt).getOperands();
			return operands.get(0).toString(ctx) + op + operands.get(1).toString(ctx);
		};
	}

	public static void mergeArgLists(RewriterInstruction stmt, final RuleContext ctx) {
		Set<String> ops = new HashSet<>();

		for (Map.Entry<String, HashSet<String>> p : ctx.instrProperties.entrySet()) {
			if (p.getValue().contains("FusedOperator(MATRIX...)")) {
				//String fName = p.getKey().substring(0, p.getKey().indexOf('('));
				ops.add(p.getKey());
			}
		}

		stmt.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(new ArrayList<>(ops), ctx));

		stmt.forEachPostOrderWithDuplicates(el -> {
			if (!(el instanceof RewriterInstruction))
				return true;
			RewriterInstruction e = (RewriterInstruction) el;
			String ts = e.typedInstruction(ctx);

			if (ops.contains(ts) && e.getOperands() != null) {
				for (int idx = 0; idx < e.getOperands().size(); idx++) {
					RewriterStatement stmt2 = e.getOperands().get(idx);
					if (stmt2.isArgumentList()) {
						for (int idx2 = 0; idx2 < stmt2.getOperands().size(); idx2++) {
							RewriterStatement stmt3 = stmt2.getOperands().get(idx2);
							if (stmt3 instanceof RewriterInstruction && ((RewriterInstruction)stmt3).typedInstruction(ctx).equals(ts)) {
								if (stmt3.getOperands() != null && stmt3.getOperands().size() > 0 && stmt3.getOperands().size() == 1) {
									RewriterStatement stmt4 = stmt3.getOperands().get(0);
									if (stmt4.isArgumentList()) {
										stmt2.getOperands().remove(idx);
										stmt2.getOperands().addAll(idx, stmt4.getOperands());
									}
								}

							}
						}
					}
				}
			}

			return true;
		});
	}

	public static RewriterStatement parse(String expr, final RuleContext ctx, String... varDefinitions) {
		HashMap<String, RewriterStatement> dataTypes = new HashMap<>();

		for (String def : varDefinitions)
			parseDataTypes(def, dataTypes, ctx);

		return parseExpression(expr, new HashMap<>(), dataTypes, ctx);
	}

	/**
	 * Parses an expression
	 * @param expr the expression string. Note that all whitespaces have to already be removed
	 * @param refmap
	 * @return
	 */
	public static RewriterStatement parseExpression(String expr, HashMap<Integer, RewriterStatement> refmap, HashMap<String, RewriterStatement> dataTypes, final RuleContext ctx) {
		RuleContext.currentContext = ctx;
		expr = expr.replaceAll("\\s+", "");
		MutableObject<String> mexpr = new MutableObject<>(expr);
		return doParseExpression(mexpr, refmap, dataTypes, ctx);
	}

	private static RewriterStatement doParseExpression(MutableObject<String> mexpr, HashMap<Integer, RewriterStatement> refmap, HashMap<String, RewriterStatement> dataTypes, final RuleContext ctx) {
		String expr = mexpr.getValue();
		if (expr.startsWith("$")) {
			expr = expr.substring(1);
			Pattern pattern = Pattern.compile("^\\d+");
			Matcher matcher = pattern.matcher(expr);

			if (matcher.find()) {
				String number = matcher.group();
				int n = Integer.parseInt(number);
				if (expr.charAt(matcher.end()) != ':')
					throw new IllegalArgumentException("Expected the token ':'");
				String remainder = expr.substring(matcher.end() + 1);
				mexpr.setValue(remainder);
				RewriterStatement stmt = parseRawExpression(mexpr, refmap, dataTypes, ctx);
				refmap.put(n, stmt);
				return stmt;
			} else {
				throw new IllegalArgumentException("Expected a number");
			}
		} else {
			return parseRawExpression(mexpr, refmap, dataTypes, ctx);
		}
	}

	public static boolean parseDataTypes(String expr, HashMap<String, RewriterStatement> dataTypes, final RuleContext ctx) {
		RuleContext.currentContext = ctx;
		Pattern pattern = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
		Matcher matcher = pattern.matcher(expr);

		if (!matcher.find())
			return false;

		String dType = matcher.group();

		if (expr.charAt(matcher.end()) != ':')
			return false;

		expr = expr.substring(matcher.end() + 1);

		matcher = pattern.matcher(expr);

		while (matcher.find()) {
			String varName = matcher.group();

			RewriterDataType dt = new RewriterDataType().as(varName).ofType(dType);
			dt.consolidate(ctx);

			dataTypes.put(varName, dt);

			if (expr.length() == matcher.end())
				return true;

			if (expr.charAt(matcher.end()) != ',')
				return false;

			expr = expr.substring(matcher.end()+1);
			matcher = pattern.matcher(expr);
		}

		return false;
	}

	private static RewriterStatement parseRawExpression(MutableObject<String> mexpr, HashMap<Integer, RewriterStatement> refmap, HashMap<String, RewriterStatement> dataTypes, final RuleContext ctx) {
		String expr = mexpr.getValue();

		Pattern pattern = Pattern.compile("^[^(),:]+");
		Matcher matcher = pattern.matcher(expr);



		if (matcher.find()) {
			String token = matcher.group();
			String remainder = expr.substring(matcher.end());

			char nextChar = remainder.charAt(0);

			switch (nextChar) {
				case '(':
					// Then this is a function
					if (remainder.charAt(1) == ')') {
						RewriterInstruction mInstr = new RewriterInstruction().withInstruction(token).as(UUID.randomUUID().toString());
						mInstr.consolidate(ctx);
						mexpr.setValue(remainder.substring(2));
						return mInstr;
					} else {
						List<RewriterStatement> opList = new ArrayList<>();
						mexpr.setValue(remainder.substring(1));
						RewriterStatement cstmt = doParseExpression(mexpr, refmap, dataTypes, ctx);
						opList.add(cstmt);

						while (mexpr.getValue().charAt(0) == ',') {
							mexpr.setValue(mexpr.getValue().substring(1));
							cstmt = doParseExpression(mexpr, refmap, dataTypes, ctx);
							opList.add(cstmt);
						}

						if (mexpr.getValue().charAt(0) != ')')
							throw new IllegalArgumentException(mexpr.getValue());

						mexpr.setValue(mexpr.getValue().substring(1));
						RewriterInstruction instr = new RewriterInstruction().withInstruction(token).withOps(opList.toArray(RewriterStatement[]::new)).as(UUID.randomUUID().toString());
						instr.consolidate(ctx);
						return instr;
					}
				case ')':
				case ',':
					mexpr.setValue(remainder);
					if (dataTypes.containsKey(token))
						return dataTypes.get(token);
					throw new IllegalArgumentException("DataType: '" + token + "' doesn't exist");
				default:
					throw new NotImplementedException();
			}
		} else {
			throw new IllegalArgumentException(mexpr.getValue());
		}
	}
}
