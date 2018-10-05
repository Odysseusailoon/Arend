package org.arend.core.context.param;

import org.arend.core.expr.ReferenceExpression;
import org.arend.core.expr.type.Type;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.LevelSubstitution;

import java.util.List;

public class TypedSingleDependentLink extends TypedDependentLink implements SingleDependentLink {
  public TypedSingleDependentLink(boolean isExplicit, String name, Type type) {
    super(isExplicit, name, type, EmptyDependentLink.getInstance());
  }

  @Override
  public TypedSingleDependentLink getNextTyped(List<String> names) {
    if (names != null) {
      names.add(getName());
    }
    return this;
  }

  @Override
  public SingleDependentLink subst(ExprSubstitution exprSubst, LevelSubstitution levelSubst, int size, boolean updateSubst) {
    if (size > 0) {
      TypedSingleDependentLink result = new TypedSingleDependentLink(isExplicit(), getName(), getType().subst(exprSubst, levelSubst));
      if (updateSubst) {
        exprSubst.addSubst(this, new ReferenceExpression(result));
      } else {
        exprSubst.add(this, new ReferenceExpression(result));
      }
      return result;
    } else {
      return EmptyDependentLink.getInstance();
    }
  }

  @Override
  public EmptyDependentLink getNext() {
    return EmptyDependentLink.getInstance();
  }

  @Override
  public void setNext(DependentLink next) {
    if (next.hasNext()) {
      throw new IllegalStateException();
    }
  }
}