package com.jetbrains.jetpad.vclang.term.expr.visitor;

import com.jetbrains.jetpad.vclang.term.definition.ClassField;
import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.*;
import com.jetbrains.jetpad.vclang.term.expr.arg.Argument;
import com.jetbrains.jetpad.vclang.term.expr.arg.NameArgument;
import com.jetbrains.jetpad.vclang.term.expr.arg.TelescopeArgument;
import com.jetbrains.jetpad.vclang.term.expr.arg.TypeArgument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.Index;
import static com.jetbrains.jetpad.vclang.term.expr.arg.Utils.numberOfVariables;
import static com.jetbrains.jetpad.vclang.term.pattern.Utils.expandPatternSubstitute;
import static com.jetbrains.jetpad.vclang.term.pattern.Utils.patternToExpression;

public class TerminationCheckVisitor extends BaseExpressionVisitor<Void, Boolean> {
  private final Definition myDef;
  private final List<Expression> myPatterns;

  public TerminationCheckVisitor(FunctionDefinition def) {
    myDef = def;

    int vars = numberOfVariables(def.getArguments());
    myPatterns = new ArrayList<>(vars);
    for (int i = 0; i < vars; ++i) {
      myPatterns.add(Index(i));
    }
  }

  public TerminationCheckVisitor(Definition def, List<Expression> patterns) {
    myDef = def;
    myPatterns = patterns;
  }

  private enum Ord { LESS, EQUALS, NOT_LESS }

  private Ord isLess(Expression expr1, Expression expr2) {
    List<Expression> args1 = new ArrayList<>();
    expr1 = expr1.getFunction(args1);
    List<Expression> args2 = new ArrayList<>();
    expr2 = expr2.getFunction(args2);
    if (expr1.equals(expr2)) {
      Ord ord = isLess(args1, args2);
      if (ord != Ord.NOT_LESS) return ord;
    }
    for (Expression arg : args2) {
      if (isLess(expr1, arg) != Ord.NOT_LESS) return Ord.LESS;
    }
    return Ord.NOT_LESS;
  }

  private Ord isLess(List<Expression> exprs1, List<Expression> exprs2) {
    for (int i = 0; i < Math.min(exprs1.size(), exprs2.size()); ++i) {
      Ord ord = isLess(exprs1.get(exprs1.size() - 1 - i), exprs2.get(exprs2.size() - 1 - i));
      if (ord != Ord.EQUALS) return ord;
    }
    return exprs1.size() >= exprs2.size() ? Ord.EQUALS : Ord.NOT_LESS;
  }

  @Override
  public Boolean visitApp(AppExpression expr, Void params) {
    List<Expression> args = new ArrayList<>();
    Expression fun = expr.getFunction(args);
    if (fun instanceof ConCallExpression) {
      args.addAll(((ConCallExpression) fun).getParameters());
      Collections.reverse(args.subList(args.size() - ((ConCallExpression) fun).getParameters().size(), args.size()));
    }
    if (fun instanceof DefCallExpression) {
      if (((DefCallExpression) fun).getDefinition().getThisClass() != null && !args.isEmpty()) {
        args.remove(args.size() - 1);
      }
      if (((DefCallExpression) fun).getDefinition() == myDef && isLess(args, myPatterns) != Ord.LESS) {
        return false;
      }
      if (fun instanceof ConCallExpression && ((ConCallExpression) fun).getDefinition() != myDef && !visitConCall((ConCallExpression) fun, null)) {
        return false;
      }
      if (fun instanceof ClassCallExpression && !visitClassCall((ClassCallExpression) fun, null)) {
        return false;
      }
    } else {
      if (!fun.accept(this, null)) {
        return false;
      }
    }

    for (Expression arg : args) {
      if (!arg.accept(this, null)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitDefCall(DefCallExpression expr, Void params) {
    return expr.getDefinition() != myDef;
  }

  @Override
  public Boolean visitConCall(ConCallExpression expr, Void params) {
    for (Expression parameter : expr.getParameters()) {
      if (!parameter.accept(this, null)) {
        return false;
      }
    }
    return expr.getDefinition() != myDef;
  }

  @Override
  public Boolean visitClassCall(ClassCallExpression expr, Void params) {
    for (Map.Entry<ClassField, ClassCallExpression.ImplementStatement> elem : expr.getImplementStatements().entrySet()) {
      if (elem.getValue().type != null && !elem.getValue().type.accept(this, null) || elem.getValue().term != null && !elem.getValue().term.accept(this, null)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitIndex(IndexExpression expr, Void params) {
    return true;
  }

  private class PatternLifter implements AutoCloseable {
    private int total = 0;

    void liftPatterns(int on) {
      total += on;
      if (on == 0) return;
      for (int i = 0; i < myPatterns.size(); ++i) {
        myPatterns.set(i, myPatterns.get(i).liftIndex(0, on));
      }
    }

    void liftPatterns() {
      liftPatterns(1);
    }

    @Override
    public void close() {
      liftPatterns(-total);
    }
  }

  @Override
  public Boolean visitLam(LamExpression expr, Void params) {
    return visitLamArguments(expr.getArguments(), expr.getBody(), null);
  }

  private boolean visitLamArguments(List<Argument> arguments, Expression body1, Expression body2) {
    try (PatternLifter lifter = new PatternLifter()) {
      for (Argument argument : arguments) {
        if (argument instanceof NameArgument) {
          lifter.liftPatterns();
        } else if (argument instanceof TelescopeArgument) {
          if (!((TypeArgument) argument).getType().accept(this, null)) {
            return false;
          }
          lifter.liftPatterns(((TelescopeArgument) argument).getNames().size());
        } else {
          throw new IllegalStateException();
        }
      }
      return (body1 == null ? true : body1.accept(this, null)) && (body2 == null ? true : body2.accept(this, null));
    }
  }
  
  private Boolean visitArguments(List<? extends Argument> arguments, Expression codomain, PatternLifter lifter) {
    for (Argument argument : arguments) {
      if (argument instanceof NameArgument) {
        lifter.liftPatterns();
      } else
      if (argument instanceof TypeArgument) {
        if (!((TypeArgument) argument).getType().accept(this, null)) {
          return false;
        }
        if (argument instanceof TelescopeArgument) {
          lifter.liftPatterns(((TelescopeArgument) argument).getNames().size());
        } else {
          lifter.liftPatterns();
        }
      }
    }

    return codomain != null ? codomain.accept(this, null) : true;
  }

  private Boolean visitArguments(List<? extends Argument> arguments, PatternLifter lifter) {
    return visitArguments(arguments, null, lifter);
  }

  @Override
  public Boolean visitPi(PiExpression expr, Void params) {
    try (PatternLifter lifter = new PatternLifter()) {
      return visitArguments(expr.getArguments(), expr.getCodomain(), lifter);
    }
  }

  @Override
  public Boolean visitUniverse(UniverseExpression expr, Void params) {
    return true;
  }

  @Override
  public Boolean visitInferHole(InferHoleExpression expr, Void params) {
    return true;
  }

  @Override
  public Boolean visitError(ErrorExpression expr, Void params) {
    return true;
  }

  @Override
  public Boolean visitTuple(TupleExpression expr, Void params) {
    for (Expression field : expr.getFields()) {
      if (!field.accept(this, null)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitSigma(SigmaExpression expr, Void params) {
    try (PatternLifter lifter = new PatternLifter()) {
      return visitArguments(expr.getArguments(), lifter);
    }
  }

  @Override
  public Boolean visitElim(ElimExpression expr, Void params) {
    for (Clause clause : expr.getClauses()) {
      List<Expression> patterns = new ArrayList<>(myPatterns);
      for (int i = 0; i < clause.getPatterns().size(); i++) {
        Expression newExpr = patternToExpression(clause.getPatterns().get(i)).getExpression();
        for (int j = 0; j < patterns.size(); j++) {
          patterns.set(j, expandPatternSubstitute(clause.getPatterns().get(i), expr.getExpressions().get(i).getIndex(), newExpr, patterns.get(j)));
        }
      }

      if (!clause.getExpression().accept(new TerminationCheckVisitor(myDef, patterns), null)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public Boolean visitProj(ProjExpression expr, Void params) {
    return expr.getExpression().accept(this, null);
  }

  @Override
  public Boolean visitNew(NewExpression expr, Void params) {
    return expr.getExpression().accept(this, null);
  }

  @Override
  public Boolean visitLet(LetExpression letExpression, Void params) {
    try (PatternLifter lifter = new PatternLifter()) {
      for (LetClause clause : letExpression.getClauses()) {
        if (!visitLetClause(clause)) {
          return false;
        }
        lifter.liftPatterns();
      }

      return letExpression.getExpression().accept(this, null);
    }
  }

  private boolean visitLetClause(LetClause clause) {
    try (PatternLifter lifter1 = new PatternLifter()) {
      if (!visitArguments(clause.getArguments(), lifter1)) {
        return false;
      }
      return clause.getTerm().accept(this, null);
    }
  }
}
