package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.context.binding.Binding;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LevelSubstitution {
  private Map<Binding, LevelExpression> mySubstExprs = new HashMap<>();

  public LevelSubstitution() {}

  public LevelSubstitution(Binding lp, LevelExpression lp_expr, Binding lh, LevelExpression lh_expr) {
    mySubstExprs.put(lp, lp_expr);
    mySubstExprs.put(lh, lh_expr);
  }

  public LevelSubstitution(Binding lp, Binding lp_new, Binding lh, Binding lh_new) {
    mySubstExprs.put(lp, new LevelExpression(lp_new));
    mySubstExprs.put(lh, new LevelExpression(lh_new));
  }

  public Set<Binding> getDomain() {
    return mySubstExprs.keySet();
  }

  public LevelExpression get(Binding binding)  {
    return mySubstExprs.get(binding);
  }

  public void add(Binding binding, LevelExpression expr) {
    mySubstExprs.put(binding, expr);
  }

  public void add(LevelSubstitution subst) {
    mySubstExprs.putAll(subst.mySubstExprs);
  }

  public void subst(Binding binding, LevelExpression expr) {
    for (Map.Entry<Binding, LevelExpression> var : mySubstExprs.entrySet()) {
      var.setValue(var.getValue().subst(binding, expr));
    }
  }

  public LevelSubstitution compose(LevelSubstitution subst) {
    LevelSubstitution result = new LevelSubstitution();
    result.add(this);
    for (Binding binding : subst.getDomain()) {
      boolean foundInExprs = false;
      for (Map.Entry<Binding, LevelExpression> substExpr : mySubstExprs.entrySet()) {
        if (substExpr.getValue().findBinding(binding)) {
          result.add(substExpr.getKey(), substExpr.getValue().subst(subst));
          foundInExprs = true;
        }
      }
      if (!foundInExprs) {
        result.add(binding, subst.get(binding));
      }
    }
    return result;
  }
}
