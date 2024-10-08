package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewriterUtils {
	public static String typedToUntypedInstruction(String instr) {
		return instr.substring(0, instr.indexOf('('));
	}

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
			String op1Str = operands.get(0).toString(ctx);
			if (operands.get(0) instanceof RewriterInstruction && ((RewriterInstruction)operands.get(0)).getOperands().size() > 1)
				op1Str = "(" + op1Str + ")";
			String op2Str = operands.get(1).toString(ctx);
			if (operands.get(1) instanceof RewriterInstruction && ((RewriterInstruction)operands.get(1)).getOperands().size() > 1)
				op2Str = "(" + op2Str + ")";
			return op1Str + op + op2Str;
		};
	}

	public static BiFunction<RewriterStatement, RuleContext, String> wrappedBinaryStringRepr(String op) {
		return (stmt, ctx) -> {
			List<RewriterStatement> operands = ((RewriterInstruction)stmt).getOperands();
			return "(" + operands.get(0).toString(ctx) + ")" + op + "(" + operands.get(1).toString(ctx) + ")";
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

		stmt.forEachPreOrderWithDuplicates(RewriterUtils.propertyExtractor(new ArrayList<>(ops), ctx));

		stmt.forEachPreOrderWithDuplicates(el -> {
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

		RewriterStatement parsed = parseExpression(expr, new HashMap<>(), dataTypes, ctx);
		return ctx.metaPropagator != null ? ctx.metaPropagator.apply(parsed) : parsed;
	}

	/**
	 * Parses an expression
	 * @param expr the expression string. Note that all whitespaces have to already be removed
	 * @param refmap test
	 * @param dataTypes data type
	 * @param ctx context
	 * @return test
	 */
	public static RewriterStatement parseExpression(String expr, HashMap<Integer, RewriterStatement> refmap, HashMap<String, RewriterStatement> dataTypes, final RuleContext ctx) {
		RuleContext.currentContext = ctx;
		expr = expr.replaceAll("\\s+", "");
		MutableObject<String> mexpr = new MutableObject<>(expr);
		RewriterStatement stmt = doParseExpression(mexpr, refmap, dataTypes, ctx);
		stmt.prepareForHashing();
		stmt.consolidate(ctx);
		return stmt;
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
				if (expr.charAt(matcher.end()) != ':') {
					// Then we inject the common subexpression
					String remainder = expr.substring(matcher.end());
					mexpr.setValue(remainder);
					return refmap.get(n);
					//throw new IllegalArgumentException("Expected the token ':'");
				}
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
		Pattern pattern = Pattern.compile("[A-Za-z]([A-Za-z0-9]|_)*");
		Matcher matcher = pattern.matcher(expr);

		if (!matcher.find())
			return false;

		String dType = matcher.group();
		boolean intLiteral = dType.equals("LITERAL_INT");
		boolean boolLiteral = dType.equals("LITERAL_BOOL");
		boolean floatLiteral = dType.equals("LITERAL_FLOAT");

		if (intLiteral) {
			pattern = Pattern.compile("(-)?[0-9]+");
		} else if (boolLiteral) {
			pattern = Pattern.compile("(TRUE|FALSE)");
		} else if (floatLiteral) {
			pattern = Pattern.compile("(-)?[0-9]+(\\.[0-9]*)?");
		}

		if (expr.charAt(matcher.end()) != ':')
			return false;

		expr = expr.substring(matcher.end() + 1);

		matcher = pattern.matcher(expr);

		while (matcher.find()) {
			String varName = matcher.group();

			RewriterDataType dt;

			if (intLiteral) {
				dt = new RewriterDataType().as(varName).ofType("INT").asLiteral(Integer.parseInt(varName));
			} else if (boolLiteral) {
				dt = new RewriterDataType().as(varName).ofType("BOOL").asLiteral(Boolean.parseBoolean(varName));
			} else if (floatLiteral) {
				dt = new RewriterDataType().as(varName).ofType("FLOAT").asLiteral(Float.parseFloat(varName));
			} else {
				dt = new RewriterDataType().as(varName).ofType(dType);
			}

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

			if (remainder.isEmpty()) {
				mexpr.setValue(remainder);
				if (dataTypes.containsKey(token))
					return dataTypes.get(token);
				throw new IllegalArgumentException("DataType: '" + token + "' doesn't exist");
			}


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

	public static HashMap<String, List<RewriterStatement>> createIndex(RewriterStatement stmt, final RuleContext ctx) {
		HashMap<String, List<RewriterStatement>> index = new HashMap<>();
		stmt.forEachPreOrderWithDuplicates(mstmt -> {
			if (mstmt instanceof RewriterInstruction) {
				RewriterInstruction instr = (RewriterInstruction)mstmt;
				index.compute(instr.trueTypedInstruction(ctx), (k, v) -> {
					if (v == null) {
						return List.of(mstmt);
					} else {
						if (v.stream().noneMatch(el -> el == instr))
							v.add(instr);
						return v;
					}
				});
			}
			return true;
		});
		return index;
	}

	public static void buildBinaryAlgebraInstructions(StringBuilder sb, String instr, List<String> instructions) {
		for (String arg1 : instructions) {
			for (String arg2 : instructions) {
				sb.append(instr + "(" + arg1 + "," + arg2 + ")::");

				if (arg1.equals("MATRIX") || arg2.equals("MATRIX"))
					sb.append("MATRIX\n");
				else if (arg1.equals("FLOAT") || arg2.equals("FLOAT"))
					sb.append("FLOAT\n");
				else
					sb.append("INT\n");
			}
		}
	}

	public static void buildBinaryBoolInstructions(StringBuilder sb, String instr, List<String> instructions) {
		for (String arg1 : instructions) {
			for (String arg2 : instructions) {
				sb.append(instr + "(" + arg1 + "," + arg2 + ")::BOOL\n");
			}
		}
	}

	public static void buildBinaryPermutations(List<String> args, BiConsumer<String, String> func) {
		buildBinaryPermutations(args, args, func);
	}

	public static void buildBinaryPermutations(List<String> args1, List<String> args2, BiConsumer<String, String> func) {
		for (String arg1 : args1)
			for (String arg2 : args2)
				func.accept(arg1, arg2);
	}

	public static String defaultTypeHierarchy(String t1, String t2) {
		if (t1.equals("INT") && t2.equals("INT"))
			return "INT";
		if (!t1.equals("MATRIX") && !t2.equals("MATRIX"))
			return "FLOAT";
		return "MATRIX";
	}

	public static void putAsBinaryPrintable(String instr, List<String> types, HashMap<String, BiFunction<RewriterStatement, RuleContext, String>> printFunctions, BiFunction<RewriterStatement, RuleContext, String> function) {
		for (String type1 : types)
			for (String type2 : types)
				printFunctions.put(instr + "(" + type1 + "," + type2 + ")", function);
	}

	public static void putAsDefaultBinaryPrintable(List<String> instrs, List<String> types, HashMap<String, BiFunction<RewriterStatement, RuleContext, String>> funcs) {
		for (String instr : instrs)
			putAsBinaryPrintable(instr, types, funcs, binaryStringRepr(" " + instr + " "));
	}

	public static HashMap<String, Set<String>> mapToImplementedFunctions(final RuleContext ctx) {
		HashMap<String, Set<String>> out = new HashMap<>();
		Set<String> superTypes = new HashSet<>();

		for (Map.Entry<String, String> entry : ctx.instrTypes.entrySet()) {
			Set<String> props = ctx.instrProperties.get(entry.getKey());
			if (props != null && !props.isEmpty()) {
				for (String prop : props) {
					Set<String> impl = out.computeIfAbsent(prop, k -> new HashSet<>());
					impl.add(typedToUntypedInstruction(entry.getKey()));
					superTypes.add(typedToUntypedInstruction(prop));
				}
			}
		}

		for (Map.Entry<String, Set<String>> entry : out.entrySet())
			entry.getValue().removeAll(superTypes);

		return out;
	}

	public static RewriterStatement replaceReferenceAware(RewriterStatement root, Function<RewriterStatement, RewriterStatement> comparer) {
		return replaceReferenceAware(root, false, comparer, new HashMap<>());
	}

	public static RewriterStatement replaceReferenceAware(RewriterStatement root, boolean duplicateReferences, Function<RewriterStatement, RewriterStatement> comparer, HashMap<RewriterRule.IdentityRewriterStatement, RewriterStatement> visited) {
		RewriterRule.IdentityRewriterStatement is = new RewriterRule.IdentityRewriterStatement(root);
		if (visited.containsKey(is)) {
			return visited.get(is);
		}

		RewriterStatement oldRef = root;
		RewriterStatement newOne = comparer.apply(root);
		root = newOne != null ? newOne : root;

		if (newOne == null)
			duplicateReferences |= root.refCtr > 1;

		if (root.getOperands() != null) {
			for (int i = 0; i < root.getOperands().size(); i++) {
				RewriterStatement newSub = replaceReferenceAware(root.getOperands().get(i), duplicateReferences, comparer, visited);

				if (newSub != null) {
					if (duplicateReferences && newOne == null) {
						root = root.copyNode();
					}

					root.getOperands().set(i, newSub);
				}
			}
		}

		return newOne;
	}
}
