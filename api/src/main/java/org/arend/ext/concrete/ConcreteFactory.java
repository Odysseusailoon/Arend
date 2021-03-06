package org.arend.ext.concrete;

import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteCaseArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.GoalSolver;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.MetaDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collection;

/**
 * ConcreteFactory can be used to create concrete expressions, which can be checked by {@link org.arend.ext.typechecking.ExpressionTypechecker}
 */
public interface ConcreteFactory {
  @NotNull ConcreteExpression ref(@NotNull ArendRef ref);
  @NotNull ConcreteExpression ref(@NotNull ArendRef ref, @Nullable ConcreteLevel pLevel, @Nullable ConcreteLevel hLevel);
  @NotNull ConcreteExpression ref(@NotNull CoreBinding ref);
  @NotNull ConcreteExpression core(String name, @NotNull TypedExpression expr);
  @NotNull ConcreteExpression meta(String name, @NotNull MetaDefinition meta);
  @NotNull ConcreteExpression thisExpr();
  @NotNull ConcreteExpression lam(@NotNull Collection<? extends ConcreteParameter> parameters, @NotNull ConcreteExpression body);
  @NotNull ConcreteExpression pi(@NotNull Collection<? extends ConcreteParameter> parameters, @NotNull ConcreteExpression codomain);
  @NotNull ConcreteExpression universe(@Nullable ConcreteLevel pLevel, @Nullable ConcreteLevel hLevel);
  @NotNull ConcreteExpression hole();
  @NotNull ConcreteExpression goal(@Nullable String name, @Nullable ConcreteExpression expression);
  @NotNull ConcreteExpression goal(@Nullable String name, @Nullable ConcreteExpression expression, @NotNull GoalSolver goalSolver);
  @NotNull ConcreteExpression goal();
  @NotNull ConcreteExpression tuple(@NotNull ConcreteExpression... expressions);
  @NotNull ConcreteExpression tuple(@NotNull Collection<? extends ConcreteExpression> expressions);
  @NotNull ConcreteExpression sigma(@NotNull ConcreteParameter... parameters);
  @NotNull ConcreteExpression sigma(@NotNull Collection<? extends ConcreteParameter> parameters);
  @NotNull ConcreteExpression caseExpr(boolean isSCase, Collection<? extends ConcreteCaseArgument> arguments, @Nullable ConcreteExpression resultType, @Nullable ConcreteExpression resultTypeLevel, @NotNull ConcreteClause... clauses);
  @NotNull ConcreteExpression caseExpr(boolean isSCase, Collection<? extends ConcreteCaseArgument> arguments, @Nullable ConcreteExpression resultType, @Nullable ConcreteExpression resultTypeLevel, @NotNull Collection<? extends ConcreteClause> clauses);
  @NotNull ConcreteExpression eval(@NotNull ConcreteExpression expression);
  @NotNull ConcreteExpression peval(@NotNull ConcreteExpression expression);
  @NotNull ConcreteExpression proj(@NotNull ConcreteExpression expression, int field);
  @NotNull ConcreteExpression classExt(@NotNull ConcreteExpression expression, @NotNull ConcreteClassElement... elements);
  @NotNull ConcreteExpression classExt(@NotNull ConcreteExpression expression, @NotNull Collection<? extends ConcreteClassElement> elements);
  @NotNull ConcreteExpression newExpr(@NotNull ConcreteExpression expression);
  @NotNull ConcreteExpression letExpr(boolean isStrict, @NotNull Collection<? extends ConcreteLetClause> clauses, @NotNull ConcreteExpression expression);
  @NotNull ConcreteExpression number(@NotNull BigInteger number);
  @NotNull ConcreteExpression number(int number);
  @NotNull ConcreteExpression typed(@NotNull ConcreteExpression expression, @NotNull ConcreteExpression type);
  @NotNull ConcreteExpression app(@NotNull ConcreteExpression function, @NotNull Collection<? extends ConcreteArgument> arguments);
  @NotNull ConcreteExpression app(@NotNull ConcreteExpression function, boolean isExplicit, @NotNull Collection<? extends ConcreteExpression> arguments);
  @NotNull ConcreteArgument arg(@NotNull ConcreteExpression expression, boolean isExplicit);
  @NotNull ConcreteAppBuilder appBuilder(@NotNull ConcreteExpression function);

  @NotNull ArendRef local(@NotNull String name);
  @NotNull ConcreteParameter param(@Nullable ArendRef ref);
  @NotNull ConcreteParameter param(@NotNull Collection<? extends ArendRef> refs, @NotNull ConcreteExpression type);

  @NotNull ConcreteLetClause letClause(@NotNull ArendRef ref, @NotNull Collection<? extends ConcreteParameter> parameters, @Nullable ConcreteExpression type, @NotNull ConcreteExpression term);
  @NotNull ConcreteLetClause letClause(@NotNull ConcreteSinglePattern pattern, @Nullable ConcreteExpression type, @NotNull ConcreteExpression term);
  @NotNull ConcreteSinglePattern singlePatternRef(@Nullable ArendRef ref, @Nullable ConcreteExpression type);
  @NotNull ConcreteSinglePattern singlePatternConstructor(@NotNull ConcreteSinglePattern... subpatterns);
  @NotNull ConcreteSinglePattern singlePatternConstructor(@NotNull Collection<? extends ConcreteSinglePattern> subpatterns);

  @NotNull ConcreteClassElement implementation(@NotNull ArendRef field, @Nullable ConcreteExpression expression, @NotNull ConcreteClassElement... subclauses);
  @NotNull ConcreteClassElement implementation(@NotNull ArendRef field, @Nullable ConcreteExpression expression, @NotNull Collection<? extends ConcreteClassElement> subclauses);

  @NotNull ConcreteCaseArgument caseArg(@NotNull ConcreteExpression expression, @Nullable ArendRef asRef, @Nullable ConcreteExpression type);
  @NotNull ConcreteClause clause(@NotNull Collection<? extends ConcretePattern> patterns, @Nullable ConcreteExpression expression);
  @NotNull ConcretePattern refPattern(@Nullable ArendRef ref, @Nullable ConcreteExpression type);
  @NotNull ConcretePattern tuplePattern(@NotNull ConcretePattern... subpatterns);
  @NotNull ConcretePattern tuplePattern(@NotNull Collection<? extends ConcretePattern> subpatterns);
  @NotNull ConcretePattern numberPattern(int number);
  @NotNull ConcretePattern conPattern(@NotNull ArendRef constructor, @NotNull ConcretePattern... subpatterns);
  @NotNull ConcretePattern conPattern(@NotNull ArendRef constructor, @NotNull Collection<? extends ConcretePattern> subpatterns);

  @NotNull ConcreteLevel inf();
  @NotNull ConcreteLevel lp();
  @NotNull ConcreteLevel lh();
  @NotNull ConcreteLevel numLevel(int level);
  @NotNull ConcreteLevel sucLevel(@NotNull ConcreteLevel level);
  @NotNull ConcreteLevel maxLevel(@NotNull ConcreteLevel level1, @NotNull ConcreteLevel level2);

  @NotNull ConcreteFactory copy();
  @NotNull ConcreteFactory withData(@Nullable Object data);
}
