package com.jetbrains.jetpad.vclang.term.pattern;


import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.definition.Binding;
import com.jetbrains.jetpad.vclang.term.definition.Constructor;
import com.jetbrains.jetpad.vclang.term.expr.ArgumentExpression;
import com.jetbrains.jetpad.vclang.term.expr.DefCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.arg.TypeArgument;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.arg.Utils.numberOfVariables;

public class Utils {
  public static int getNumArguments(Abstract.Pattern pattern) {
    if (pattern instanceof Abstract.NamePattern || pattern instanceof Abstract.AnyConstructorPattern) {
      return 1;
    } else if (pattern instanceof Abstract.ConstructorPattern) {
      int result = 0;
      for (Abstract.Pattern nestedPattern : ((Abstract.ConstructorPattern) pattern).getPatterns()) {
        result += getNumArguments(nestedPattern);
      }
      return result;
    } else {
      throw new IllegalStateException();
    }
  }

  public static void collectPatternNames(Abstract.Pattern pattern, List<String> names) {
    if (pattern instanceof Abstract.NamePattern) {
      names.add(((Abstract.NamePattern) pattern).getName());
    } else if (pattern instanceof Abstract.AnyConstructorPattern) {
      names.add(null);
    } else if (pattern instanceof Abstract.ConstructorPattern) {
      for (Abstract.Pattern nestedPattern : ((Abstract.ConstructorPattern) pattern).getPatterns()) {
        collectPatternNames(nestedPattern, names);
      }
    }
  }

  public static void prettyPrintPattern(Abstract.Pattern pattern, StringBuilder builder, List<String> names) {
    prettyPrintPattern(pattern, builder, names, false);
  }

  public static void prettyPrintPattern(Abstract.Pattern pattern, StringBuilder builder, List<String> names, boolean topLevel) {
    if (!pattern.getExplicit())
      builder.append('{');
    if (pattern instanceof Abstract.NamePattern) {
      if (((Abstract.NamePattern) pattern).getName() == null) {
        builder.append('_');
      } else {
        builder.append(((Abstract.NamePattern) pattern).getName());
      }
      names.add(((Abstract.NamePattern) pattern).getName());
    } else if (pattern instanceof Abstract.AnyConstructorPattern) {
      builder.append("_!");
    } else if (pattern instanceof Abstract.ConstructorPattern) {
      if (!topLevel && pattern.getExplicit())
        builder.append('(');
      builder.append(((Abstract.ConstructorPattern) pattern).getConstructorName());
      for (Abstract.Pattern p : ((Abstract.ConstructorPattern) pattern).getPatterns()) {
        builder.append(' ');
        prettyPrintPattern(p, builder, names, false);
      }
      if (!topLevel && pattern.getExplicit())
        builder.append(')');
    }
    if (!pattern.getExplicit())
      builder.append('}');
  }

  public static class ProcessImplicitResult {
    public final List<Abstract.Pattern> patterns;
    public final int wrongImplicitPosition;
    public final int numExplicit;
    public final int numExcessive;

    public ProcessImplicitResult(List<Abstract.Pattern> patterns, int numExplicit) {
      this.patterns = patterns;
      this.wrongImplicitPosition = -1;
      this.numExplicit = numExplicit;
      this.numExcessive = 0;
    }

    public ProcessImplicitResult(int wrongImplicitPosition, int numExplicit) {
      this.patterns = null;
      this.wrongImplicitPosition = wrongImplicitPosition;
      this.numExplicit = numExplicit;
      this.numExcessive = 0;
    }

    public ProcessImplicitResult(int numExcessive) {
      this.patterns = null;
      this.numExcessive = numExcessive;
      this.numExplicit = -1;
      this.wrongImplicitPosition = -1;
    }
  }

  public static ProcessImplicitResult processImplicit(List<? extends Abstract.Pattern> patterns, List<? extends Abstract.TypeArgument> arguments) {
    ArrayList<Boolean> argIsExplicit = new ArrayList<>();
    for (Abstract.TypeArgument arg : arguments) {
      if (arg instanceof Abstract.TelescopeArgument) {
        argIsExplicit.addAll(Collections.nCopies(((Abstract.TelescopeArgument) arg).getNames().size(), arg.getExplicit()));
      } else {
        argIsExplicit.add(arg.getExplicit());
      }
    }

    int numExplicit = 0;
    for (boolean b : argIsExplicit) {
      if (b)
        numExplicit++;
    }

    List<Abstract.Pattern> result = new ArrayList<>();
    int indexI = 0;
    for (boolean isExplicit : argIsExplicit) {
      Abstract.Pattern curPattern = indexI < patterns.size() ? patterns.get(indexI) : new NamePattern(null, false);
      if (curPattern.getExplicit() && !isExplicit) {
        curPattern = new NamePattern(null, false);
      } else {
        indexI++;
      }
      if (curPattern.getExplicit() != isExplicit) {
        return new ProcessImplicitResult(indexI, numExplicit);
      }
      result.add(curPattern);
    }
    if (indexI < patterns.size()) {
      return new ProcessImplicitResult(patterns.size() - indexI);
    }
    return new ProcessImplicitResult(result, numExplicit);
  }

  public static class PatternMatchResult {}

  public static class PatternMatchOKResult extends PatternMatchResult {
    public final List<Expression> expressions;

    PatternMatchOKResult(List<Expression> expressions) {
      this.expressions = expressions;
    }
  }

  public static class PatternMatchFailedResult extends PatternMatchResult {
    public final ConstructorPattern failedPattern;
    public final Expression actualExpression;

    PatternMatchFailedResult(ConstructorPattern failedPattern, Expression actualExpression) {
      this.failedPattern = failedPattern;
      this.actualExpression = actualExpression;
    }
  }

  public static class PatternMatchMaybeResult extends PatternMatchResult {
    public final ConstructorPattern maybePattern;
    public final Expression actualExpression;

    PatternMatchMaybeResult(ConstructorPattern maybePattern, Expression actualExpression) {
      this.maybePattern = maybePattern;
      this.actualExpression = actualExpression;
    }
  }

  public static PatternMatchResult patternMatchAll(List<Pattern> patterns, List<Expression> exprs, List<Binding> context) {
    List<Expression> result = new ArrayList<>();
    assert patterns.size() == exprs.size();

    PatternMatchMaybeResult maybe = null;
    for (int i = 0; i < patterns.size(); i++) {
      PatternMatchResult subMatch = patterns.get(i).match(exprs.get(i), context);
      if (subMatch instanceof PatternMatchFailedResult) {
        return subMatch;
      } else if (subMatch instanceof PatternMatchMaybeResult) {
        if (maybe == null)
          maybe = (PatternMatchMaybeResult) subMatch;
      } else if (subMatch instanceof PatternMatchOKResult) {
        result.addAll(((PatternMatchOKResult) subMatch).expressions);
      }
    }

    return maybe != null ? maybe : new PatternMatchOKResult(result);
  }

  public static List<Integer> patternMultipleMatch(List<Pattern> patterns, Expression expr, List<Binding> ctx) {
    List<Integer> result = new ArrayList<>();
    boolean hasConstructorPattern = false;
    for (int i = 0; i < patterns.size(); i++) {
      if (patterns.get(i) instanceof NamePattern) {
        result.add(i);
      } else if (patterns.get(i) instanceof ConstructorPattern) {
        hasConstructorPattern = true;
      }
    }
    if (!hasConstructorPattern)
      return result;

    List<Expression> constructorArgs = new ArrayList<>();
    expr = expr.normalize(NormalizeVisitor.Mode.WHNF, ctx).getFunction(constructorArgs);
    Collections.reverse(constructorArgs);
    if (!(expr instanceof DefCallExpression && ((DefCallExpression) expr).getDefinition() instanceof Constructor)) {
      return new ArrayList<>();
    }

    List<Integer> goodConstructorPatternsIdxs = new ArrayList<>();
    List<List<Pattern>> goodConstructorNestedPatterns = new ArrayList<>();
    for (int i = 0; i < constructorArgs.size(); i++)
      goodConstructorNestedPatterns.add(new ArrayList<Pattern>());

    for (int i = 0; i < patterns.size(); i++) {
      if (patterns.get(i) instanceof ConstructorPattern) {
        if (((ConstructorPattern) patterns.get(i)).getConstructor() == ((DefCallExpression) expr).getDefinition()) {
          goodConstructorPatternsIdxs.add(i);
          for (int j = 0; j < constructorArgs.size(); j++) {
            goodConstructorNestedPatterns.get(j).add(((ConstructorPattern) patterns.get(i)).getPatterns().get(j));
          }
        }
      } else if (patterns.get(i) instanceof AnyConstructorPattern) {
        result.add(i);
      }
    }

    result.addAll(patternMultipleMatch(goodConstructorNestedPatterns, constructorArgs, ctx, goodConstructorPatternsIdxs));
    Collections.sort(result);
    return result;
  }

  public static List<Integer> patternMultipleMatch(List<List<Pattern>> patterns, List<Expression> expressions, List<Binding> ctx, int numPatterns) {
    List<Integer> origIndicies = new ArrayList<>();
    for (int i = 0; i < numPatterns; i++) {
      origIndicies.add(i);
    }
    return patternMultipleMatch(patterns, expressions, ctx, origIndicies);
  }

  public static List<Integer> patternMultipleMatch(List<List<Pattern>> patterns, List<Expression> expressions, List<Binding> ctx, List<Integer> origIndicies) {
    for (int i = 0; i < expressions.size(); i++) {
      List<Integer> goodPatterns = patternMultipleMatch(patterns.get(i), expressions.get(i), ctx);
      for (int j = origIndicies.size() - 1; j >= 0; j--) {
        if (!goodPatterns.contains(j)) {
          origIndicies.remove(j);
          for (List<Pattern> nestedPatterns : patterns) {
            nestedPatterns.remove(j);
          }
        }
      }
    }

    return origIndicies;
  }

  public static List<TypeArgument> expandConstructorParameters(Constructor constructor) {
    List<PatternExpansion.Result> results = PatternExpansion.expandPatterns(constructor.getPatterns(), constructor.getDataType().getParameters());

    List<TypeArgument> result = new ArrayList<>();
    for (PatternExpansion.Result nestedResult : results) {
      result.addAll(nestedResult.args);
    }
    return result;
  }

  public static List<ArgumentExpression> constructorPatternsToExpressions(Constructor constructor) {
    List<PatternExpansion.Result> results = PatternExpansion.expandPatterns(constructor.getPatterns(), constructor.getDataType().getParameters());

    List<ArgumentExpression> result = new ArrayList<>();
    int shift = numberOfVariables(constructor.getArguments());
    int numArguments = 0;
    for (int i = results.size() - 1; i >= 0; i--) {
      result.add(new ArgumentExpression(results.get(i).expression.getExpression().liftIndex(0, numArguments + shift),
          results.get(i).expression.isExplicit(), results.get(i).expression.isHidden()));
      numArguments += results.get(i).args.size();
    }
    Collections.reverse(result);

    return result;
  }

  public static ArgumentExpression patternToExpression(Pattern pattern) {
    return PatternExpansion.expandPattern(pattern);
  }

  public static Expression expandPatternSubstitute(Pattern pattern, int varIndex, Expression what, Expression where) {
    return expandPatternSubstitute(getNumArguments(pattern), varIndex, what, where);
  }

  public static Expression expandPatternSubstitute(int numBindings, int varIndex, Expression what, Expression where) {
    Expression expression = what.liftIndex(0, varIndex);
    if (numBindings > 0) {
      where = where.liftIndex(varIndex + 1, numBindings).subst(expression, varIndex);
    } else {
      where = where.subst(expression, varIndex);
    }
    return where;
  }
}
