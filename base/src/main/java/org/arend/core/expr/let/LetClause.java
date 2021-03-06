package org.arend.core.expr.let;

import org.arend.core.context.binding.EvaluatingBinding;
import org.arend.core.context.binding.NamedBinding;
import org.arend.core.expr.Expression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.core.subst.SubstVisitor;
import org.jetbrains.annotations.NotNull;

public class LetClause extends NamedBinding implements EvaluatingBinding {
  private LetClausePattern myPattern;
  private Expression myExpression;

  public LetClause(String name, LetClausePattern pattern, Expression expression) {
    super(name);
    myPattern = pattern;
    myExpression = expression;
  }

  public LetClausePattern getPattern() {
    return myPattern;
  }

  public void setPattern(LetClausePattern pattern) {
    myPattern = pattern;
  }

  @NotNull
  @Override
  public Expression getExpression() {
    return myExpression;
  }

  @Override
  public Expression subst(SubstVisitor visitor) {
    return myExpression.accept(visitor, null);
  }

  @Override
  public void subst(InPlaceLevelSubstVisitor visitor) {
    myExpression.accept(visitor, null);
  }

  public void setExpression(Expression expression) {
    myExpression = expression;
  }

  @Override
  public Expression getTypeExpr() {
    return myExpression.getType();
  }

  @Override
  public void strip(StripVisitor stripVisitor) {
    myExpression = myExpression.accept(stripVisitor, null);
  }
}
